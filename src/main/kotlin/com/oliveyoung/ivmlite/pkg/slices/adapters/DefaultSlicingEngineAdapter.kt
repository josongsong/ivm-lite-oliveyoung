package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * RFC-IMPL-010: SlicingEngine Adapter
 *
 * SlicingEnginePort 구현체.
 * 도메인 서비스 SlicingEngine을 래핑하여 Port 인터페이스 제공.
 * SlicingEngine.Result → SlicingEnginePort.Result 변환 수행.
 */
class DefaultSlicingEngineAdapter(
    private val delegate: SlicingEngine,
) : SlicingEnginePort {

    override suspend fun slice(
        rawData: RawDataRecord,
        ruleSetRef: ContractRef,
    ): SlicingEnginePort.Result<SlicingEnginePort.SlicingResult> {
        return when (val r = delegate.slice(rawData, ruleSetRef)) {
            is SlicingEngine.Result.Ok -> SlicingEnginePort.Result.Ok(
                SlicingEnginePort.SlicingResult(
                    slices = r.value.slices,
                    indexes = r.value.indexes,
                )
            )
            is SlicingEngine.Result.Err -> SlicingEnginePort.Result.Err(r.error)
        }
    }

    override suspend fun slicePartial(
        rawData: RawDataRecord,
        ruleSetRef: ContractRef,
        impactedTypes: Set<SliceType>,
    ): SlicingEnginePort.Result<SlicingEnginePort.SlicingResult> {
        return when (val r = delegate.slicePartial(rawData, ruleSetRef, impactedTypes)) {
            is SlicingEngine.Result.Ok -> SlicingEnginePort.Result.Ok(
                SlicingEnginePort.SlicingResult(
                    slices = r.value.slices,
                    indexes = r.value.indexes,
                )
            )
            is SlicingEngine.Result.Err -> SlicingEnginePort.Result.Err(r.error)
        }
    }
}
