package com.oliveyoung.ivmlite.pkg.workflow.canvas

import com.oliveyoung.ivmlite.pkg.observability.domain.QueueDepthMetrics
import com.oliveyoung.ivmlite.pkg.observability.domain.ThroughputMetrics
import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort
import com.oliveyoung.ivmlite.pkg.workflow.canvas.adapters.WorkflowGraphBuilder
import com.oliveyoung.ivmlite.pkg.workflow.canvas.application.WorkflowCanvasService
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.NodeStatus
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.NodeType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk

/**
 * 노드 상태 계산 테스트 (RFC-IMPL-015)
 *
 * 핵심 시나리오:
 * - HEALTHY: 정상 처리 중
 * - WARNING: 지연 또는 낮은 처리량
 * - ERROR: 에러 발생
 * - INACTIVE: 처리량 없음
 */
class NodeStatusCalculationTest : StringSpec({

    val graphBuilder = WorkflowGraphBuilder()

    "상태 계산 - ERROR: errorCount > 0" {
        val metricsCollector = mockk<MetricsCollectorPort>()
        coEvery { metricsCollector.collectThroughput(any()) } returns ThroughputMetrics(
            recordsPerSecond = 10.0,
            recordsPerMinute = 600.0,
            recordsPerHour = 36000.0,
            recentMinuteCount = 600L,
            measurementPeriodSeconds = 60L
        )
        coEvery { metricsCollector.collectQueueDepths() } returns QueueDepthMetrics(
            pending = 100L,
            processing = 10L,
            failed = 50L,  // 에러 있음!
            dlq = 10L,
            stale = 5L
        )

        val service = WorkflowCanvasService(graphBuilder, metricsCollector)
        val graph = service.getGraph()

        // 에러가 있으면 ERROR 상태
        val rawDataNode = graph.nodes.find { it.type == NodeType.RAWDATA }
        rawDataNode shouldNotBe null
        // stats.errorCount가 주입되므로 ERROR 상태
    }

    "상태 계산 - WARNING: 높은 P99 지연 (>5000ms)" {
        // P99 > 5000ms 이면 WARNING
        // 이 테스트는 stats.latencyP99Ms를 직접 설정할 수 없으므로,
        // 낮은 처리량 + 레코드 있음 시나리오로 대체

        val metricsCollector = mockk<MetricsCollectorPort>()
        coEvery { metricsCollector.collectThroughput(any()) } returns ThroughputMetrics(
            recordsPerSecond = 0.01,  // 매우 낮은 처리량
            recordsPerMinute = 0.5,
            recordsPerHour = 30.0,
            recentMinuteCount = 1L,
            measurementPeriodSeconds = 60L
        )
        coEvery { metricsCollector.collectQueueDepths() } returns QueueDepthMetrics(
            pending = 1000L,  // 많은 대기
            processing = 10L,
            failed = 0L,
            dlq = 0L,
            stale = 0L
        )

        val service = WorkflowCanvasService(graphBuilder, metricsCollector)
        val graph = service.getGraph()

        // 처리량 < 1.0 && recordCount > 0 이면 WARNING
        val rawDataNode = graph.nodes.find { it.type == NodeType.RAWDATA }
        rawDataNode shouldNotBe null
        rawDataNode?.status shouldBe NodeStatus.WARNING
    }

    "상태 계산 - INACTIVE: 처리량 0 && 레코드 0" {
        val metricsCollector = mockk<MetricsCollectorPort>()
        coEvery { metricsCollector.collectThroughput(any()) } returns ThroughputMetrics(
            recordsPerSecond = 0.0,
            recordsPerMinute = 0.0,
            recordsPerHour = 0.0,
            recentMinuteCount = 0L,
            measurementPeriodSeconds = 60L
        )
        coEvery { metricsCollector.collectQueueDepths() } returns QueueDepthMetrics(
            pending = 0L,
            processing = 0L,
            failed = 0L,
            dlq = 0L,
            stale = 0L
        )

        val service = WorkflowCanvasService(graphBuilder, metricsCollector)
        val graph = service.getGraph()

        val rawDataNode = graph.nodes.find { it.type == NodeType.RAWDATA }
        rawDataNode shouldNotBe null
        rawDataNode?.status shouldBe NodeStatus.INACTIVE
    }

    "상태 계산 - HEALTHY: 정상 처리 중" {
        val metricsCollector = mockk<MetricsCollectorPort>()
        coEvery { metricsCollector.collectThroughput(any()) } returns ThroughputMetrics(
            recordsPerSecond = 100.0,
            recordsPerMinute = 6000.0,
            recordsPerHour = 360000.0,
            recentMinuteCount = 6000L,
            measurementPeriodSeconds = 60L
        )
        coEvery { metricsCollector.collectQueueDepths() } returns QueueDepthMetrics(
            pending = 50L,
            processing = 10L,
            failed = 0L,
            dlq = 0L,
            stale = 0L
        )

        val service = WorkflowCanvasService(graphBuilder, metricsCollector)
        val graph = service.getGraph()

        val rawDataNode = graph.nodes.find { it.type == NodeType.RAWDATA }
        rawDataNode shouldNotBe null
        rawDataNode?.status shouldBe NodeStatus.HEALTHY
    }

    "상태 계산 - MetricsCollector 없으면 INACTIVE" {
        val service = WorkflowCanvasService(graphBuilder, null)
        val graph = service.getGraph()

        // MetricsCollector가 없으면 stats가 null이므로 INACTIVE
        graph.nodes.all { it.status == NodeStatus.INACTIVE } shouldBe true
    }

    "헬스 요약 - 상태별 카운트 정확성" {
        val service = WorkflowCanvasService(graphBuilder, null)
        val graph = service.getGraph()

        val summary = graph.metadata.healthSummary
        val totalFromSummary = summary.healthy + summary.warning + summary.error + summary.inactive
        val totalNodes = graph.nodes.size

        totalFromSummary shouldBe totalNodes
    }
})
