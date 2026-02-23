# RFC-018: Slice 실행 순서/의존성 런타임 강제

## 1. 배경

현재 SlicingEngine은 `ruleSet.slices` 순서 그대로 순회하며, TopoSort/의존성 검증/ExecutionPlan 강제가 없음.

- **문제**: ENRICHED가 CORE보다 먼저 오면 JOIN 실패 가능 (Slice-to-Slice 의존성 도입 시)
- **현재**: JoinExecutor는 RawData→RawData LOOKUP만 수행 → 동일 RuleSet 내 Slice 간 의존성 없음
- **미래**: Slice-to-Slice JOIN 도입 시 의존성 강제 필요

## 2. 구현 완료 (SOTA/DX)

| 항목 | 구현 |
|------|------|
| **의존성 자동 추론** | SliceKind.ENRICHMENT → CORE 의존, joins.targetSliceType (동일 RuleSet 내) |
| **계약 검증 시점** | GatedContractRegistryAdapter.loadRuleSetContract 시 DAG 검증 |
| **병렬 실행** | Wave별 병렬 (toWaves), 동일 Wave 내 Slice는 async/awaitAll |
| **설명 가능성** | SliceExecutionStep.reason ("ENRICHMENT 슬라이스는 CORE 이후 실행" 등) |
| **에러 메시지** | "Slice dependency cycle detected. Involved slices: X → Y. Fix: ..." |

## 3. 구현 방안 (참고)

### 2-1. Phase 1: TopoSort + 검증 (최소 구현)

**목표**: 계약 로드/검증 시점에 순서 검증, 런타임은 기존대로.

1. **의존성 추출**: `SliceDefinition.joins`에서 암시적 의존성 도출
   - `targetEntityType`이 **동일 entityType**이면 → `targetSliceType`이 의존 대상
   - 예: PRODUCT ENRICHED가 BRAND SUMMARY 참조 → 엔티티 간이므로 Slice 순서 무관
   - **동일 RuleSet 내** `sourceSlice`(미래) 또는 `dependsOn`(명시) 추가 시 의존성 그래프 구축

2. **SliceExecutionPlanner** 도메인 서비스 추가
   ```kotlin
   object SliceExecutionPlanner {
       /**
        * RuleSet의 slices를 의존성 순서로 정렬.
        * cycles 감지 시 Err 반환 (fail-closed).
        */
       fun plan(ruleSet: RuleSetContract): Result<List<SliceDefinition>>
   }
   ```

3. **의존성 그래프 구축**
   - `SliceDefinition`에 `dependsOn: List<SliceType> = emptyList()` 추가 (선택)
   - 또는 `joins`에서 `sourceSlice`(동일 RuleSet 내 참조 시) 파싱
   - DAG 검증: Kahn's algorithm 또는 DFS cycle detection

4. **SlicingEngine 수정**
   - `slice()` / `slicePartial()` 진입 시 `SliceExecutionPlanner.plan(ruleSet)` 호출
   - 정렬된 리스트로 순회

### 2-2. Phase 2: SliceDefinition.dependsOn 명시 (계약 확장)

**RuleSet YAML 확장**:

```yaml
slices:
  - type: CORE
    buildRules: { type: PassThrough, fields: ["*"] }
    joins: []
    # dependsOn: []  # 생략 시 빈 배열

  - type: ENRICHED
    sliceKind: ENRICHMENT
    dependsOn: [CORE]  # 명시적 의존성
    joins:
      - name: brand
        type: LOOKUP
        sourceFieldPath: masterInfo.brand.code
        targetEntityType: BRAND
        targetSliceType: SUMMARY
        targetKeyPattern: "BRAND#{tenantId}#{value}"
```

**계약 스키마**: `SliceDefinition`에 `dependsOn?: SliceType[]` 추가.

### 2-3. Phase 3: ExecutionPlan 런타임 강제

기존 `DeployPlan`/`ExecutionStep` 모델 활용:

- `SliceExecutionPlanner.plan()` → `ExecutionPlan(slices: List<SliceDefinition>, steps: List<ExecutionStep>)`
- SlicingEngine이 `ExecutionPlan`을 받아 순서 강제 실행
- `slicePartial` 시: `impactedTypes` + 의존성 closure 계산 → 필요한 Slice만 TopoSort 후 실행

## 3. TopoSort 구현 (Kahn's Algorithm)

### 3-1. SliceExecutionPlanner (신규 파일)

**경로**: `pkg/slices/domain/SliceExecutionPlanner.kt`

