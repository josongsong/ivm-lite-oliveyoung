# RFC-020: Sink Reliability & Data Consistency

| 항목 | 값 |
|------|-----|
| RFC | 020 |
| 상태 | PROPOSED |
| 작성일 | 2026-02-16 |
| 의존 | RFC-017 (Sink Plugin Architecture) |
| 범위 | SinkStreamHandler, SinkPlugin, SinkLedger, OpenSearch versioning, DELETE 처리 |

---

## 1. 배경 및 동기

RFC-017에서 Sink Plugin Architecture를 정의하고, SQS 중간 단계를 제거하여 Lambda에서 SinkPlugin을 직접 실행하는 구조로 전환했다.

**현재 파이프라인:**
```
DynamoDB SinkEvent(PENDING) → DynamoDB Streams → Lambda(SinkStreamHandler) → SinkPlugin 직접 실행
```

이 구조는 단순하고 효율적이나, CDC(Change Data Capture) best practice 관점에서 다음 리스크가 존재한다:

| # | 리스크 | 현재 상태 | 영향 |
|---|--------|----------|------|
| R1 | REMOVE 이벤트 무시 | SinkStreamHandler가 skip | Sink에 삭제된 데이터 잔존 |
| R2 | 버전 충돌 무방비 | 무조건 덮어쓰기 | 구버전이 신버전 덮어쓸 수 있음 |
| R3 | 실패 레코드 유실 | 로그만 남김 | 재처리 불가, 운영 사각지대 |
| R4 | SinkLedger 미사용 | 인터페이스만 존재 | 멱등성 검증 부재 |
| R5 | SinkEvent 상태 미갱신 | PENDING 그대로 | Admin UI에서 처리 여부 불명 |

---

## 2. 목표

1. **데이터 정합성**: Sink와 Source(DynamoDB) 간 eventual consistency 보장
2. **실패 복원력**: N회 재시도 후 실패 레코드를 별도 저장 → 수동/자동 재처리
3. **순서 안전성**: 늦게 도착한 구버전이 신버전을 덮지 않도록 보장
4. **운영 가시성**: Admin UI에서 Sink 처리 상태 추적 가능

---

## 3. 설계

### 3.1 REMOVE 이벤트 처리 (R1)

**원칙**: DynamoDB REMOVE → Sink DELETE. 데이터가 Source에서 삭제되면 Sink에서도 삭제.

```
DynamoDB REMOVE event
  └─ oldImage에서 tenantId, entityKey, sinkTargets 추출
  └─ 각 target에 DELETE 작업 실행
```

**SinkPlugin 인터페이스 확장:**

```kotlin
interface SinkPlugin {
    // 기존
    suspend fun execute(payload: SinkPayload): Either<SinkError, SinkResult>
    suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult>

    // 신규: DELETE 지원
    val supportsDelete: Boolean get() = false
    suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String> = emptyMap()
    ): Either<SinkError, SinkResult> {
        return Either.Left(SinkError.NonRetryableError(
            reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
            message = "Delete not supported by ${pluginId}"
        ))
    }
}
```

**플러그인별 DELETE 구현:**

| Plugin | DELETE 방식 |
|--------|-----------|
| OpenSearch | `DELETE /{index}/_doc/{docId}` |
| S3 | `DeleteObject(key)` |
| Personalize | PutItems with `__deleted=true` (Personalize 관례) |

**SinkStreamHandler REMOVE 처리:**

```kotlin
"REMOVE" -> {
    val oldItem = record.dynamodb.oldImage ?: return@forEach
    val tenantId = oldItem["tenantId"]?.s ?: return@forEach
    val entityKey = oldItem["entityKey"]?.s ?: return@forEach
    val sinkTargets = oldItem["sinkTargets"]?.ss ?: return@forEach

    sinkTargets.forEach { target ->
        val plugin = pluginRegistry.resolve(target) ?: return@forEach
        if (plugin.supportsDelete) {
            plugin.delete(tenantId, entityKey)
        }
    }
}
```

### 3.2 버전 충돌 방지 (R2)

**문제**: DynamoDB Streams는 순서를 보장하지만, Lambda 재시도/병렬 실행 시 이벤트 순서가 뒤집힐 수 있다. 구버전(v2)이 신버전(v3) 이후에 도착하면 데이터 후퇴.

**해법**: OpenSearch 외부 버전화(External Versioning)

