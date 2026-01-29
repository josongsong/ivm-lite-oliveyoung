package com.oliveyoung.ivmlite.pkg.changeset.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetBuilderPort
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * v4 초기: JSON Pointer 수준의 간단 diff로 ChangeSet을 만든다.
 * - 정확한 RFC6901 전수 diff가 필요하면 v4.1+에서 별도 엔진으로 교체한다.
 *
 * 결정성(Determinism): 동일 입력 → 동일 ChangeSet (ID 포함)
 * - changeSetId는 입력값의 hash로 결정적 생성
 * - UUID.randomUUID() 사용 금지
 */
class ChangeSetBuilder(
    private val om: ObjectMapper = ObjectMapper(),
) : ChangeSetBuilderPort {

    override fun build(
        tenantId: TenantId,
        entityType: String,
        entityKey: EntityKey,
        fromVersion: Long,
        toVersion: Long,
        fromPayload: String?,
        toPayload: String?,
        impactedSliceTypes: Set<String>,
        impactMap: Map<String, ImpactDetail>,
    ): ChangeSet {
        // 결정적 ID 생성: 입력값의 hash 기반
        // 동일 입력 → 동일 changeSetId (멱등성, 결정성 보장)
        val idInput = "${tenantId.value}|${entityType}|${entityKey.value}|$fromVersion|$toVersion"
        val idHash = Hashing.sha256Hex(idInput).take(16)
        val id = "CS_${idHash}"
        val changeType = when {
            fromPayload == null && toPayload != null -> ChangeType.CREATE
            fromPayload != null && toPayload == null -> ChangeType.DELETE
            fromPayload == toPayload -> ChangeType.NO_CHANGE
            else -> ChangeType.UPDATE
        }

        val changed = if (changeType == ChangeType.UPDATE) {
            diffJsonPointers(fromPayload!!, toPayload!!)
        } else {
            emptyList()
        }

        val payloadHash = Hashing.sha256Hex(toPayload ?: "")

        return ChangeSet(
            changeSetId = id,
            tenantId = tenantId,
            entityType = entityType,
            entityKey = entityKey,
            fromVersion = fromVersion,
            toVersion = toVersion,
            changeType = changeType,
            changedPaths = changed,
            impactedSliceTypes = impactedSliceTypes,
            impactMap = impactMap,
            payloadHash = "sha256:$payloadHash",
        )
    }

    private fun diffJsonPointers(fromJson: String, toJson: String): List<ChangedPath> {
        val a = om.readTree(fromJson)
        val b = om.readTree(toJson)
        val out = mutableListOf<ChangedPath>()
        walkDiff("", a, b, out)
        return out.sortedBy { it.path }
    }

    private fun walkDiff(path: String, a: JsonNode?, b: JsonNode?, out: MutableList<ChangedPath>) {
        if (a == null && b == null) return
        if (a == null || b == null) {
            val h = Hashing.sha256Hex(b?.toString() ?: "null")
            out += ChangedPath(path.ifEmpty { "/" }, "sha256:$h")
            return
        }
        if (a.nodeType != b.nodeType) {
            val h = Hashing.sha256Hex(b.toString())
            out += ChangedPath(path.ifEmpty { "/" }, "sha256:$h")
            return
        }
        when {
            a.isObject -> {
                val names = (a.fieldNames().asSequence().toSet() + b.fieldNames().asSequence().toSet()).toList().sorted()
                for (n in names) {
                    val p = "$path/${escapePointer(n)}"
                    walkDiff(p, a.get(n), b.get(n), out)
                }
            }
            a.isArray -> {
                val max = maxOf(a.size(), b.size())
                for (i in 0 until max) {
                    val p = "$path/$i"
                    walkDiff(p, a.get(i), b.get(i), out)
                }
            }
            else -> {
                if (a.asText() != b.asText() || a.toString() != b.toString()) {
                    val h = Hashing.sha256Hex(b.toString())
                    out += ChangedPath(path.ifEmpty { "/" }, "sha256:$h")
                }
            }
        }
    }

    private fun escapePointer(s: String): String = s.replace("~", "~0").replace("/", "~1")
}
