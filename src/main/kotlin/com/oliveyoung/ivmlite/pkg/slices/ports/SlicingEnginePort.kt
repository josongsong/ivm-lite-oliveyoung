package com.oliveyoung.ivmlite.pkg.slices.ports

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * RFC-IMPL-010: SlicingEngine Port
 *
 * RuleSet 기반 슬라이싱 엔진의 Port 인터페이스.
 * Orchestration에서 도메인 서비스 직접 참조를 제거하기 위한 추상화.
 *
 * - 결정성: 동일 RawData + RuleSet → 동일 Slices
 * - 멱등성: 재실행해도 동일 결과
 * - Contract is Law: RuleSet이 슬라이싱 규칙의 유일한 정의 소스
 */
interface SlicingEnginePort {

    /**
     * RawDataRecord를 RuleSet 기반으로 슬라이싱 + Inverted Index 생성
     *
     * @param rawData 원본 데이터
     * @param ruleSetRef RuleSet 계약 참조
     * @return SlicingResult (Slices + Inverted Indexes)
     */
    suspend fun slice(
        rawData: RawDataRecord,
        ruleSetRef: ContractRef,
    ): Result<SlicingResult>

    /**
     * 특정 SliceType만 부분 슬라이싱 (INCREMENTAL용)
     *
     * @param rawData 원본 데이터
     * @param ruleSetRef RuleSet 계약 참조
     * @param impactedTypes 영향받은 SliceType 집합
     * @return SlicingResult (영향받은 Slices + Inverted Indexes만 생성)
     */
    suspend fun slicePartial(
        rawData: RawDataRecord,
        ruleSetRef: ContractRef,
        impactedTypes: Set<SliceType>,
    ): Result<SlicingResult>

    /**
     * 슬라이싱 결과 (Slices + Inverted Indexes)
     */
    data class SlicingResult(
        val slices: List<SliceRecord>,
        val indexes: List<InvertedIndexEntry>,
    )

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()

        inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
            is Ok -> Ok(transform(value))
            is Err -> this
        }
    }
}