```
기존 Bulk body:
{"index":{"_index":"idx","_id":"doc1"}}
{"field":"value"}

변경 Bulk body:
{"index":{"_index":"idx","_id":"doc1","version":3,"version_type":"external"}}
{"field":"value"}
```

- `version_type: external` → incoming version > current version 일 때만 반영
- incoming version <= current version → 409 Conflict (무시, 정상 동작)
- 효과: **늦게 도착한 구버전이 자동으로 drop**

**OpenSearchSinkPlugin.buildBulkBody 변경:**

```kotlin
private fun buildBulkBody(payloads: List<SinkPayload>): String {
    val sb = StringBuilder()
    payloads.forEach { payload ->
        when (payload) {
            is SinkPayload.V1 -> {
                val index = resolveIndex(payload.tenantId)
                val docId = "${payload.tenantId}__${payload.entityKey}"

                // 외부 버전화: 구버전 자동 무시
                sb.append("""{"index":{"_index":"$index","_id":"$docId","version":${payload.entityVersion},"version_type":"external"}}""")
                sb.append('\n')
                sb.append(SinkJson.json.encodeToString(payload.viewData))
                sb.append('\n')
            }
        }
    }
    return sb.toString()
}
```

**409 Conflict 처리:**

Bulk API 응답에서 개별 item의 `status: 409`는 "이미 더 높은 버전 존재"를 의미. 이 경우 `SinkStatus.ALREADY_PROCESSED`로 처리 (에러 아님).

```kotlin
// Bulk 응답 파싱 시
when (itemStatus) {
    200, 201 -> succeeded.add(SinkResult(..., status = SinkStatus.SUCCESS))
    409 -> succeeded.add(SinkResult(..., status = SinkStatus.ALREADY_PROCESSED))
    429 -> retryableFailed.add(...)
    else -> nonRetryableFailed.add(...)
}
```

**S3**: S3는 외부 버전화가 없으므로, 키에 버전을 포함하여 해결:
```
views/{viewType}/{entityKey}/v{entityVersion}.json   ← 이미 이 구조 사용 중 (OK)
views/{viewType}/{entityKey}/latest.json              ← 최신 버전 (conditional write)
```

**Personalize**: PutItems는 항상 최신으로 덮어쓰기 (Personalize 자체가 이를 관리).

### 3.3 실패 레코드 관리 (R3)

**원칙**: Lambda에서 N회 시도 후 실패한 레코드는 DynamoDB 실패 테이블에 저장. Admin UI에서 조회/재처리 가능.

**재시도 전략:**

```
DynamoDB Streams 자체 재시도 (최대 24시간 보관)
  └─ Lambda 실패 → 배치 재전달 (자동)
  └─ BisectBatchOnFunctionError: 실패 배치 이분 (문제 레코드 격리)
  └─ maximumRetryAttempts: 3 (3회 초과 시 Destination으로)
  └─ On-failure destination: Lambda → DynamoDB 실패 테이블 저장
```

**SinkStreamHandler 재시도 로직:**

```kotlin
// 에러 분류에 따른 처리
when (error) {
    is SinkError.RetryableError -> {
        // Lambda 전체 실패 → DynamoDB Streams가 자동 재시도
        throw RetryableSinkException(error.message)
    }
    is SinkError.NonRetryableError -> {
        // 실패 레코드 저장 → 건너뛰기
        saveFailedRecord(sinkEventId, target, error)
        errorCount++
    }
    is SinkError.PoisonPillError -> {
        // 실패 레코드 저장 → 건너뛰기 (재시도 무의미)
        saveFailedRecord(sinkEventId, target, error)
        errorCount++
    }
}
```

**실패 레코드 저장소 (DynamoDB):**

```
테이블: ivm-sink-failures-{env}
PK: FAILURE#{sinkEventId}#{target}
SK: ATTEMPT#{timestamp}

속성:
- sinkEventId: String
- target: String (opensearch, s3, personalize)
- errorCategory: RETRYABLE | NON_RETRYABLE | POISON_PILL
- errorReasonCode: ErrorReasonCode
- errorMessage: String
- payload: String (원본 SinkPayload JSON)
- attemptCount: Int
- createdAt: String (ISO-8601)
- status: FAILED | RETRIED | RESOLVED
- ttl: Long (30일 후 자동 삭제)
```

**Admin UI 통합:**

