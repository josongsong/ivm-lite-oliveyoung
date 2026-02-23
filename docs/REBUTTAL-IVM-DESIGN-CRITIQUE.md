# IVM-Lite 설계 비판에 대한 반박자료

> **목적**: "ChangeSet 모델 없음", "Projection 엔진 수준" 비판에 대한 사실 확인 및 반박

---

## 1. ChangeSet 모델 관련 비판

### 비판 내용
> "ChangeSet 모델이 없다. 이벤트 구조, change diff 구조, old vs new 비교 방식이 명확히 없음."
> "진짜 IVM 엔진은 `ChangeSet { path, oldValue, newValue }` 같은 atomic diff 기반으로 slice rebuild 여부를 판단함."
> "현재 설계는 경로 매핑 기반 rebuild까지만 보임."

### 반박: ChangeSet 모델 **존재함**

#### 1.1 구현된 ChangeSet 도메인

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/changeset/domain/`

```kotlin
// ChangeSet.kt
data class ChangeSet(
    val changeSetId: String,
    val tenantId: TenantId,
    val entityType: String,
    val entityKey: EntityKey,
    val fromVersion: Long,
    val toVersion: Long,
    val changeType: ChangeType,           // CREATE, UPDATE, DELETE, NO_CHANGE
    val changedPaths: List<ChangedPath>,  // atomic diff 목록
    val impactedSliceTypes: Set<String>,
    val impactMap: Map<String, ImpactDetail>,
    val payloadHash: String,
)

data class ChangedPath(
    val path: String,       // RFC6901 JSON Pointer (예: /options/3/price)
    val valueHash: String,  // sha256:... (변경 후 값의 해시)
)
```

- **이벤트 구조**: `changeType`, `fromVersion`, `toVersion`으로 old/new 버전 명시
- **change diff 구조**: `changedPaths` = path 단위 atomic diff 배열
- **old vs new 비교**: `ChangeSetBuilder.diffJsonPointers()`에서 `walkDiff()`로 재귀 비교

#### 1.2 계약 정의 (SSOT)

**위치**: `src/main/resources/contracts/v1/changeset.v1.yaml`

```yaml
fields:
  changeType: { type: enum, values: [CREATE, UPDATE, DELETE, NO_CHANGE] }
  changedPaths:
    type: array
    items:
      type: object
      fields:
        path: { type: string, rule: RFC6901_JSON_POINTER }
        valueHash: { type: string, rule: "sha256:<hex>" }
impact:
  impactedSliceTypes: { type: array, items: string }
  impactMap: ...
