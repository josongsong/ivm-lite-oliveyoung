package com.oliveyoung.ivmlite.apps.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse
import com.oliveyoung.ivmlite.apps.lambda.wiring.lambdaTracingModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.sinkPluginModule
import com.oliveyoung.ivmlite.shared.adapters.withSpan
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

/**
 * SinkBatchHandler - SQS 기반 Sink 벌크 처리 Lambda
 *
 * SQS Batch Window(batchSize=500, batchWindow=60초)와 함께 사용.
 * 메시지가 SQS에 모였다가 500건 또는 60초 경과 시 Lambda 호출 → executeBatch로 OpenSearch bulk.
 *
 * 이벤트 소스 매핑 설정:
 * - batchSize: 500
 * - maximumBatchingWindowInSeconds: 60
 *
 * 환경변수: OPENSEARCH_ENDPOINT 등 (sinkPluginModule)
 */
class SinkBatchHandler : RequestHandler<SQSEvent, SQSBatchResponse> {

    private val lambdaInfraModule = module {
        single<DynamoDbAsyncClient> {
            DynamoDbAsyncClient.builder().build()
        }
    }

    init {
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(
                    lambdaInfraModule,
                    lambdaTracingModule,
                    sinkPluginModule,
                )
            }
        }
    }

    override fun handleRequest(event: SQSEvent, context: Context): SQSBatchResponse {
        val koin = GlobalContext.get()
        val tracer = koin.get<Tracer>()

        val messages = event.records.map { SqsSinkMessage(it.messageId, it.body) }

        val result = tracer.withSpan(
            name = "SinkBatchHandler.processBatch",
            attributes = mapOf(
                "aws.lambda.function" to (context.functionName ?: "unknown"),
                "sink.batch.size" to messages.size.toString(),
            ),
        ) { span ->
            val batchStartMs = System.currentTimeMillis()

            val processor = SinkBatchProcessor(
                pluginRegistry = koin.get(),
                sinkLedger = koin.get(),
                failureRepository = koin.get(),
            )

            val processResult = runBlocking { processor.processBatch(messages) }

            val batchElapsedMs = System.currentTimeMillis() - batchStartMs
            span.setAttribute("sink.succeeded", processResult.succeeded.toLong())
            span.setAttribute("sink.failed", processResult.failed.toLong())
            span.setAttribute("sink.parse_errors", processResult.parseErrors.toLong())
            span.setAttribute("sink.duration_ms", batchElapsedMs.toLong())

            val summary = "TIMING batch=${batchElapsedMs}ms messages=${messages.size} " +
                "succeeded=${processResult.succeeded} failed=${processResult.failed} parseErrors=${processResult.parseErrors}"
            context.logger.log(summary)

            processResult
        }

        // Batch Item Failure: 재시도할 메시지 지정 (실패 시 해당 messageId 반환)
        val failures = mutableListOf<SQSBatchResponse.BatchItemFailure>()
        if (result.failed > 0) {
            // 전체 배치 실패 시 모든 메시지 재시도 (단순화)
            event.records.forEach { failures.add(SQSBatchResponse.BatchItemFailure(it.messageId)) }
        }

        return SQSBatchResponse(failures)
    }
}