```
GET  /admin/api/sink-failures                  → 목록 조회 (필터: target, errorCategory, 기간)
GET  /admin/api/sink-failures/{id}             → 상세 조회
POST /admin/api/sink-failures/{id}/retry       → 수동 재처리
POST /admin/api/sink-failures/retry-batch      → 배치 재처리 (조건부)
```

### 3.4 SinkLedger 통합 (R4)

**현재 상태**: `SinkLedger` 인터페이스와 `InMemorySinkLedger`가 존재하지만 SinkStreamHandler에서 미사용.

**통합 방안:**

```kotlin
// SinkStreamHandler에서 Ledger 체크 추가
sinkTargetsRaw.forEach targetLoop@{ target ->
    val plugin = pluginRegistry.resolve(target) ?: return@targetLoop

    // 1. Ledger tryStart: 이미 처리된 이벤트인지 확인
    val canProcess = sinkLedger.tryStart(
        pluginId = target,
        idempotencyKey = idempotencyKey,
        payloadDigest = payloadDigest,
        contractVersion = "1.0"
    ).getOrElse { return@targetLoop }

    if (!canProcess) {
        logger.log("Already processed: $idempotencyKey for $target")
        return@targetLoop
    }

    // 2. Plugin 실행
    val result = plugin.execute(sinkPayload)

    // 3. Ledger 결과 기록
    result.fold(
        { error -> sinkLedger.fail(target, idempotencyKey, error, 1) },
        { sinkResult -> sinkLedger.complete(target, idempotencyKey, sinkResult) }
    )
}
```

**DynamoDB SinkLedger 구현:**

```
테이블: ivm-sink-ledger-{env}
PK: LEDGER#{pluginId}#{idempotencyKey}

속성:
- pluginId: String
- idempotencyKey: String
- payloadDigest: String
- contractVersion: String
- status: PROCESSING | COMPLETED | FAILED
- attemptCount: Int
- createdAt: String
- processedAt: String?
- lastError: String? (SinkError JSON)
- resultMetadata: Map<String, String>?
- ttl: Long (7일)
```

**Conditional Write로 Optimistic Lock:**

```kotlin
// tryStart: attribute_not_exists OR status != COMPLETED
PutItemRequest.builder()
    .conditionExpression("attribute_not_exists(PK) OR #status <> :completed")
    .expressionAttributeNames(mapOf("#status" to "status"))
    .expressionAttributeValues(mapOf(":completed" to AttributeValue.builder().s("COMPLETED").build()))
```

### 3.5 SinkEvent 상태 갱신 (R5)

**현재 문제**: SinkStreamHandler가 Plugin 실행 후 SinkEvent 상태를 PENDING에서 COMPLETED/FAILED로 갱신하지 않음.

**해법**: Plugin 실행 결과에 따라 DynamoDB SinkEvent 상태 업데이트.

```kotlin
// SinkStreamHandler 처리 완료 후
val allTargetsSucceeded = targetResults.all { it.isRight() }

val updateRequest = UpdateItemRequest.builder()
    .tableName(sinkEventTableName)
    .key(mapOf("PK" to ..., "SK" to ...))
    .updateExpression("SET #status = :status, processedAt = :now")
    .expressionAttributeNames(mapOf("#status" to "status"))
    .expressionAttributeValues(mapOf(
        ":status" to if (allTargetsSucceeded) "COMPLETED" else "FAILED",
        ":now" to Instant.now().toString()
    ))
    .build()
```

**상태 전이:**
```
PENDING → COMPLETED (모든 target 성공)
PENDING → FAILED (하나라도 NonRetryable 실패)
PENDING → PENDING (Retryable 실패 → DynamoDB Streams 재시도)
```

---

## 4. Lambda Event Source Mapping 설정

```hcl
resource "aws_lambda_event_source_mapping" "sink_stream" {
  event_source_arn  = aws_dynamodb_table.sink_events.stream_arn
  function_name     = aws_lambda_function.sink_stream_handler.arn

  starting_position = "LATEST"
  batch_size        = 100
  maximum_batching_window_in_seconds = 1

  # 부분 배치 실패 격리
  bisect_batch_on_function_error = true

  # 최대 재시도 (3회 초과 시 on_failure destination)
  maximum_retry_attempts = 3

  # 실패 이벤트 저장 (SQS DLQ 또는 Lambda destination)
  destination_config {
    on_failure {
      destination_arn = aws_sqs_queue.sink_stream_dlq.arn
    }
  }

  # 레코드 최대 보존 시간 (기본 24시간)
  maximum_record_age_in_seconds = 86400
}
```

