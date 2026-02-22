package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceDefinition
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceKind
import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * RFC-018: Slice 실행 순서/의존성 런타임 강제
 *
 * - 의존성 자동 추론: SliceKind.ENRICHMENT → CORE 의존 (동일 RuleSet 내)
 * - TopoSort로 DAG 검증 및 실행 순서 산출
 * - cycles 감지 시 구체적 에러 메시지 (fail-closed)
 */
object SliceExecutionPlanner {

    /**
     * 의존성 순서로 정렬된 SliceDefinition 리스트 + 설명 가능한 ExecutionPlan 반환.
     */
    fun plan(ruleSet: RuleSetContract): Result<SliceExecutionPlan> {
        val slices = ruleSet.slices
        val (deps, reasons) = buildDependencyMapWithReasons(slices)
        return topoSort(slices, deps, reasons)
    }

    /**
     * 의존성 추출 (자동 추론 + 이유)
     * - ENRICHMENT 슬라이스 → CORE 의존 (동일 RuleSet에 CORE가 있으면)
     * - joins에서 동일 entityType targetSliceType 참조 시 (미래 확장)
     */
    private fun buildDependencyMapWithReasons(
        slices: List<SliceDefinition>,
    ): Pair<Map<SliceType, Set<SliceType>>, Map<SliceType, Map<SliceType, String>>> {
        val sliceTypes = slices.map { it.type }.toSet()
        val deps = mutableMapOf<SliceType, MutableSet<SliceType>>()
        val reasons = mutableMapOf<SliceType, MutableMap<SliceType, String>>()

        for (def in slices) {
            val defDeps = mutableSetOf<SliceType>()
            val defReasons = mutableMapOf<SliceType, String>()

            // 1. ENRICHMENT → CORE 자동 추론 (RFC-006: ENRICHED는 CORE 기반)
            if (def.sliceKind == SliceKind.ENRICHMENT && SliceType.CORE in sliceTypes) {
                defDeps.add(SliceType.CORE)
                defReasons[SliceType.CORE] = "ENRICHMENT slice requires CORE (joins use RawData from same entity)"
            }

            // 2. joins에서 동일 RuleSet 내 sourceSlice 참조 (미래: sourceSlice 필드 추가 시)
            for (join in def.joins) {
                val targetSlice = join.targetSliceType?.let { s ->
                    SliceType.entries.find { it.name.equals(s, ignoreCase = true) }
                }
                if (targetSlice != null && targetSlice in sliceTypes && targetSlice != def.type) {
                    defDeps.add(targetSlice)
                    defReasons[targetSlice] = "joins.${join.name} references slice $targetSlice"
                }
            }

            deps[def.type] = defDeps
            reasons[def.type] = defReasons
        }

        return deps.mapValues { it.value.toSet() } to reasons.mapValues { it.value.toMap() }
    }

    /**
     * Kahn's algorithm: DAG 위상 정렬
     */
    private fun topoSort(
        slices: List<SliceDefinition>,
        dependencies: Map<SliceType, Set<SliceType>>,
        reasons: Map<SliceType, Map<SliceType, String>>,
    ): Result<SliceExecutionPlan> {
        val sliceByType = slices.associateBy { it.type }
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
        val stepReasons = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            val def = sliceByType[t]!!
            order.add(def)
            val depList = dependencies[t]?.filter { it in sliceByType }?.joinToString(", ") { dep ->
                reasons[t]?.get(dep) ?: "depends on $dep"
            } ?: ""
            stepReasons.add(if (depList.isEmpty()) "root slice" else depList)

            reverseEdges[t]?.forEach { succ ->
                inDegree[succ] = inDegree[succ]!! - 1
                if (inDegree[succ] == 0) queue.add(succ)
            }
        }

        return if (order.size < slices.size) {
            val cycleMembers = slices.map { it.type }.toSet() - order.map { it.type }.toSet()
            Result.Err(
                DomainError.InvariantViolation(
                    "Slice dependency cycle detected. Involved slices: ${cycleMembers.joinToString(" → ")}. " +
                        "Fix: ensure ENRICHMENT slices come after CORE, or remove circular dependsOn."
                )
            )
        } else {
            val steps = order.mapIndexed { idx, def ->
                SliceExecutionStep(
                    stepNumber = idx + 1,
                    sliceRef = def.type.name,
                    dependencies = (dependencies[def.type] ?: emptySet()).map { it.name },
                    reason = stepReasons[idx],
                )
            }
            Result.Ok(SliceExecutionPlan(slices = order, steps = steps))
        }
    }

    /**
     * slicePartial용: impactedTypes + 의존성 closure (transitive) 계산
     */
    fun computeClosure(
        ruleSet: RuleSetContract,
        impactedTypes: Set<SliceType>,
    ): Set<SliceType> {
        val (deps, _) = buildDependencyMapWithReasons(ruleSet.slices)
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

/**
 * RFC-018: 설명 가능한 Slice 실행 계획
 */
data class SliceExecutionPlan(
    val slices: List<SliceDefinition>,
    val steps: List<SliceExecutionStep>,
) {
    /**
     * 병렬 실행용 Wave 분할.
     * Wave 0: 의존성 없는 Slice들 (동시 실행 가능)
     * Wave N: Wave 0..N-1 완료 후 실행 가능한 Slice들
     */
    fun toWaves(): List<List<SliceDefinition>> {
        val deps = steps.associate { it.sliceRef to it.dependencies.toSet() }
        val completed = mutableSetOf<String>()
        val remaining = slices.toMutableList()
        val waves = mutableListOf<List<SliceDefinition>>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { def ->
                (deps[def.type.name] ?: emptySet()).all { it in completed }
            }
            if (ready.isEmpty()) break
            waves.add(ready)
            ready.forEach {
                completed.add(it.type.name)
                remaining.remove(it)
            }
        }
        return waves
    }
}

/**
 * RFC-018: 실행 스텝 (reason으로 "왜 이 순서인가" 설명)
 */
data class SliceExecutionStep(
    val stepNumber: Int,
    val sliceRef: String,
    val dependencies: List<String>,
    val reason: String,
)
