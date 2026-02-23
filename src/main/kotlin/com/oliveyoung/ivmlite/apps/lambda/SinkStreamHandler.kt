package com.oliveyoung.ivmlite.apps.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.oliveyoung.ivmlite.apps.lambda.wiring.lambdaTracingModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.sinkPluginModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.sinkPluginRegistryModule
import com.oliveyoung.ivmlite.shared.adapters.withSpan
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.time.Instant

/**
 * SinkStreamHandler - DynamoDB Streams 기반 Sink 처리 Lambda (RFC-020)
 *
 * DynamoDB Streams → Lambda → SinkPlugin 직접 실행
 *
 * 코어 로직은 SinkStreamProcessor에 위임.
 * 이 클래스는 Lambda 인프라(Koin DI, DynamoDB SDK, 이벤트 파싱)만 담당.
 * RFC-IMPL-009: OpenTelemetry 트레이싱 적용
 */
class SinkStreamHandler : RequestHandler<DynamodbEvent, String> {

    private val lambdaInfraModule = module {
        single<DynamoDbAsyncClient> {
            DynamoDbAsyncClient.builder()
                .build()
        }
    }

    init {
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(
                    lambdaInfraModule,
                    lambdaTracingModule,
                    sinkPluginRegistryModule,
                    sinkPluginModule,
                )
            }
        }
    }

    override fun handleRequest(event: DynamodbEvent, context: Context): String {
        val koin = GlobalContext.get()
        val tracer = koin.get<Tracer>()

        return tracer.withSpan(
            name = "SinkStreamHandler.processBatch",
            attributes = mapOf(
                "aws.lambda.function" to (context.functionName ?: "unknown"),
                "sink.batch.size" to event.records.size.toString(),
            ),
        ) { span ->
            val batchStartMs = System.currentTimeMillis()
            val dynamoClient = koin.get<DynamoDbAsyncClient>()
            val tableName = System.getenv("SINK_EVENT_TABLE") ?: "ivm-sink-events"

            val statusUpdater = SinkEventStatusUpdater { sinkEventId, sk, newStatus ->
                val request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(mapOf(
                        "PK" to AttributeValue.builder().s("SINK_EVENT#$sinkEventId").build(),
                        "SK" to AttributeValue.builder().s(sk).build(),
                    ))
                    .updateExpression("SET #status = :status, processedAt = :now, GSI2_PK = :gsi2pk")
                    .expressionAttributeNames(mapOf("#status" to "status"))
                    .expressionAttributeValues(mapOf(
                        ":status" to AttributeValue.builder().s(newStatus).build(),
                        ":now" to AttributeValue.builder().n(Instant.now().toEpochMilli().toString()).build(),
                        ":gsi2pk" to AttributeValue.builder().s("STATUS#$newStatus").build(),
                    ))
                    .build()

                try {
                    dynamoClient.updateItem(request).await()
                    context.logger.log("SinkEvent status updated: $sinkEventId → $newStatus")
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    context.logger.log("Failed to update SinkEvent status: $sinkEventId → ${e.message}")
                }
            }

            val processor = SinkStreamProcessor(
                pluginRegistry = koin.get(),
                sinkLedger = koin.get(),
                failureRepository = koin.get(),
                statusUpdater = statusUpdater,
            )

            val records = event.records.map { record -> convertRecord(record) }
            val result = runBlocking { processor.processBatch(records) }

            val batchElapsedMs = System.currentTimeMillis() - batchStartMs
            span.setAttribute("sink.processed", result.processed.toLong())
            span.setAttribute("sink.deleted", result.deleted.toLong())
            span.setAttribute("sink.errors", result.errors.toLong())
            span.setAttribute("sink.duration_ms", batchElapsedMs.toLong())

            val summary = "TIMING batch=${batchElapsedMs}ms records=${event.records.size} " +
                "processed=${result.processed} deleted=${result.deleted} errors=${result.errors}"
            context.logger.log(summary)
            summary
        }
    }

    private fun convertRecord(record: DynamodbEvent.DynamodbStreamRecord): StreamRecord {
        return StreamRecord(
            eventName = record.eventName ?: "",
            newImage = record.dynamodb?.newImage?.let { convertImage(it) },
            oldImage = record.dynamodb?.oldImage?.let { convertOldImage(it) },
        )
    }

    private fun convertImage(
        item: Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue>,
    ): SinkEventImage {
        return SinkEventImage(
            id = item["id"]?.s ?: "",
            sk = item["SK"]?.s ?: "",
            tenantId = item["tenantId"]?.s ?: "",
            entityKey = item["entityKey"]?.s ?: "",
            version = item["version"]?.n?.toLongOrNull() ?: 0L,
            viewType = item["viewType"]?.s ?: "",
            payload = item["payload"]?.s ?: "{}",
            targets = item["sinkTargets"]?.ss ?: emptyList(),
            status = item["status"]?.s ?: "",
            jobId = item["jobId"]?.s,
        )
    }

    private fun convertOldImage(
        item: Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue>,
    ): SinkEventImage {
        return SinkEventImage(
            id = item["id"]?.s ?: "",
            sk = item["SK"]?.s ?: "",
            tenantId = item["tenantId"]?.s ?: "",
            entityKey = item["entityKey"]?.s ?: "",
            version = item["version"]?.n?.toLongOrNull() ?: 0L,
            viewType = item["viewType"]?.s ?: "",
            payload = item["payload"]?.s ?: "{}",
            targets = item["sinkTargets"]?.ss ?: emptyList(),
            status = item["status"]?.s ?: "",
            jobId = item["jobId"]?.s,
        )
    }
}