```kotlin
package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceDefinition
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * RuleSet의 slices를 의존성 순서로 정렬.
 * RFC-018: Slice 실행 순서/의존성 런타임 강제
 *
 * - dependsOn 비어 있으면 기존 YAML 순서 유지
 * - cycles 감지 시 Err 반환 (fail-closed)
 */
object SliceExecutionPlanner {

    /**
     * 의존성 순서로 정렬된 SliceDefinition 리스트 반환.
     */
    fun plan(ruleSet: RuleSetContract): Result<List<SliceDefinition>> {
        val slices = ruleSet.slices
        val deps = buildDependencyMap(slices)
        return topoSort(slices, deps)
    }

    /**
     * SliceDefinition에서 의존성 추출.
     * - dependsOn 필드 (미래 확장) 또는 joins에서 암시적 의존성
     */
    private fun buildDependencyMap(slices: List<SliceDefinition>): Map<SliceType, Set<SliceType>> {
        val sliceTypes = slices.map { it.type }.toSet()
        return slices.associate { def ->
            def.type to buildDepsFor(def, sliceTypes)
        }
    }

    private fun buildDepsFor(def: SliceDefinition, validTypes: Set<SliceType>): Set<SliceType> {
        // Phase 2: dependsOn 명시 시 사용
        // val explicit = def.dependsOn?.toSet() ?: emptySet()
        val explicit = emptySet<SliceType>()  // Phase 1: 아직 미구현

        // joins에서 동일 RuleSet 내 sourceSlice 참조 시 (미래)
        val fromJoins = def.joins
            .mapNotNull { it.targetSliceType?.let { s -> SliceType.entries.find { it.name == s } } }
            .filter { it in validTypes }

        return (explicit + fromJoins).toSet()
    }

    /**
     * Kahn's algorithm: DAG 위상 정렬
     */
    private fun topoSort(
        slices: List<SliceDefinition>,
        dependencies: Map<SliceType, Set<SliceType>>,
    ): Result<List<SliceDefinition>> {
        val sliceByType = slices.associateBy { it.type }
        val inDegree = mutableMapOf<SliceType, Int>()

        slices.forEach { inDegree[it.type] = 0 }
        dependencies.forEach { (node, deps) ->
            deps.forEach { dep ->
                if (sliceByType.containsKey(dep)) {
                    inDegree[node] = (inDegree[node] ?: 0) + 1
                }
            }
        }

        val queue = ArrayDeque<SliceType>()
        inDegree.forEach { (t, d) -> if (d == 0) queue.add(t) }

        val order = mutableListOf<SliceDefinition>()
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            val def = sliceByType[t] ?: continue
            order.add(def)
            dependencies.forEach { (node, deps) ->
                if (t in deps) {
                    val newDeg = (inDegree[node] ?: 0) - 1
                    inDegree[node] = newDeg
                    if (newDeg == 0) queue.add(node)
                }
            }
        }

        return if (order.size < slices.size) {
            Result.Err(DomainError.InvariantViolation("Cycle detected in slice dependencies"))
        } else {
            Result.Ok(order)
        }
    }

    /**
     * slicePartial용: impactedTypes + 의존성 closure 계산
     */
    fun computeClosure(
        ruleSet: RuleSetContract,
        impactedTypes: Set<SliceType>,
    ): Set<SliceType> {
        val deps = buildDependencyMap(ruleSet.slices)
        var closure = impactedTypes.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            deps.forEach { (node, depsOf) ->
                if (node in closure) {
                    depsOf.forEach {
                        if (it !in closure) {
                            closure.add(it)
                            changed = true
                        }
                    }
                }
            }
        }
        return closure
    }
}
```

**주의**: 위 Kahn 구현은 `inDegree` 업데이트 로직이 표준과 다름. 표준 Kahn 구현:

```kotlin
private fun topoSort(
    slices: List<SliceDefinition>,
    dependencies: Map<SliceType, Set<SliceType>>,
): Result<List<SliceDefinition>> {
    val sliceByType = slices.associateBy { it.type }
    // 의존성 역방향: A가 B에 의존 → B -> [A]
    val reverseEdges = mutableMapOf<SliceType, MutableSet<SliceType>>()
    slices.forEach { reverseEdges[it.type] = mutableSetOf() }
    dependencies.forEach { (node, deps) ->
        deps.forEach { dep ->
            if (sliceByType.containsKey(dep)) {
                reverseEdges.getOrPut(dep) { mutableSetOf() }.add(node)
            }
        }
    }
    val inDegree = slices.associate { it.type to (dependencies[it.type]?.count { sliceByType.containsKey(it) } ?: 0) }.toMutableMap()
    val queue = ArrayDeque<SliceType>()
    inDegree.forEach { (t, d) -> if (d == 0) queue.add(t) }

    val order = mutableListOf<SliceDefinition>()
    while (queue.isNotEmpty()) {
        val t = queue.removeFirst()
        order.add(sliceByType[t]!!)
        reverseEdges[t]?.forEach { succ ->
            inDegree[succ] = inDegree[succ]!! - 1
            if (inDegree[succ] == 0) queue.add(succ)
        }
    }
    return if (order.size < slices.size) {
        Result.Err(DomainError.InvariantViolation("Cycle detected in slice dependencies"))
    } else {
        Result.Ok(order)
    }
}
```

## 4. SlicingEngine 연동

### 4-1. slice() 수정

```kotlin
// SlicingEngine.kt slice()
val orderedSlices = when (val planResult = SliceExecutionPlanner.plan(ruleSet)) {
    is Result.Ok -> planResult.value
    is Result.Err -> return Result.Err(planResult.error)
}
for (def in orderedSlices) {
    when (val sliceResult = buildSlice(rawData, def, ruleSet)) {
        is Result.Ok -> slices.add(sliceResult.value)
        is Result.Err -> return Result.Err(sliceResult.error)
    }
}
```

### 4-2. slicePartial() 수정 (의존성 Closure)

`impactedTypes`만 있으면 부족함. 의존 대상도 함께 실행해야 함.

```kotlin
// slicePartial 시
val closure = SliceExecutionPlanner.computeClosure(ruleSet, impactedTypes)
val orderedSlices = when (val planResult = SliceExecutionPlanner.plan(ruleSet)) {
    is Result.Ok -> planResult.value.filter { it.type in closure }
    is Result.Err -> return Result.Err(planResult.error)
}
for (def in orderedSlices) {
    // ...
}
```

## 5. 요약

| 단계 | 내용 |
|------|------|
| Phase 1 | SliceExecutionPlanner + TopoSort, dependsOn 없으면 기존 순서 유지 |
| Phase 2 | SliceDefinition.dependsOn 계약 확장 |
| Phase 3 | slicePartial 시 의존성 closure 계산, ExecutionPlan 강제 |

**현재 즉시 적용 가능**: Phase 1만으로도 `dependsOn`이 비어 있으면 기존 동작 유지, cycles 감지만 추가.
