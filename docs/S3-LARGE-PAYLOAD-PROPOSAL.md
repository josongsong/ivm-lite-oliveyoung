# S3 대용량 Payload 패턴 제안

**작성일**: 2026-02-21  
**상태**: 제안 (미구현)

---

## 배경

SinkEvent의 `payload`(View JSON)가 DynamoDB 아이템 400KB 제한을 초과할 경우 대응 방안.

| 구간 | 제한 |
|------|------|
| DynamoDB 아이템 | 400KB |
| SQS 메시지 | 256KB |

일반 상품 JSON은 수 KB~수십 KB 수준이지만, 옵션/이미지가 많은 상품은 100KB 이상 가능.

---

## 제안: S3 참조 패턴

**원칙**: payload가 임계값(예: 300KB) 초과 시 S3에 저장하고, 메시지에는 S3 키만 담기

```
[Ingest]
  View Compose
    ↓
  payload.size > THRESHOLD?
    YES → S3.put(key, payload) → payloadRef = "s3://bucket/key"
    NO  → payloadRef = payload (인라인)
    ↓
  SinkEvent(payloadRef=..., payloadLocation="inline"|"s3")
    ↓
  DynamoDB 저장

[Lambda/Sink]
  payloadLocation == "s3"?
    YES → S3.get(key) → payload
    NO  → payload 그대로 사용
```

---

## 의견: **당분간 보류 권장**

### 이유

1. **실제 니즈 불명확**
   - 현재 상품 스키마 기준 400KB 초과 사례 드묾
   - `.tmp/product/UA11279226.json` 등 샘플은 ~20–50KB 수준

2. **복잡도 증가**
   - S3 버킷/권한 추가
   - Lambda에서 S3 읽기 로직
   - 실패 시 재시도/일관성 처리

3. **모니터링 선행**
   - 프로덕션에서 payload 크기 분포 수집
   - 400KB 근접/초과 비율 확인 후 판단

### 권장 단계

| 단계 | 액션 |
|------|------|
| 1 (현재) | payload 크기 로깅/메트릭 추가 |
| 2 | 400KB 초과 발생 시 알림 설정 |
| 3 | 실제 초과 사례 확인 후 S3 패턴 검토 |

### 구현 시 고려사항

- **임계값**: 300KB (메타데이터 여유)
- **S3 키**: `payloads/{tenantId}/{entityKey}/{version}/{viewType}.json`
- **TTL**: Sink 처리 완료 후 7일 (SinkEvent TTL과 동일)
- **멱등성**: 동일 키 재업로드 시 덮어쓰기 허용

---

## 결론

**지금 당장 구현하지 않고**, payload 크기 모니터링을 먼저 도입한 뒤, 실제 초과 사례가 확인되면 S3 패턴을 적용하는 것을 권장합니다.
