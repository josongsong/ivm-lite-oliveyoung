# RFC-IMPL-012 — Sink Configuration (Minimal Coupling)

**Status**: Draft  
**Created**: 2026-01-27  
**Scope**: SinkRule Contract 설계 및 외부 시스템 연동 원칙  
**Depends on**: RFC-007 (Sink Orchestration)  

---

## 0. Executive Summary

IVM은 **데이터 전달자**일 뿐, **Sink 시스템 설정의 주인이 아니다**.

- OpenSearch 인덱스 설정, 매핑, analyzer → OpenSearch 운영팀 책임
- AWS Personalize 데이터셋 스키마 → ML팀 책임
- IVM이 알아야 하는 것: **어디로(endpoint), 뭘(index/topic), 어떤 ID로(doc_id)**

**Anti-pattern**: IVM이 OpenSearch 템플릿, 매핑, settings를 관리하는 것  
**SOTA**: IVM은 최소한의 라우팅 정보만 가지고, 인프라 설정은 해당 시스템 운영팀이 관리

---

## 1. 핵심 원칙 (Non-Negotiable)

### 1-1. 관심사 분리 (Separation of Concerns)

| 책임 | IVM (SinkRule) | Sink 시스템 운영 |
|------|----------------|------------------|
| 어떤 데이터를 보낼지 | ✅ | - |
| 어디로 보낼지 (endpoint, index) | ✅ | - |
| doc_id 패턴 | ✅ | - |
| 필드 매핑 (from → to) | ✅ | - |
| 인덱스 settings (shards, replicas) | ❌ | ✅ |
| 인덱스 mapping (field types, analyzer) | ❌ | ✅ |
| 인덱스 템플릿 | ❌ | ✅ |
| 인덱스 수명 주기 (ISM) | ❌ | ✅ |

### 1-2. 최소 결합 (Minimal Coupling)

IVM이 Sink에 전달하는 정보:
1. **Endpoint**: 어디로 보낼지
2. **Target**: 어떤 인덱스/토픽/버킷
3. **Doc ID**: 문서 식별자 (멱등성)
4. **Payload**: JSON 데이터

IVM이 **몰라야 하는** 정보:
- 인덱스가 몇 개의 shard로 구성되는지
- 어떤 analyzer를 사용하는지
- 인덱스 매핑이 어떻게 되어 있는지
- 인덱스 수명 주기 정책

### 1-3. 왜 이렇게 해야 하는가?

1. **독립적 진화**: OpenSearch 운영팀이 인덱스 최적화를 IVM 배포 없이 수행 가능
2. **전문성 분리**: 검색 최적화는 검색팀, 데이터 파이프라인은 IVM팀
3. **장애 격리**: 인덱스 설정 문제가 IVM 장애로 전파되지 않음
4. **운영 단순화**: IVM Contract 변경 없이 인덱스 튜닝 가능

---

## 2. SinkRule 설계 (Minimal)

### 2-1. OpenSearch SinkRule

```yaml
kind: SINKRULE
id: sinkrule.opensearch.product
version: 1.0.0
status: ACTIVE

input:
  type: SLICE
  sliceTypes: [CORE]
  entityTypes: [PRODUCT, BRAND, CATEGORY]

target:
  type: OPENSEARCH
  
  # 연결 정보 (환경변수로 주입)
  endpoint: ${OPENSEARCH_ENDPOINT}
  
  # 인덱스 이름 패턴 (IVM이 알아야 하는 유일한 인덱스 정보)
  indexPattern: "ivm-products-{tenantId}"
  
  # 인증 (선택)
  auth:
    type: BASIC  # BASIC, IAM, NONE
    username: ${OPENSEARCH_USERNAME:-}
    password: ${OPENSEARCH_PASSWORD:-}

# 문서 ID 생성 규칙 (멱등성 보장)
docId:
  pattern: "{tenantId}__{entityKey}"

# 필드 매핑 (선택 - 생략 시 Slice payload 그대로 전송)
# 이것도 사실 OpenSearch dynamic mapping에 맡기는 게 더 SOTA
fieldMapping:
  enabled: false  # true면 아래 fields 적용, false면 pass-through

# 커밋 설정
commit:
  batchSize: 1000
  timeoutMs: 30000
```

### 2-2. AWS Personalize SinkRule

```yaml
kind: SINKRULE
id: sinkrule.personalize.product-reco
version: 1.0.0
status: ACTIVE

input:
  type: SLICE
  sliceTypes: [CORE]
  entityTypes: [PRODUCT]

target:
  type: PERSONALIZE
  
  # 연결 정보
  region: ${AWS_REGION}
  
  # S3 전달 경로 (Personalize가 읽어갈 위치)
  s3Bucket: ${PERSONALIZE_S3_BUCKET}
  s3Prefix: "ivm-exports/{tenantId}/"
  
  # 포맷
  format: JSONL

docId:
  pattern: "{entityKey}"

commit:
  batchSize: 10000
```

### 2-3. Kafka SinkRule