---

## 5. 구현 단계

### Phase A: 기반 (필수, 즉시)

| 태스크 | 파일 | 설명 |
|--------|------|------|
| A-1 | `SinkPlugin.kt` | `delete()` 메서드 + `supportsDelete` 추가 |
| A-2 | `OpenSearchSinkPlugin.kt` | `delete()` 구현 + `buildBulkBody()` 외부 버전화 |
| A-3 | `S3SinkPlugin.kt` | `delete()` 구현 (DeleteObject) |
| A-4 | `SinkStreamHandler.kt` | REMOVE 이벤트 처리 + 에러 분류별 처리 |
| A-5 | `SinkStreamHandler.kt` | SinkEvent 상태 갱신 (COMPLETED/FAILED) |

### Phase B: 안전장치

| 태스크 | 파일 | 설명 |
|--------|------|------|
| B-1 | `DynamoDbSinkLedger.kt` | SinkLedger DynamoDB 구현 |
| B-2 | `SinkStreamHandler.kt` | SinkLedger 통합 (tryStart/complete/fail) |
| B-3 | `DynamoDbSinkFailureRepository.kt` | 실패 레코드 저장소 |
| B-4 | `SinkStreamHandler.kt` | 실패 레코드 저장 로직 |

### Phase C: 운영

| 태스크 | 파일 | 설명 |
|--------|------|------|
| C-1 | Terraform | Lambda Event Source Mapping 정의 |
| C-2 | Terraform | DLQ + Failure 테이블 정의 |
| C-3 | Admin Routes | Sink failure 조회/재처리 API |
| C-4 | Admin UI | Sink Failures 대시보드 |

### Phase D: 고급 (향후)

| 태스크 | 설명 |
|--------|------|
| D-1 | Backfill API (초기 대량 인덱싱) |
| D-2 | OpenSearch Bulk 응답 개별 item 파싱 |
| D-3 | Tombstone 안티패턴 방지 (삭제 후 구버전 upsert 차단) |

---

## 6. SinkStreamHandler 최종 구조 (Phase A+B 완료 후)

```kotlin
override fun handleRequest(event: DynamodbEvent, context: Context): String {
    event.records.forEach { record ->
        when (record.eventName) {
            "INSERT", "MODIFY" -> {
                val newImage = record.dynamodb.newImage
                if (newImage["status"]?.s != "PENDING") return@forEach

                // 1. 페이로드 추출
                val (tenantId, entityKey, version, ...) = extractFields(newImage)

                // 2. 각 target 처리
                val targetResults = sinkTargets.map { target ->
                    val plugin = pluginRegistry.resolve(target) ?: return@map TargetResult.skip(target)

                    // 3. Ledger 체크 (멱등성)
                    val canProcess = sinkLedger.tryStart(target, idempotencyKey, digest, "1.0")
                    if (!canProcess) return@map TargetResult.alreadyProcessed(target)

                    // 4. Plugin 실행
                    val result = plugin.execute(sinkPayload)

                    // 5. 결과 기록
                    result.fold(
                        { error ->
                            sinkLedger.fail(target, idempotencyKey, error, 1)
                            when (error) {
                                is RetryableError -> TargetResult.retryable(target, error)
                                is NonRetryableError -> {
                                    saveFailedRecord(sinkEventId, target, error, sinkPayload)
                                    TargetResult.failed(target, error)
                                }
                                is PoisonPillError -> {
                                    saveFailedRecord(sinkEventId, target, error, sinkPayload)
                                    TargetResult.poisoned(target, error)
                                }
                            }
                        },
                        { sinkResult ->
                            sinkLedger.complete(target, idempotencyKey, sinkResult)
                            TargetResult.success(target)
                        }
                    )
                }

                // 6. SinkEvent 상태 갱신
                updateSinkEventStatus(sinkEventId, targetResults)

                // 7. Retryable 실패가 있으면 Lambda 실패 → Streams 재시도
                if (targetResults.any { it.isRetryable }) {
                    throw RetryableSinkException("Retryable failures exist")
                }
            }

            "REMOVE" -> {
                // DELETE 처리
                val oldImage = record.dynamodb.oldImage ?: return@forEach
                val (tenantId, entityKey, sinkTargets) = extractDeleteFields(oldImage)

                sinkTargets.forEach { target ->
                    val plugin = pluginRegistry.resolve(target) ?: return@forEach
                    if (plugin.supportsDelete) {
                        plugin.delete(tenantId, entityKey)
                    }
                }
            }
        }
    }
}
```

