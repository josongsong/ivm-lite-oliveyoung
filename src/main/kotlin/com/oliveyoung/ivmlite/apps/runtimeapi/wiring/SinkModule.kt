package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.sinks.adapters.DynamoDbSinkFailureRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.DynamoDbSinkLedger
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkPluginRegistry
import com.oliveyoung.ivmlite.pkg.sinks.adapters.OpenSearchSinkPlugin
import com.oliveyoung.ivmlite.pkg.sinks.adapters.PersonalizeSinkPlugin
import com.oliveyoung.ivmlite.pkg.sinks.adapters.S3SinkPlugin
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.pkg.views.ports.ViewComposerPort
import com.oliveyoung.ivmlite.sinks.contract.SinkLedger
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import org.koin.dsl.module
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.personalizeevents.PersonalizeEventsClient
import software.amazon.awssdk.services.s3.S3Client

/**
 * View Module - ViewComposer 바인딩 (Runtime API용)
 *
 * ViewComposer는 순수 Slice→View 변환만 담당 (Sink 발행 책임 없음).
 * Runtime API, Admin 등 모든 환경에서 사용.
 */
val viewModule = module {
    single<ViewComposerPort> { ViewComposer() }
}

/**
 * Sink Plugin Module - Lambda 전용 (SQS 제거, 직접 실행)
 *
 * 아키텍처 변경:
 * - 기존: DynamoDB Streams → Lambda → SQS → Sink Lambda → SinkPlugin
 * - 변경: DynamoDB Streams → Lambda → SinkPlugin (직접)
 *
 * SinkPluginRegistryPort로 target별 SinkPlugin을 관리.
 * 환경변수로 각 Sink 활성화/비활성화:
 * - OPENSEARCH_ENDPOINT: OpenSearch 활성화
 * - S3_BUCKET: S3 활성화
 * - PERSONALIZE_DATASET_ARN: Personalize 활성화
 */
val sinkPluginModule = module {

    // SinkPluginRegistry (target → SinkPlugin 매핑)
    single<SinkPluginRegistryPort> {
        val plugins = mutableMapOf<String, SinkPlugin>()

        // OpenSearch Plugin
        val opensearchEndpoint = System.getenv("OPENSEARCH_ENDPOINT")
        if (!opensearchEndpoint.isNullOrBlank()) {
            val indexPattern = System.getenv("OPENSEARCH_INDEX_PATTERN") ?: "ivm-products-{tenantId}"
            val username = System.getenv("OPENSEARCH_USERNAME")
            val password = System.getenv("OPENSEARCH_PASSWORD")
            val auth = if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                OpenSearchSinkPlugin.AuthConfig(username, password)
            } else null

            plugins["opensearch-sink"] = OpenSearchSinkPlugin(
                endpoint = opensearchEndpoint,
                indexPattern = indexPattern,
                auth = auth,
            )
        }

        // S3 Plugin
        val s3Bucket = System.getenv("S3_BUCKET")
        if (!s3Bucket.isNullOrBlank()) {
            plugins["s3-sink"] = S3SinkPlugin(
                s3Client = S3Client.builder().build(),
                bucketName = s3Bucket,
            )
        }

        // Personalize Plugin
        val personalizeDatasetArn = System.getenv("PERSONALIZE_DATASET_ARN")
        if (!personalizeDatasetArn.isNullOrBlank()) {
            plugins["personalize-sink"] = PersonalizeSinkPlugin(
                personalizeClient = PersonalizeEventsClient.builder().build(),
                datasetArn = personalizeDatasetArn,
            )
        }

        InMemorySinkPluginRegistry(plugins)
    }

    // RFC-020 R4: SinkLedger (DynamoDB 기반 멱등성 보장)
    single<SinkLedger> {
        val ledgerTable = System.getenv("SINK_LEDGER_TABLE") ?: "ivm-sink-ledger"
        DynamoDbSinkLedger(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = ledgerTable,
        )
    }

    // RFC-020 R3: SinkFailureRepository (실패 레코드 저장)
    single<SinkFailureRepositoryPort> {
        val failureTable = System.getenv("SINK_FAILURE_TABLE") ?: "ivm-sink-failures"
        DynamoDbSinkFailureRepository(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = failureTable,
        )
    }
}