```yaml
kind: SINKRULE
id: sinkrule.kafka.product-events
version: 1.0.0
status: ACTIVE

input:
  type: SLICE
  sliceTypes: [CORE]

target:
  type: KAFKA
  
  bootstrapServers: ${KAFKA_BOOTSTRAP_SERVERS}
  topic: "ivm.products.{tenantId}"
  
  # 파티션 키 (순서 보장)
  partitionKey: "{entityKey}"

docId:
  pattern: "{tenantId}:{entityKey}:v{version}"
```

---

## 3. 인프라 설정은 어디서?

### 3-1. OpenSearch

```bash
# OpenSearch 운영팀이 관리 (IVM과 무관)
# 인덱스 템플릿, ISM 정책 등

# 템플릿 생성 (OpenSearch 운영팀)
curl -X PUT "localhost:9200/_index_template/ivm-products-template" -H 'Content-Type: application/json' -d'
{
  "index_patterns": ["ivm-products-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1
    },
    "mappings": {
      "properties": {
        "sku": { "type": "keyword" },
        "name": { "type": "text", "analyzer": "korean" }
      }
    }
  }
}'

# ISM 정책 (인덱스 수명 주기)
curl -X PUT "localhost:9200/_plugins/_ism/policies/ivm-rollover-policy" -d'...'
```

### 3-2. IVM이 하는 일

```kotlin
// IVM은 단순히 데이터를 보낼 뿐
class OpenSearchSinkAdapter(private val config: SinkRuleConfig) {
    
    suspend fun ship(tenantId: TenantId, entityKey: EntityKey, payload: String) {
        val indexName = config.indexPattern.replace("{tenantId}", tenantId.value)
        val docId = config.docIdPattern
            .replace("{tenantId}", tenantId.value)
            .replace("{entityKey}", entityKey.value)
        
        // 그냥 보내기만 함. 인덱스 설정? 몰라.
        client.put("$endpoint/$indexName/_doc/$docId") {
            setBody(payload)
        }
    }
}
```

---

## 4. Q&A

### Q1: 인덱스가 없으면 어떻게 되나요?

**A**: OpenSearch의 `auto_create_index` 설정에 따라 자동 생성됨.
- Index Template이 있으면 해당 설정 적용
- 없으면 dynamic mapping 적용
- IVM은 신경 안 씀

### Q2: 필드 타입이 안 맞으면?

**A**: OpenSearch가 에러 반환 → IVM이 에러 로깅 → 운영팀이 대응
- IVM이 미리 검증하는 건 과한 결합
- OpenSearch 운영팀이 매핑 수정

### Q3: 필드 매핑이 필요한 경우는?

**A**: Slice 필드명과 OpenSearch 필드명이 다를 때만.
- 대부분의 경우 Slice 구조 = OpenSearch 문서 구조
- 다를 때만 `fieldMapping.enabled: true`

### Q4: SDK가 OpenSearch를 직접 조회하려면?

**A**: SDK가 필요한 건 `indexPattern`과 `endpoint`뿐.
- SinkRule에서 읽어서 사용
- 인덱스 매핑은 몰라도 됨 (OpenSearch가 알아서 처리)

---

## 5. 비교: AS-IS vs TO-BE

### AS-IS (Over-engineering)

```yaml
# IVM이 인덱스 설정까지 관리 (X)
target:
  type: OPENSEARCH
  templateFile: opensearch/product-template.json  # 과함
  settings:
    number_of_shards: 3  # 과함
  mapping:
    properties:
      sku: { type: keyword }  # 과함
```

### TO-BE (Minimal Coupling)

```yaml
# IVM은 라우팅 정보만
target:
  type: OPENSEARCH
  endpoint: ${OPENSEARCH_ENDPOINT}
  indexPattern: "ivm-products-{tenantId}"
```

---

## 6. 정리

| 항목 | IVM 책임 | Sink 운영팀 책임 |
|------|---------|-----------------|
| 데이터 변환 | ✅ (Slice → payload) | - |
| 라우팅 | ✅ (endpoint, index) | - |
| 멱등성 | ✅ (doc_id) | - |
| 재시도 | ✅ (transient failure) | - |
| 인덱스 생성 | - | ✅ (template) |
| 인덱스 매핑 | - | ✅ |
| 인덱스 설정 | - | ✅ (shards, replicas) |
| 검색 최적화 | - | ✅ (analyzer, tokenizer) |
| 모니터링 | IVM 전송 메트릭 | Sink 시스템 메트릭 |

**SOTA**: IVM은 **우체부**, Sink 시스템은 **수취인**. 우체부가 수취인 집 인테리어를 결정하지 않는다.

---

## 7. 구현 우선순위

1. **P0**: SinkRule에서 `endpoint`, `indexPattern`, `docId` 만 정의
2. **P1**: OpenSearch Index Template은 별도 운영 (IaC로 관리)
3. **P2**: SDK에서 SinkRule 읽어서 조회 시 사용

---

## 8. 파일 정리

기존에 만든 과도한 설정 파일 삭제:
- ❌ `opensearch/product-index-template.json` (OpenSearch 운영팀이 별도 관리)
- ✅ `sinkrule-opensearch-product.v1.yaml` (최소 정보만)
