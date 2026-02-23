# Sink Contract Compatibility Rules (SSOT)

**Version**: 1.0
**Status**: MANDATORY
**Enforcement**: Automated Tests

---

## 원칙

1. **하위 호환성 우선**: 기존 플러그인이 새 계약으로 동작해야 함
2. **명시적 Breaking Changes**: 메이저 버전업으로만 허용
3. **Unknown Fields 허용**: 플러그인은 미지원 필드를 무시해야 함

---

## 허용되는 변경 (Backward Compatible)

### ✅ 1. 필드 추가 (Nullable/Default만)

**허용**:
```kotlin
// v1.0
data class V1(
    val tenantId: String,
    val entityKey: String
)

// v1.1 (호환)
data class V1(
    val tenantId: String,
    val entityKey: String,
    val newField: String? = null  // ✅ nullable
)
```

**금지**:
```kotlin
// v1.1 (비호환!)
data class V1(
    val tenantId: String,
    val entityKey: String,
    val newField: String  // ❌ required (기존 데이터 파싱 불가)
)
```

---

### ✅ 2. Enum 값 추가

**허용**:
```kotlin
enum class ErrorCategory {
    RETRYABLE,
    NON_RETRYABLE,
    POISON_PILL,
    QUARANTINE  // ✅ 추가 가능
}
```

**필수**: 플러그인은 `UNKNOWN` 처리 로직 필요
```kotlin
when (error.category) {
    ErrorCategory.RETRYABLE -> retry()
    ErrorCategory.NON_RETRYABLE -> dlq()
    else -> {  // ✅ Unknown 처리
        logger.warn("Unknown category: ${error.category}")
        dlq()  // 안전하게 DLQ 처리
    }
}
```

---

### ✅ 3. Deprecated 필드 표시

**허용**:
```kotlin
@Deprecated("Use 'viewType' instead", ReplaceWith("viewType"))
val viewName: String? = null
```

**규칙**:
- 2개 메이저 버전 동안 유지
- 문서에 Migration 가이드 필수

---

## 금지되는 변경 (Breaking Changes)

### ❌ 1. 필드 삭제

**금지**:
```kotlin
// v1.0
data class V1(
    val tenantId: String,
    val entityKey: String,
    val viewName: String  // ← 기존 필드
)

// v2.0 (Breaking!)
data class V2(
    val tenantId: String,
    val entityKey: String
    // ❌ viewName 삭제 → 메이저 업 필요
)
```

**대안**: Deprecated 후 메이저 업

---

### ❌ 2. 필드 타입 변경

**금지**:
```kotlin
// v1.0
val entityVersion: Long

// v2.0 (Breaking!)
val entityVersion: String  // ❌ 타입 변경
```

**대안**: 새 필드 추가 + 기존 필드 Deprecated

---

### ❌ 3. Required 필드 추가

**금지**:
```kotlin
// v1.1 (Breaking!)
data class V1(
    val tenantId: String,
    val entityKey: String,
    val newRequiredField: String  // ❌ 기본값 없음
)
```

---

### ❌ 4. Enum 값 삭제

**금지**:
```kotlin
enum class SinkStatus {
    SUCCESS,
    // PARTIAL_SUCCESS,  // ❌ 삭제 금지
    ALREADY_PROCESSED
}
```

---

## 버전 관리 전략

### Semantic Versioning

```
MAJOR.MINOR.PATCH

MAJOR: Breaking changes (필드 삭제, 타입 변경, Required 추가)
MINOR: Backward-compatible (필드 추가, Enum 추가)
PATCH: 버그 수정, 문서 업데이트
```

### 예시

- v1.0 → v1.1: 필드 추가 (nullable)
- v1.1 → v2.0: 필드 삭제, 타입 변경
- v2.0 → v2.1: Enum 값 추가

---

## Migration 프로토콜

### 1. Dual-Version Support (권장)

**기간**: 최소 1개월
```kotlin
sealed interface SinkPayload {
    data class V1(...) : SinkPayload
    data class V2(...) : SinkPayload  // 새 버전 추가
}

// 플러그인은 둘 다 지원
when (payload) {
    is V1 -> handleV1(payload)
    is V2 -> handleV2(payload)
}
```

### 2. Feature Flag

```kotlin
val ENABLE_V2_CONTRACT = System.getenv("ENABLE_V2_CONTRACT")?.toBoolean() ?: false

if (ENABLE_V2_CONTRACT) {
    // v2 처리
} else {
    // v1 처리 (Fallback)
}
```

### 3. Gradual Rollout

1. Week 1: 플러그인 배포 (v1/v2 둘 다 지원)
2. Week 2: 엔진에서 v2 발행 시작 (canary 10%)
3. Week 3: v2 비율 증가 (50% → 100%)
4. Week 4: v1 제거 (플러그인 업데이트)

---

## 자동 검증

### Compatibility Test (필수)

```kotlin
@Test
fun `v1_1 should accept v1_0 payloads`() {
    val v1_0_json = """{"tenantId":"t1","entityKey":"e1"}"""
    
    // v1.1 파서가 v1.0 데이터를 읽을 수 있어야 함
    val payload = Json.decodeFromString<SinkPayload.V1>(v1_0_json)
    
    payload.tenantId shouldBe "t1"
    payload.newField shouldBe null  // ✅ 기본값
}

@Test
fun `v1_0 plugins should ignore unknown fields`() {
    val v1_1_json = """{"tenantId":"t1","entityKey":"e1","newField":"value"}"""
    
    // v1.0 파서가 미지원 필드를 무시해야 함
    val payload = Json.decodeFromString<SinkPayload.V1>(v1_1_json)
    
    payload.tenantId shouldBe "t1"
    // newField는 무시됨 (에러 없음)
}
```

### Schema Evolution Matrix

| 변경 | v1.0 → v1.1 | v1.1 → v2.0 | 테스트 |
|------|-------------|-------------|--------|
| 필드 추가 (nullable) | ✅ | ✅ | ✅ |
| 필드 삭제 | ❌ | ✅ (메이저) | ✅ |
| 타입 변경 | ❌ | ✅ (메이저) | ✅ |
| Enum 추가 | ✅ | ✅ | ✅ |
| Enum 삭제 | ❌ | ✅ (메이저) | ✅ |

---

## Runbook: Breaking Change 배포

1. **사전 공지** (2주 전)
   - 플러그인 개발자에게 변경사항 공유
   - Migration 가이드 문서 작성

2. **Dual-Version 플러그인 배포**
   - v1/v2 둘 다 처리 가능하도록 업데이트
   - 배포 완료 확인

3. **엔진 계약 버전 업그레이드**
   - Feature Flag로 점진적 롤아웃
   - 에러율 모니터링

4. **v1 Deprecated 마크**
   - 로그에 경고 메시지 추가
   - 메트릭 수집 (v1 사용 비율)

5. **v1 제거** (3개월 후)
   - v2만 남김
   - 플러그인 강제 업데이트

---

**작성자**: SOTA Platform Team
**검수**: Architecture Review Board
**시행일**: 2026-02-12