```

#### 1.3 atomic diff 기반 rebuild 판단

**ChangeSetBuilder** (`ChangeSetBuilder.kt`):
- `diffJsonPointers(fromPayload, toPayload)` → JSON Pointer 경로별 diff
- 배열 인덱스 포함: `/options/3/price` 형태로 정확한 경로 추출

**ImpactCalculator** (`ImpactCalculator.kt`):
- `changeSet.changedPaths`와 `ruleSet.impactMap` 매칭
- **path 기반**으로 영향받는 SliceType 계산 → `impactedSliceTypes`
- `SlicingWorkflow.executeIncremental()`에서 `impactedTypes`만 재빌드

**결론**: 경로 매핑 기반 rebuild가 **atomic diff(path) 기반**으로 동작함.  
비판의 "경로 매핑 기반 rebuild까지만"은 사실이나, 그 경로는 **ChangeSet.changedPaths**에서 atomic diff로 추출된 것임.

#### 1.4 oldValue/newValue vs valueHash

| 비판 요구 | 현재 구현 | 설명 |
|----------|----------|------|
| `oldValue`, `newValue` | `path`, `valueHash` | rebuild 판단에는 **어떤 경로가 변경됐는지**가 핵심. 값 자체는 RawData에 있음. |
| 값 기반 판단 | path + impactMap | Slice rebuild 여부 = "이 경로가 이 SliceType에 영향하는가?" → path 매칭으로 충분 |

**설계 선택**:
- `valueHash`: payload 외부화(S3) 시 무결성 검증용
- `oldValue/newValue` 미포함: RawData 저장소에 from/to payload 존재. 필요 시 조회 가능.
- **증분 계산**에 old/new 값이 필요하면: `RawDataRepository.get(version)`으로 조회 후 사용 가능 (확장 포인트)

---

## 2. Slice 내부 계산 규칙 (buildRules) 관련 비판

### 비판 내용
> "buildRules = PassThrough는 Projection 엔진이지 IVM 엔진이 아님."
> "진짜 IVM는 집계 유지, 파생 필드 증분 계산, 정렬키 재계산, facet count 증분 업데이트까지 해야 함."
> "지금은 필드 복사 + join 단계임."

### 반박: 의도된 스코프 + 확장 경로

#### 2.1 현재 구현 범위 (사실 인정)

| 기능 | 현재 | 비판 |
|-----|------|-----|
| PassThrough | ✅ | Projection |
| MapFields | ✅ | Projection |
| Join (LOOKUP) | ✅ | Projection + Join |
| 집계 (SUM, COUNT 등) | ❌ | IVM 필수 |
| 파생 필드 증분 계산 | ❌ | IVM 필수 |
| 정렬키 재계산 | ❌ | IVM 필수 |
| facet count 증분 | ❌ | IVM 필수 |

**인정**: 현재 Slice 빌드는 "Projection + Join" 수준임.

#### 2.2 IVM-Lite의 포지셔닝

- **이름**: "IVM-**Lite**" = 경량 IVM
- **1차 목표**: RawData → Slice → View → Sink 파이프라인, **증분 슬라이싱** (영향받은 Slice만 재빌드)
- **증분 최적화**: ChangeSet 기반 `slicePartial(impactedTypes)`로 **전체 재계산 회피**

즉, "Slice 내부" 증분 계산보다 **"어떤 Slice를 다시 만들지"**에 초점을 둔 설계.

#### 2.3 확장 경로 (Roadmap)

`SliceBuildRules`는 sealed class로 확장 가능:

```kotlin
sealed class SliceBuildRules {
    data class PassThrough(val fields: List<String>) : SliceBuildRules()
    data class MapFields(val mappings: Map<String, String>) : SliceBuildRules()
    // 향후: Aggregate, Derived, SortKey 등 추가 가능
}
```

**RFC-IMPL-010** 등에서 `buildRules` 확장이 논의된 바 있음.  
집계/파생/정렬키 등은 **Phase 2+** 로드맵 후보.

#### 2.4 실사용 시나리오

- **캐탈로그/검색 파이프라인**: PassThrough + Join으로 대부분 커버
- **집계/팩트**: 별도 집계 파이프라인 또는 외부 시스템에서 처리
- **IVM-Lite 역할**: Contract 기반 슬라이싱 + 증분 rebuild + View 조합 + Sink 전송

---

## 3. 요약 표

| 비판 | 사실 여부 | 반박/보완 |
|-----|----------|----------|
| ChangeSet 모델 없음 | ❌ | ChangeSet, ChangedPath, changeset.v1.yaml 존재. diffJsonPointers로 atomic path diff 생성. |
| old vs new 비교 없음 | ⚠️ 부분 | fromVersion/toVersion, changeType으로 버전 비교. ChangedPath는 path+valueHash. oldValue/newValue는 RawData에서 조회 가능. |
| 경로 매핑 기반 rebuild만 | ✅ | 맞음. 단, 그 경로는 ChangeSet.changedPaths(atomic diff)에서 옴. |
| PassThrough = Projection | ✅ | 맞음. IVM-Lite는 Projection+Join 수준을 1차 스코프로 함. |
| 집계/파생/정렬키 없음 | ✅ | 맞음. 로드맵 확장 포인트. |

---

## 4. 결론

1. **ChangeSet 모델**: 존재하며, JSON Pointer 기반 atomic diff로 slice rebuild 판단에 사용됨.
2. **oldValue/newValue**: rebuild 판단에는 path만으로 충분. 값이 필요하면 RawData 조회로 확장 가능.
3. **buildRules**: 현재는 Projection+Join. IVM-Lite는 "어떤 Slice를 다시 만들지"에 초점을 두고, Slice 내부 집계/파생은 확장 로드맵으로 남김.

---

## 5. 참조 (코드/문서 위치)

| 항목 | 경로 |
|-----|-----|
| ChangeSet 도메인 | `pkg/changeset/domain/ChangeSet.kt` |
| ChangeSetBuilder | `pkg/changeset/domain/ChangeSetBuilder.kt` |
| ImpactCalculator | `pkg/changeset/domain/ImpactCalculator.kt` |
| changeset 계약 | `contracts/v1/changeset.v1.yaml` |
| SliceBuildRules | `pkg/contracts/domain/RuleSetContract.kt` |
| SlicingWorkflow (INCREMENTAL) | `pkg/orchestration/application/SlicingWorkflow.kt` |
| 가이드 (ChangeSet, ImpactMap) | `docs/guides/raw-to-slicing-to-view-to-sink.md` |
