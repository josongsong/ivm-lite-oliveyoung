package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import io.opentelemetry.api.trace.Tracer
import org.koin.dsl.module

/**
 * Workflow Module (RFC-IMPL-009)
 * 
 * Orchestration Workflow 바인딩.
 * 모든 외부 진입점은 Workflow를 통해서만 접근 (RFC-V4-010)
 */
val workflowModule = module {
    
    // Ingest Workflow (RFC-IMPL-003)
    // B-0: OutboxRepositoryPort 통합 (Transactional Outbox)
    // RFC-IMPL-009: OpenTelemetry tracing 지원
    single {
        IngestWorkflow(
            rawRepo = get(),
            outboxRepo = get(),
            tracer = get<Tracer>(),
        )
    }
    
    // JoinExecutor (RFC-IMPL-010 Phase D-4: Light JOIN)
    // fail-closed: required join 실패 시 에러
    // 결정성: 동일 소스 + 동일 타겟 → 동일 결과
    single {
        JoinExecutor(
            rawRepo = get(),
        )
    }
    
    // SlicingEngine (RFC-IMPL-010 Phase D-3, D-4, D-9)
    // Contract is Law: RuleSet 기반 슬라이싱 + JOIN + Index
    single {
        SlicingEngine(
            contractRegistry = get(),
            joinExecutor = get(),  // GAP-A 해결: JoinExecutor 주입
        )
    }

    // ChangeSetBuilder (RFC-IMPL-010 Phase D-7)
    single {
        ChangeSetBuilder()
    }

    // ImpactCalculator (RFC-IMPL-010 Phase D-7)
    single {
        ImpactCalculator()
    }

    // Slicing Workflow (RFC-IMPL-004, RFC-IMPL-010 D-3, D-8, D-9)
    // RFC-IMPL-009: OpenTelemetry tracing 지원
    single {
        SlicingWorkflow(
            rawRepo = get(),
            sliceRepo = get(),
            slicingEngine = get(),
            invertedIndexRepo = get(),
            changeSetBuilder = get(),
            impactCalculator = get(),
            contractRegistry = get(),
            tracer = get<Tracer>(),
        )
    }
    
    // QueryView Workflow (RFC-IMPL-005, RFC-IMPL-010 GAP-D: ViewDefinition 기반)
    // Contract is Law: ViewDefinition이 조회 정책의 SSOT
    // RFC-IMPL-009: OpenTelemetry tracing 지원
    single {
        QueryViewWorkflow(
            sliceRepo = get(),
            contractRegistry = get(),  // GAP-D 해결: ViewDefinitionContract 로드용
            tracer = get<Tracer>(),
        )
    }
}
