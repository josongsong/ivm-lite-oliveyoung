package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.sinks.adapters.DynamoDbSinkFailureRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.DynamoDbSinkLedger
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkPluginRegistry
import com.oliveyoung.ivmlite.pkg.sinks.adapters.SinkPreflightPluginRegistryAdapter
import com.oliveyoung.ivmlite.pkg.sinks.adapters.OpenSearchSinkPlugin
import com.oliveyoung.ivmlite.pkg.sinks.adapters.PersonalizeSinkPlugin
import com.oliveyoung.ivmlite.pkg.sinks.adapters.S3SinkPlugin
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkTargetType
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPreflightPort
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
 * Sink Plugin Registry Module - Runtime API + Lambda 공유
 *
 * DynamoDB 의존성 없음. Ingest Preflight 및 Lambda Sink 처리에 사용.
 * 환경변수로 각 Sink 활성화/비활성화:
 * - OPENSEARCH_ENDPOINT: OpenSearch 활성화
 * - S3_BUCKET: S3 활성화
 * - PERSONALIZE_DATASET_ARN: Personalize 활성화
 */
val sinkPluginRegistryModule = module {

    // SinkPluginRegistry (target → SinkPlugin 매핑)
    single<SinkPluginRegistryPort> {
        val plugins = mutableMapOf<String, SinkPlugin>()

        // OpenSearch Plugin (opensearch-index-plan v2: Static write alias)
        val opensearchEndpoint = System.getenv("OPENSEARCH_ENDPOINT")
        if (!opensearchEndpoint.isNullOrBlank()) {
            val writeAlias = System.getenv("OPENSEARCH_STATIC_WRITE_ALIAS")
                ?: System.getenv("OPENSEARCH_INDEX_PATTERN")
                ?: "ivm-products-{tenantId}__write"
            val username = System.getenv("OPENSEARCH_USERNAME")
            val password = System.getenv("OPENSEARCH_PASSWORD")
            val auth = if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                OpenSearchSinkPlugin.AuthConfig(username, password)
            } else null

            plugins[SinkTargetType.OPENSEARCH.toPluginId()] = OpenSearchSinkPlugin(
                endpoint = opensearchEndpoint,
                indexPattern = writeAlias,
                auth = auth,
                useStaticProjection = true,
            )
        }

        // S3 Plugin
        val s3Bucket = System.getenv("S3_BUCKET")
        if (!s3Bucket.isNullOrBlank()) {
            plugins[SinkTargetType.S3.toPluginId()] = S3SinkPlugin(
                s3Client = S3Client.builder().build(),
                bucketName = s3Bucket,
            )
        }

        // Personalize Plugin
        val personalizeDatasetArn = System.getenv("PERSONALIZE_DATASET_ARN")
        if (!personalizeDatasetArn.isNullOrBlank()) {
            plugins[SinkTargetType.PERSONALIZE.toPluginId()] = PersonalizeSinkPlugin(
                personalizeClient = PersonalizeEventsClient.builder().build(),
                datasetArn = personalizeDatasetArn,
            )
        }

        InMemorySinkPluginRegistry(plugins)
    }

    // Sink Preflight (Ingest 시점 검증, DX)
    single<SinkPreflightPort> {
        SinkPreflightPluginRegistryAdapter(pluginRegistry = get())
    }
}

/**
 * Sink Plugin Module - Lambda 전용 (SQS 제거, 직접 실행)
 *
 * sinkPluginRegistryModule + DynamoDB 기반 Ledger/FailureRepo.
 * Lambda에서 SinkEvent 처리 시 사용.
 */
val sinkPluginModule = module {

    // SinkPluginRegistry는 sinkPluginRegistryModule에서 로드 (공유)
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
