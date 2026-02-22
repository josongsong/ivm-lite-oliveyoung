package com.oliveyoung.ivmlite.pkg.views.ports

import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.views.domain.ViewRecord
import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * ViewComposerPort - Slice 조합 인터페이스
 *
 * RFC-003: ViewDefinition 기반 Slice → View 변환
 * - requiredSlices: 필수 (없으면 에러)
 * - optionalSlices: 선택적 (없어도 OK)
 * - missingPolicy: FAIL_CLOSED (기본값)
 */
interface ViewComposerPort {

    /**
     * Slice 조합 → View 생성
     *
     * @param slices 입력 Slice 목록
     * @param viewDefId ViewDefinition ID
     * @param viewDefVersion ViewDefinition 버전 (Contract SSOT)
     * @return 생성된 View 목록
     */
    suspend fun compose(
        slices: List<SliceRecord>,
        viewDefId: String,
        viewDefVersion: String
    ): Result<List<ViewRecord>>

    /**
     * 특정 ViewType만 생성
     */
    suspend fun composeOne(
        slices: List<SliceRecord>,
        viewDefId: String,
        viewType: String,
        viewDefVersion: String = "1.0.0"
    ): Result<ViewRecord>
}
