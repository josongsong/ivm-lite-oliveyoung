package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

/**
 * GitOutputService (Phase 6: Git-Friendly Output)
 *
 * Contract 변경 내용을 Git 친화적 형식으로 출력.
 * - Unified Patch 생성
 * - PR 템플릿 자동 생성
 */
class GitOutputService(
    private val semanticDiffService: SemanticDiffService
) {
    /**
     * Unified Diff 형식으로 패치 생성
     */
    fun exportPatch(
        beforeYaml: String,
        afterYaml: String,
        filePath: String
    ): Either<DomainError, PatchOutput> = either {
        val beforeLines = beforeYaml.lines()
        val afterLines = afterYaml.lines()

        val hunks = computeUnifiedDiff(beforeLines, afterLines)

        val patchContent = buildString {
            appendLine("--- a/$filePath")
            appendLine("+++ b/$filePath")
            hunks.forEach { hunk ->
                appendLine(hunk.header)
                hunk.lines.forEach { line ->
                    appendLine(line)
                }
            }
        }

        PatchOutput(
            filePath = filePath,
            patch = patchContent,
            additions = hunks.sumOf { h -> h.lines.count { it.startsWith("+") } },
            deletions = hunks.sumOf { h -> h.lines.count { it.startsWith("-") } }
        )
    }

    /**
     * PR 설명 템플릿 자동 생성
     */
    fun generatePRDescription(
        beforeYaml: String,
        afterYaml: String,
        contractId: String,
        contractKind: String
    ): Either<DomainError, PRDescription> = either {
        val diff = semanticDiffService.computeDiff(beforeYaml, afterYaml).bind()

        val breakingChanges = diff.changes.filter { it.breaking }
        val nonBreakingChanges = diff.changes.filter { !it.breaking }

        val title = when {
            breakingChanges.isNotEmpty() -> "[BREAKING] Update $contractKind: $contractId"
            nonBreakingChanges.size == 1 -> "${nonBreakingChanges[0].type.toActionVerb()} ${nonBreakingChanges[0].target} in $contractId"
            else -> "Update $contractKind: $contractId"
        }

        val body = buildString {
            appendLine("## Summary")
            appendLine()
            appendLine("Updates `$contractKind` contract `$contractId`.")
            appendLine()

            if (breakingChanges.isNotEmpty()) {
                appendLine("### Breaking Changes")
                appendLine()
                breakingChanges.forEach { change ->
                    appendLine("- **${change.type.toDisplayName()}**: `${change.target}`")
                    if (change.before != null && change.after != null) {
                        appendLine("  - Before: `${change.before}`")
                        appendLine("  - After: `${change.after}`")
                    }
                }
                appendLine()
            }

            if (nonBreakingChanges.isNotEmpty()) {
                appendLine("### Changes")
                appendLine()
                nonBreakingChanges.forEach { change ->
                    appendLine("- ${change.type.toDisplayName()}: `${change.target}`")
                }
                appendLine()
            }

            appendLine("### Impact")
            appendLine()
            if (diff.affectedSlices.isNotEmpty()) {
                appendLine("**Affected Slices**: ${diff.affectedSlices.joinToString(", ")}")
            }
            if (diff.affectedViews.isNotEmpty()) {
                appendLine("**Affected Views**: ${diff.affectedViews.joinToString(", ")}")
            }
            appendLine("**Re-ingest Required**: ${if (diff.regenRequired) "Yes" else "No"}")
            appendLine()

            appendLine("### Checklist")
            appendLine()
            appendLine("- [ ] Reviewed breaking changes impact")
            if (diff.regenRequired) {
                appendLine("- [ ] Scheduled data re-ingestion")
            }
            appendLine("- [ ] Tested with sample data")
            appendLine("- [ ] Updated documentation (if needed)")
        }

        PRDescription(
            title = title,
            body = body,
            labels = buildList {
                add("contract")
                add(contractKind.lowercase())
                if (breakingChanges.isNotEmpty()) add("breaking-change")
                if (diff.regenRequired) add("requires-reingest")
            }
        )
    }

    // ==================== Private Helpers ====================

    private fun computeUnifiedDiff(before: List<String>, after: List<String>): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()

        // 간단한 LCS 기반 diff 알고리즘
        val lcs = computeLCS(before, after)

        var beforeIdx = 0
        var afterIdx = 0
        var lcsIdx = 0

        val currentHunkLines = mutableListOf<String>()
        var hunkStartBefore = 1
        var hunkStartAfter = 1
        var hunkCountBefore = 0
        var hunkCountAfter = 0

        while (beforeIdx < before.size || afterIdx < after.size) {
            val beforeLine = before.getOrNull(beforeIdx)
            val afterLine = after.getOrNull(afterIdx)
            val lcsLine = lcs.getOrNull(lcsIdx)

            when {
                beforeLine == lcsLine && afterLine == lcsLine -> {
                    // Context line
                    if (currentHunkLines.isNotEmpty()) {
                        currentHunkLines.add(" $beforeLine")
                        hunkCountBefore++
                        hunkCountAfter++
                    }
                    beforeIdx++
                    afterIdx++
                    lcsIdx++
                }
                beforeLine != lcsLine && afterLine == lcsLine -> {
                    // Deletion
                    if (currentHunkLines.isEmpty()) {
                        hunkStartBefore = beforeIdx + 1
                        hunkStartAfter = afterIdx + 1
                    }
                    currentHunkLines.add("-$beforeLine")
                    hunkCountBefore++
                    beforeIdx++
                }
                beforeLine == lcsLine && afterLine != lcsLine -> {
                    // Addition
                    if (currentHunkLines.isEmpty()) {
                        hunkStartBefore = beforeIdx + 1
                        hunkStartAfter = afterIdx + 1
                    }
                    currentHunkLines.add("+$afterLine")
                    hunkCountAfter++
                    afterIdx++
                }
                else -> {
                    // Both different from LCS
                    if (currentHunkLines.isEmpty()) {
                        hunkStartBefore = beforeIdx + 1
                        hunkStartAfter = afterIdx + 1
                    }
                    if (beforeLine != null) {
                        currentHunkLines.add("-$beforeLine")
                        hunkCountBefore++
                        beforeIdx++
                    }
                    if (afterLine != null) {
                        currentHunkLines.add("+$afterLine")
                        hunkCountAfter++
                        afterIdx++
                    }
                }
            }

            // 충분한 컨텍스트가 모이면 hunk 생성
            if (currentHunkLines.size > 20 || (beforeIdx >= before.size && afterIdx >= after.size)) {
                if (currentHunkLines.any { it.startsWith("+") || it.startsWith("-") }) {
                    hunks.add(
                        DiffHunk(
                            header = "@@ -$hunkStartBefore,$hunkCountBefore +$hunkStartAfter,$hunkCountAfter @@",
                            lines = currentHunkLines.toList()
                        )
                    )
                }
                currentHunkLines.clear()
                hunkCountBefore = 0
                hunkCountAfter = 0
            }
        }

        return hunks
    }

    private fun computeLCS(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to find LCS
        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    lcs.add(0, a[i - 1])
                    i--
                    j--
                }
                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }

        return lcs
    }

    private fun ChangeType.toDisplayName(): String = when (this) {
        ChangeType.FIELD_ADDED -> "Field added"
        ChangeType.FIELD_REMOVED -> "Field removed"
        ChangeType.FIELD_TYPE_CHANGED -> "Field type changed"
        ChangeType.FIELD_REQUIRED_CHANGED -> "Field required changed"
        ChangeType.SLICE_ADDED -> "Slice added"
        ChangeType.SLICE_REMOVED -> "Slice removed"
        ChangeType.RULE_CHANGED -> "Rule changed"
        ChangeType.IMPACT_MAP_CHANGED -> "Impact map changed"
        ChangeType.PROPERTY_ADDED -> "Property added"
        ChangeType.PROPERTY_REMOVED -> "Property removed"
        ChangeType.PROPERTY_CHANGED -> "Property changed"
        ChangeType.SLICE_REF_ADDED -> "Slice reference added"
        ChangeType.SLICE_REF_REMOVED -> "Slice reference removed"
        ChangeType.SLICE_REF_PROMOTED -> "Slice reference promoted"
        ChangeType.SLICE_REF_DEMOTED -> "Slice reference demoted"
    }

    private fun ChangeType.toActionVerb(): String = when (this) {
        ChangeType.FIELD_ADDED -> "Add field"
        ChangeType.FIELD_REMOVED -> "Remove field"
        ChangeType.FIELD_TYPE_CHANGED -> "Change field type for"
        ChangeType.SLICE_ADDED -> "Add slice"
        ChangeType.SLICE_REMOVED -> "Remove slice"
        else -> "Update"
    }
}

// ==================== DTOs ====================

data class PatchOutput(
    val filePath: String,
    val patch: String,
    val additions: Int,
    val deletions: Int
)

data class PRDescription(
    val title: String,
    val body: String,
    val labels: List<String>
)

data class DiffHunk(
    val header: String,
    val lines: List<String>
)
