package com.oliveyoung.ivmlite.pkg.workflow.canvas

import com.oliveyoung.ivmlite.pkg.observability.domain.*
import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort
import com.oliveyoung.ivmlite.pkg.workflow.canvas.adapters.WorkflowGraphBuilder
import com.oliveyoung.ivmlite.pkg.workflow.canvas.application.WorkflowCanvasService
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.NodeStatus
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.NodeType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Duration
import java.time.Instant

/**
 * WorkflowCanvasService 단위 테스트 (RFC-IMPL-015)
 */
class WorkflowCanvasServiceTest : StringSpec({

    val graphBuilder = WorkflowGraphBuilder()

    "그래프 조회 - MetricsCollector 없이" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        val graph = service.getGraph()

        graph.nodes.shouldNotBeEmpty()
        graph.edges.shouldNotBeEmpty()
        graph.metadata.entityTypes.shouldNotBeEmpty()
    }

    "그래프 조회 - MetricsCollector와 함께" {
        val metricsCollector = mockk<MetricsCollectorPort>()
        coEvery { metricsCollector.collectThroughput(any()) } returns ThroughputMetrics(
            recordsPerSecond = 1.67,
            recordsPerMinute = 100.0,
            recordsPerHour = 6000.0,
            recentMinuteCount = 100L,
            measurementPeriodSeconds = 60L
        )
        coEvery { metricsCollector.collectQueueDepths() } returns QueueDepthMetrics(
            pending = 100L,
            processing = 10L,
            failed = 5L,
            dlq = 0L,
            stale = 0L
        )

        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = metricsCollector
        )

        val graph = service.getGraph()

        graph.nodes.shouldNotBeEmpty()

        // RawData 노드에 통계가 주입되었는지 확인
        val rawDataNode = graph.nodes.find { it.type == NodeType.RAWDATA }
        rawDataNode shouldNotBe null
        rawDataNode?.stats shouldNotBe null
        rawDataNode?.stats?.throughput shouldBe 100.0
    }

    "엔티티 필터 적용" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        val productGraph = service.getGraph("PRODUCT")

        // PRODUCT 엔티티만 포함
        productGraph.nodes.filter { it.entityType != null }
            .all { it.entityType == "PRODUCT" } shouldBe true
    }

    "노드 상세 정보 조회 - 존재하는 노드" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        val result = service.getNodeDetail("rawdata_PRODUCT")

        result.shouldBeInstanceOf<WorkflowCanvasService.Result.Ok<*>>()
        val detail = (result as WorkflowCanvasService.Result.Ok).value

        detail.node.id shouldBe "rawdata_PRODUCT"
        detail.node.type shouldBe NodeType.RAWDATA
        detail.upstreamNodes shouldBe emptyList()  // RawData는 upstream 없음
        detail.downstreamNodes.shouldNotBeEmpty()  // RuleSet로 연결
    }

    "노드 상세 정보 조회 - 존재하지 않는 노드" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        val result = service.getNodeDetail("nonexistent_node")

        result.shouldBeInstanceOf<WorkflowCanvasService.Result.Err>()
    }

    "워크플로우 통계 조회" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        val stats = service.getStats()

        stats.entityTypes.shouldNotBeEmpty()
        stats.totalNodes shouldBe stats.nodesByType.values.sum()
        stats.nodesByType[NodeType.RAWDATA] shouldNotBe null
        stats.nodesByType[NodeType.SLICE] shouldNotBe null
    }

    "노드 상태 계산 - HEALTHY" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        // 기본 상태로 빌드된 노드는 모두 HEALTHY 또는 INACTIVE
        val graph = service.getGraph()
        graph.nodes.all { it.status in listOf(NodeStatus.HEALTHY, NodeStatus.INACTIVE) } shouldBe true
    }

    "MetricsCollector 에러 시에도 그래프 반환" {
        val failingCollector = mockk<MetricsCollectorPort>()
        coEvery { failingCollector.collectThroughput(any()) } throws RuntimeException("Connection failed")
        coEvery { failingCollector.collectQueueDepths() } throws RuntimeException("Connection failed")

        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = failingCollector
        )

        // 에러가 발생해도 그래프는 반환됨
        val graph = service.getGraph()
        graph.nodes.shouldNotBeEmpty()
    }

    "관련 Contract 조회" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        val result = service.getNodeDetail("rawdata_PRODUCT")
        val detail = (result as WorkflowCanvasService.Result.Ok).value

        // RawData 노드는 Contract ID가 있음
        (detail.relatedContracts.isNotEmpty() || detail.node.contractId != null) shouldBe true
    }

    "그래프 메타데이터 - lastUpdatedAt 포함" {
        val service = WorkflowCanvasService(
            graphBuilder = graphBuilder,
            metricsCollector = null
        )

        val graph = service.getGraph()

        graph.metadata.lastUpdatedAt shouldNotBe null
        graph.metadata.lastUpdatedAt.isNotEmpty() shouldBe true
    }
})