---

## 7. 데이터 흐름 (최종)

```
┌────────────────────────────────────────────────────────────────┐
│                    IVM Runtime API                              │
│  Ingest → RawData → Slice → View → SinkEvent(PENDING)         │
└──────────────────────────┬─────────────────────────────────────┘
                           │ DynamoDB put
                           ▼
┌────────────────────────────────────────────────────────────────┐
│  DynamoDB: ivm-sink-events                                     │
│  ├─ Status: PENDING → COMPLETED / FAILED                       │
│  ├─ DynamoDB Streams (NEW_AND_OLD_IMAGES)                      │
│  └─ TTL: 7일                                                   │
└──────────────────────────┬─────────────────────────────────────┘
                           │ INSERT/MODIFY/REMOVE
                           ▼
┌────────────────────────────────────────────────────────────────┐
│  Lambda: SinkStreamHandler                                     │
│                                                                │
│  INSERT/MODIFY (PENDING):                                      │
│  ├─ 1. SinkLedger.tryStart() → 멱등성 체크                     │
│  ├─ 2. SinkPlugin.execute() → 직접 실행                        │
│  ├─ 3. 성공 → Ledger.complete() + SinkEvent=COMPLETED          │
│  ├─ 4. Retryable 실패 → throw → Streams 재시도 (최대 3회)      │
│  └─ 5. NonRetryable 실패 → 실패 저장 + SinkEvent=FAILED        │
│                                                                │
│  REMOVE:                                                       │
│  └─ oldImage → plugin.delete(tenantId, entityKey)              │
└───────┬──────────┬──────────┬──────────┬───────────────────────┘
        │          │          │          │
        ▼          ▼          ▼          ▼
   OpenSearch     S3     Personalize   실패 저장소
   (version_type               (ivm-sink-failures)
    :external)                  │
                                ▼
                         Admin UI 재처리
```

---

## 8. 불변식 (Invariants)

| ID | 불변식 | 검증 방법 |
|----|--------|----------|
| I-1 | Source에서 삭제된 데이터는 Sink에도 없어야 한다 | REMOVE → plugin.delete() |
| I-2 | Sink의 데이터 버전 >= 마지막 성공 버전 | OpenSearch version_type:external |
| I-3 | 동일 (idempotencyKey, target) 조합은 최대 1회 처리 | SinkLedger.tryStart() |
| I-4 | 실패한 레코드는 반드시 추적 가능해야 한다 | Failure 테이블 + Admin UI |
| I-5 | SinkEvent 상태는 최종적으로 COMPLETED 또는 FAILED | updateSinkEventStatus() |

---

## 9. 결정 사항 (ADR)

### ADR-1: SQS 대신 DynamoDB Streams 자체 재시도 활용

- **결정**: Lambda 실패 시 DynamoDB Streams가 자동 재시도 (최대 24시간)
- **근거**: SQS 중간 단계 제거로 지연/비용/복잡도 감소
- **트레이드오프**: Streams 보존 24시간 제한 → `maximumRetryAttempts: 3` + DLQ로 보완

### ADR-2: OpenSearch 외부 버전화로 순서 안전성 확보

- **결정**: `version_type: external` 사용
- **근거**: 구버전 자동 drop, 추가 코드 불필요
- **트레이드오프**: OpenSearch 2.x+ 필요 (이전 버전 미지원)

### ADR-3: 실패 레코드를 DynamoDB에 저장 (SQS DLQ 아님)

- **결정**: 실패 레코드를 별도 DynamoDB 테이블에 저장
- **근거**: Admin UI에서 직접 조회/재처리 가능. SQS DLQ는 consume 시 삭제되어 조회 불편
- **트레이드오프**: DLQ는 Event Source Mapping의 on_failure destination으로만 사용 (최후 안전망)

### ADR-4: SinkLedger는 DynamoDB 기반

- **결정**: InMemorySinkLedger → DynamoDbSinkLedger
- **근거**: Lambda는 stateless. 인스턴스 간 공유 상태 필요 → DynamoDB
- **트레이드오프**: DynamoDB 추가 비용 (TTL 7일로 최소화)
