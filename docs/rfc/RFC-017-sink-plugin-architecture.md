# RFC-017 — Sink Plugin Architecture & Infrastructure as Code

**Status**: Partially Implemented
**Created**: 2026-02-12
**Updated**: 2026-02-12
**Scope**: Sink 플러그인 아키텍처, 멀티모듈 구조, IaC 전략
**Depends on**: RFC-V4-010 (아키텍처 레이아웃), ADR-0007 (Sink Orchestration)
**Audience**: Platform / Infrastructure Team

**Implementation Status**:
- ✅ Phase 1: Contract Module (`sinks-contract/`)
- ✅ Phase 2: Core Engine (`pkg/sinks/`)
- ⏳ Phase 3: Plugin Modules (`plugins/sink-*/`) - In Progress
- ⏳ Phase 4: Infrastructure (Terraform/LocalStack) - Planned

---

## 0. Executive Summary

**문제**: 현재 Sink 로직이 엔진 코드와 혼재되어 있어, 새로운 Sink 타입 추가 시 엔진 재배포 필요.

**해결책**:
- Sink 로직을 **독립 플러그인 모듈**로 분리 (S3, Kinesis, ElasticSearch 등)
- 엔진은 **SQS 발행 + 표준 인터페이스만 제공**
- 플러그인은 **AWS Lambda로 독립 배포**
- **Gradle 멀티모듈**로 관리
- **Terraform은 로컬 개발 환경만 지원** (dev/prod는 인프라팀 관리)

**핵심 원칙**:
1. **엔진과 플러그인 완전 분리** (의존성 방향: 플러그인 → 엔진 인터페이스만)
2. **표준 계약 (SinkEnvelopeV1)** 을 통한 느슨한 결합
3. **독립 배포 가능** (엔진 재배포 없이 플러그인 추가/수정)
4. **인프라 관리 분리** (로컬: Terraform, 운영: 인프라팀)
5. **계약 버저닝** (Envelope 버전 관리로 호환성 보장)

---

## 1. Architecture Overview

### 1-1. 아키텍처 다이어그램

```
┌────────────────────────────────────────────────────────────────┐
│  sinks-contract/ (독립 계약 모듈) ✅ IMPLEMENTED               │
│  ├── SinkEnvelopeV1.kt       # 표준 Envelope 모델            │
│  ├── SinkPlugin.kt           # 플러그인 인터페이스            │
│  ├── SinkError.kt            # 에러 타입                      │
│  ├── SinkRoutingTable.kt     # Target → QueueUrl 매핑         │
│  └── SinkJson.kt             # 직렬화 설정                    │
└────────────────────────────────────────────────────────────────┘
                             ▲
                             │ depends on (contract only)
                             │
┌────────────────────────────────────────────────────────────────┐
│  IVM-Lite Engine (Core Module) ✅ IMPLEMENTED                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ pkg/sinks/                                               │  │
│  │  ├── domain/                                             │  │
│  │  │   └── SinkRule.kt           # 비즈니스 라우팅 규칙  │  │
│  │  ├── application/                                        │  │
│  │  │   └── SinkDispatcher.kt     # SQS 발행 로직         │  │
│  │  ├── adapters/                                           │  │
│  │  │   ├── SqsSinkPublisher.kt   # SQS 어댑터            │  │
│  │  │   ├── OpenSearchSinkAdapter.kt                       │  │
│  │  │   └── PersonalizeSinkAdapter.kt                      │  │
│  │  └── ports/                                              │  │
│  │      ├── SinkPort.kt                                     │  │
│  │      └── SinkRuleRegistryPort.kt                        │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                             │
                             │ SQS 메시지 발행
                             ▼
                ┌────────────────────────────┐
                │  AWS SQS Queue             │
                │  (Sink별 전용 Queue)       │
                └────────────────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ Plugin: S3      │ │ Plugin: Kinesis │ │ Plugin: Custom  │
│ (Lambda)        │ │ (Lambda)        │ │ (Lambda)        │
├─────────────────┤ ├─────────────────┤ ├─────────────────┤
│ - S3SinkPlugin  │ │ - KinesisSink   │ │ - CustomSink    │
│ - S3 업로드     │ │ - Stream 전송   │ │ - 사용자 정의   │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 1-2. 데이터 흐름

```
1. View 생성 완료
   └→ SinkDispatcher.dispatch(envelope)
      └→ SinkRoutingTable.queueUrlOf(target) → queueUrl 조회
         └→ SqsSinkPublisher.publish(queueUrl, envelope)
            └→ AWS SQS 메시지 발행

2. Lambda 트리거 (SQS → Lambda) ⏳ PLANNED
   └→ S3SinkLambdaHandler.handleRequest(SQSEvent)
      └→ S3SinkPlugin.execute(envelope)
         └→ S3 업로드 (실제 비즈니스 로직)
```

**현재 구현 상태**:
- ✅ `SinkDispatcher`: 라우팅 테이블 기반 발행
- ✅ `SinkEnvelopeV1`: 풍부한 메타데이터 (traceId, correlationId)
- ✅ `SinkRule`: 비즈니스 라우팅 규칙 (input/target/docId)
- ⏳ Lambda 플러그인: 미구현 (Phase 3)

---

## 2. Module Structure

### 2-1. Gradle 멀티모듈 구조

```
ivm-lite/
├── settings.gradle.kts              # 멀티모듈 정의
├── build.gradle.kts                 # 루트 빌드 설정
│
├── sinks-contract/                  # ⭐ 독립 계약 모듈 ✅ IMPLEMENTED
│   ├── build.gradle.kts             # Contract 전용 빌드
│   └── src/main/kotlin/
│       └── com/oliveyoung/ivmlite/sinks/contract/
│           ├── SinkEnvelopeV1.kt    # 표준 Envelope (버전 관리)
│           ├── SinkPlugin.kt        # 플러그인 인터페이스
│           ├── SinkError.kt         # 에러 타입
│           ├── SinkRoutingTable.kt  # Target → QueueUrl 매핑
│           └── SinkJson.kt          # 직렬화 설정
│
├── src/main/kotlin/                 # 엔진 코어 ✅ IMPLEMENTED
│   └── com/oliveyoung/ivmlite/
│       ├── apps/                    # Admin, Runtime API
│       ├── pkg/
│       │   ├── contracts/
│       │   ├── rawdata/
│       │   ├── slices/
│       │   └── sinks/               # ⭐ Sink 엔진 로직
│       │       ├── domain/
│       │       │   └── SinkRule.kt         # 비즈니스 라우팅 규칙
│       │       ├── application/
│       │       │   └── SinkDispatcher.kt   # SQS 발행 로직
│       │       ├── adapters/
│       │       │   ├── SqsSinkPublisher.kt # SQS 어댑터
│       │       │   ├── OpenSearchSinkAdapter.kt
│       │       │   └── PersonalizeSinkAdapter.kt
│       │       └── ports/
│       │           ├── SinkPort.kt
│       │           └── SinkRuleRegistryPort.kt
│       └── shared/
│
├── plugins/                         # ⭐ Sink 플러그인 모듈들 ⏳ PLANNED
│   ├── sink-s3/                     # S3 플러그인 (예정)
│   │   ├── build.gradle.kts         # S3 플러그인 독립 빌드
│   │   └── src/main/kotlin/
│   │       └── com/oliveyoung/ivmlite/plugins/s3/
│   │           ├── S3SinkPlugin.kt          # SinkPlugin 구현
│   │           ├── S3SinkService.kt         # S3 업로드 로직
│   │           └── lambda/
│   │               └── S3SinkLambdaHandler.kt  # Lambda 진입점
│   │
│   ├── sink-kinesis/                # Kinesis 플러그인 (예정)
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/
│   │       └── com/oliveyoung/ivmlite/plugins/kinesis/
│   │           ├── KinesisSinkPlugin.kt
│   │           ├── KinesisSinkService.kt
│   │           └── lambda/
│   │               └── KinesisSinkLambdaHandler.kt
│   │
│   └── sink-opensearch/             # OpenSearch 플러그인 (예정)
│       └── ...
│
│   # 현재 대안: OpenSearchSinkAdapter, PersonalizeSinkAdapter
│   # → pkg/sinks/adapters/ 에 직접 구현 (Lambda 플러그인 전환 예정)
│
├── infra/                           # ⭐ 배포 & 인프라 문서
│   ├── terraform/                   # 로컬 개발 환경 전용
│   │   ├── local/
│   │   │   ├── main.tf              # LocalStack 기반 로컬 환경
│   │   │   ├── variables.tf
│   │   │   └── README.md            # 로컬 환경 셋업 가이드
│   │   └── modules/
│   │       ├── lambda-sink/         # 재사용 가능한 람다 모듈
│   │       └── sqs-queue/
│   │
│   ├── docs/                        # 인프라팀 전달 문서
│   │   ├── infrastructure-requirements.md   # AWS 리소스 요구사항
│   │   ├── lambda-deployment-guide.md       # Lambda 배포 가이드
│   │   └── environment-config.md            # 환경별 설정 예시
│   │
│   └── scripts/
│       ├── package-lambda.sh        # Lambda JAR 패키징
│       ├── local-deploy.sh          # LocalStack 배포 (개발용)
│       └── build-artifacts.sh       # 인프라팀 전달용 아티팩트 생성
│
└── admin-ui/                        # Admin UI (기존)
```

### 2-2. settings.gradle.kts

```kotlin
rootProject.name = "ivm-lite"

// Core 모듈
include(":core")

// Sink 플러그인 모듈들
include(":plugins:sink-s3")
include(":plugins:sink-kinesis")
include(":plugins:sink-elasticsearch")

// Admin UI (선택)
// include(":admin-ui")
```

### 2-3. 플러그인 빌드 예시 (plugins/sink-s3/build.gradle.kts)

```kotlin
plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"  // Fat JAR 생성
}

dependencies {
    // 엔진 인터페이스만 의존
    implementation(project(":core"))

    // AWS SDK
    implementation("software.amazon.awssdk:s3:2.20.0")
    implementation("software.amazon.awssdk:sqs:2.20.0")

    // Lambda 런타임
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")

    // Arrow (에러 처리)
    implementation("io.arrow-kt:arrow-core:1.2.1")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.1")
}

// Lambda 배포용 Fat JAR 생성
tasks.shadowJar {
    archiveFileName.set("s3-sink-lambda.jar")
    manifest {
        attributes["Main-Class"] = "com.oliveyoung.ivmlite.plugins.s3.lambda.S3SinkLambdaHandler"
    }
}

// Lambda 패키징 태스크
tasks.register<Zip>("packageLambda") {
    dependsOn("shadowJar")
    from(tasks.shadowJar)
    archiveFileName.set("s3-sink-lambda.zip")
}
```

---

## 3. Code Structure & Implementation

### 3-1. Core Module (엔진 인터페이스)

#### SinkPlugin.kt (도메인 인터페이스)

```kotlin
package com.oliveyoung.ivmlite.pkg.sinks.domain

import arrow.core.Either

/**
 * Sink 플러그인 인터페이스
 *
 * 모든 Sink 구현체는 이 인터페이스를 따라야 함.
 */
interface SinkPlugin {
    /**
     * 플러그인 고유 ID (예: "s3-sink", "kinesis-sink")
     */
    val pluginId: String

    /**
     * Sink 실행
     *
     * @param payload 표준 Sink 페이로드
     * @return Either<SinkError, Unit> (성공/실패)
     */
    fun execute(payload: SinkEnvelopeV1): Either<SinkError, Unit>
}
```

#### SinkEnvelopeV1.kt (표준 페이로드)

```kotlin
package com.oliveyoung.ivmlite.sinks.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sink Envelope V1 - 표준 Sink 페이로드 계약
 *
 * RFC-017: Sink Plugin Architecture
 *
 * 버저닝 규칙 (LOCK):
 * - 신규 필드는 OPTIONAL로만 추가 가능
 * - required 필드 추가 금지
 * - unknown 필드는 무시 (직렬화 정책)
 */
@Serializable
data class SinkEnvelopeV1(
    val envelopeVersion: Int = 1,       // Envelope 버전 (계약 진화 추적)
    val target: String,                 // Sink 타겟 식별자 (예: "s3-sink", "opensearch-sink")
    val producedAtEpochMs: Long,        // 엔진 생성 시각 (Epoch Milliseconds)
    val traceId: String? = null,        // 분산 추적 ID (선택)
    val correlationId: String? = null,  // 상관관계 ID (선택)

    // Payload 정보
    val payloadVersion: Long,           // IVM 버전
    val entityType: String,             // 엔티티 타입 (예: "product")
    val sliceType: String,              // 슬라이스 타입 (예: "core")
    val viewName: String,               // 뷰 이름 (예: "view-product-core")
    val viewData: JsonObject,           // 실제 뷰 데이터 (JSON)
    val metadata: Map<String, String> = emptyMap()  // 추가 메타데이터 (확장용)
)
```

**주요 개선점**:
- ✅ `envelopeVersion`: 계약 버전 추적
- ✅ `target`: 라우팅 정보 포함
- ✅ `traceId`/`correlationId`: 분산 추적 지원
- ✅ `producedAtEpochMs`: 타임스탬프
- ⚠️ `metadata`: 타입 안전하지 않은 확장용 (최소 사용 권장)

#### SinkError.kt (에러 타입)

```kotlin
package com.oliveyoung.ivmlite.sinks.contract

sealed class SinkError(override val message: String) : Exception(message) {
    data class PublishError(
        override val message: String,
        val retryable: Boolean = true
    ) : SinkError(message)

    data class RoutingError(
        override val message: String,
        val retryable: Boolean = false
    ) : SinkError(message)

    data class PluginExecutionError(
        val pluginId: String,
        override val message: String,
        val retryable: Boolean = true
    ) : SinkError(message)

    data class PayloadParseError(
        override val message: String,
        val retryable: Boolean = false
    ) : SinkError(message)
}
```

**주요 개선점**:
- ✅ `retryable`: 재시도 가능 여부 명시
- ✅ `RoutingError`: 라우팅 실패 (target → queueUrl 실패)

#### SinkDispatcher.kt (엔진 레벨 디스패처) ✅ IMPLEMENTED

```kotlin
package com.oliveyoung.ivmlite.pkg.sinks.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.sinks.contract.SinkEnvelopeV1
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkRoutingTable
import com.oliveyoung.ivmlite.pkg.sinks.adapters.SqsSinkPublisher

/**
 * Sink Dispatcher (SSOT)
 *
 * RFC-017: Sink Plugin Architecture
 *
 * View → SQS 발행을 담당 (엔진 책임)
 */
class SinkDispatcher(
    private val routingTable: SinkRoutingTable,  // ✅ 라우팅 테이블 기반
    private val sqsPublisher: SqsSinkPublisher
) {
    /**
     * Sink로 Envelope 발행
     *
     * @param envelope Sink Envelope (target 포함)
     */
    fun dispatch(envelope: SinkEnvelopeV1): Either<SinkError, Unit> = either {
        val queueUrl = routingTable.queueUrlOf(envelope.target)
            ?: raise(SinkError.RoutingError("No queue URL for target=${envelope.target}"))

        sqsPublisher.publish(queueUrl, envelope).bind()
    }

    /**
     * 배치 발행 (최적화)
     */
    fun dispatchBatch(envelopes: List<SinkEnvelopeV1>): Either<SinkError, Unit> = either {
        envelopes.forEach { dispatch(it).bind() }
    }
}
```

**주요 변경점**:
- ✅ `SinkRoutingTable` 사용 (target → queueUrl 자동 매핑)
- ✅ `envelope.target` 기반 라우팅
- ✅ 배치 발행 지원

#### SqsSinkPublisher.kt (SQS 어댑터)

```kotlin
package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEnvelopeV1
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class SqsSinkPublisher(
    private val sqsClient: SqsClient,
    private val json: Json = Json
) {
    fun publish(queueUrl: String, payload: SinkEnvelopeV1): Either<SinkError, Unit> = either {
        catch({ e: Exception ->
            raise(SinkError.PublishError("Failed to publish to SQS: ${e.message}"))
        }) {
            val messageBody = json.encodeToString(payload)

            sqsClient.sendMessage(
                SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build()
            )
        }
    }
}
```

---

### 3-2. Plugin Module (S3 Sink 예시)

#### S3SinkPlugin.kt

```kotlin
package com.oliveyoung.ivmlite.plugins.s3

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkPlugin
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEnvelopeV1
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkError
import kotlinx.serialization.json.Json
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * S3 Sink 플러그인
 *
 * View 데이터를 S3에 JSON 파일로 저장
 */
class S3SinkPlugin(
    private val s3Client: S3Client,
    private val bucketName: String,
    private val json: Json = Json
) : SinkPlugin {

    override val pluginId = "s3-sink"

    override fun execute(payload: SinkEnvelopeV1): Either<SinkError, Unit> = either {
        catch({ e: Exception ->
            raise(SinkError.PluginExecutionError(pluginId, "S3 upload failed: ${e.message}"))
        }) {
            val key = buildKey(payload)
            val content = json.encodeToString(payload.viewData)

            logger.info { "Uploading to S3: bucket=$bucketName, key=$key" }

            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/json")
                    .metadata(payload.metadata)
                    .build(),
                RequestBody.fromString(content)
            )

            logger.info { "S3 upload completed: key=$key" }
        }
    }

    private fun buildKey(payload: SinkEnvelopeV1): String {
        // 예: views/product/core/v12345.json
        return "views/${payload.entityType}/${payload.sliceType}/v${payload.version}.json"
    }
}
```

#### S3SinkLambdaHandler.kt (Lambda 진입점)

```kotlin
package com.oliveyoung.ivmlite.plugins.s3.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEnvelopeV1
import com.oliveyoung.ivmlite.plugins.s3.S3SinkPlugin
import kotlinx.serialization.json.Json
import software.amazon.awssdk.services.s3.S3Client
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * S3 Sink Lambda Handler
 *
 * SQS 이벤트를 수신하여 S3에 저장
 */
class S3SinkLambdaHandler : RequestHandler<SQSEvent, String> {

    private val plugin by lazy {
        val bucketName = System.getenv("S3_BUCKET")
            ?: throw IllegalStateException("S3_BUCKET environment variable is required")

        S3SinkPlugin(
            s3Client = S3Client.builder().build(),
            bucketName = bucketName
        )
    }

    override fun handleRequest(event: SQSEvent, context: Context): String {
        logger.info { "Received ${event.records.size} messages from SQS" }

        val results = event.records.map { record ->
            try {
                val payload = Json.decodeFromString<SinkEnvelopeV1>(record.body)

                plugin.execute(payload).fold(
                    { error ->
                        logger.error { "Plugin execution failed: $error" }
                        "FAILED: ${error.message}"
                    },
                    {
                        logger.info { "Plugin execution succeeded for version ${payload.version}" }
                        "SUCCESS"
                    }
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to process SQS message" }
                "ERROR: ${e.message}"
            }
        }

        return "Processed ${results.size} messages: ${results.count { it.startsWith("SUCCESS") }} succeeded"
    }
}
```

---

## 4. Infrastructure Strategy

### 4-1. 인프라 관리 책임 분리

| 환경 | 관리 주체 | 도구 | 용도 |
|------|----------|------|------|
| **로컬** | 개발팀 | Terraform + LocalStack | 개발/테스트 |
| **Dev/Staging/Prod** | 인프라팀 | 인프라팀 IaC (Terraform/CloudFormation/etc) | 운영 환경 |

### 4-2. 로컬 개발 환경 (Terraform + LocalStack)

#### infra/terraform/local/main.tf

```hcl
# LocalStack Provider
terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region                      = "ap-northeast-2"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    lambda     = "http://localhost:4566"
    sqs        = "http://localhost:4566"
    s3         = "http://localhost:4566"
    iam        = "http://localhost:4566"
    cloudwatch = "http://localhost:4566"
  }
}

# SQS Queue (S3 Sink용)
resource "aws_sqs_queue" "s3_sink_local" {
  name                       = "local-s3-sink-queue"
  visibility_timeout_seconds = 300

  tags = {
    Environment = "local"
  }
}

# S3 Bucket
resource "aws_s3_bucket" "sink_data_local" {
  bucket = "local-ivm-lite-sink-data"

  tags = {
    Environment = "local"
  }
}

# Lambda (LocalStack용 간소화)
resource "aws_lambda_function" "s3_sink_local" {
  function_name = "local-s3-sink"
  handler       = "com.oliveyoung.ivmlite.plugins.s3.lambda.S3SinkLambdaHandler::handleRequest"
  runtime       = "java17"
  role          = aws_iam_role.lambda_exec_local.arn

  filename         = "../../../plugins/sink-s3/build/libs/s3-sink-lambda.jar"
  source_code_hash = filebase64sha256("../../../plugins/sink-s3/build/libs/s3-sink-lambda.jar")

  timeout     = 60
  memory_size = 512

  environment {
    variables = {
      S3_BUCKET = aws_s3_bucket.sink_data_local.id
    }
  }
}

# IAM Role (LocalStack용)
resource "aws_iam_role" "lambda_exec_local" {
  name = "local-lambda-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# Output
output "s3_sink_queue_url" {
  value = aws_sqs_queue.s3_sink_local.url
}
```

### 4-3. 인프라팀 전달 문서

#### infra/docs/infrastructure-requirements.md

```markdown
# IVM-Lite Sink Plugin 인프라 요구사항

## 개요
IVM-Lite Sink 플러그인을 위한 AWS 리소스 요구사항 명세입니다.

## 필요 리소스

### 1. SQS Queue (Sink별 1개)
| 항목 | 값 |
|------|-----|
| Queue Type | Standard Queue |
| Visibility Timeout | 300초 |
| Message Retention | 14일 |
| DLQ | 필요 (재시도 3회 후 이동) |

**예시:**
- `{env}-s3-sink-queue`
- `{env}-kinesis-sink-queue`

### 2. Lambda Function (Sink별 1개)
| 항목 | 값 |
|------|-----|
| Runtime | Java 17 (Corretto) |
| Memory | 512MB |
| Timeout | 60초 |
| Concurrency | Reserved 10 |
| Event Source | SQS (Batch Size: 10) |

**환경 변수:**
- S3 Sink: `S3_BUCKET={bucket-name}`
- Kinesis Sink: `KINESIS_STREAM={stream-name}`

### 3. IAM Role & Policy
Lambda 실행 Role에 필요한 권한:
- `AWSLambdaBasicExecutionRole` (CloudWatch Logs)
- SQS Read/Delete (해당 Queue만)
- S3 PutObject (S3 Sink의 경우)
- Kinesis PutRecord (Kinesis Sink의 경우)

### 4. 아티팩트 전달
개발팀에서 전달하는 파일:
- `s3-sink-lambda.jar` (Lambda 배포 패키지)
- `kinesis-sink-lambda.jar`

## 환경별 네이밍 규칙
| 환경 | Prefix |
|------|--------|
| Dev | `dev-ivm-lite-` |
| Staging | `stg-ivm-lite-` |
| Prod | `prd-ivm-lite-` |

## 배포 절차
1. 개발팀: JAR 파일 S3 버킷에 업로드
2. 인프라팀: Lambda 함수 생성/업데이트
3. 인프라팀: SQS Event Source Mapping 설정
4. 개발팀: Queue URL 전달받아 엔진 설정
```

#### infra/docs/lambda-deployment-guide.md

```markdown
# Lambda 배포 가이드 (인프라팀용)

## 1. 아티팩트 다운로드
```bash
# 개발팀이 업로드한 JAR 파일
aws s3 cp s3://ivm-lite-artifacts/sink-plugins/v1.0.0/s3-sink-lambda.jar .
```

## 2. Lambda 함수 생성 (Terraform 예시)
```hcl
resource "aws_lambda_function" "s3_sink" {
  function_name = "prd-ivm-lite-s3-sink"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "com.oliveyoung.ivmlite.plugins.s3.lambda.S3SinkLambdaHandler::handleRequest"
  runtime       = "java17"

  s3_bucket = "ivm-lite-artifacts"
  s3_key    = "sink-plugins/v1.0.0/s3-sink-lambda.jar"

  timeout     = 60
  memory_size = 512

  environment {
    variables = {
      S3_BUCKET = "prd-ivm-lite-sink-data"
    }
  }
}

resource "aws_lambda_event_source_mapping" "sqs_trigger" {
  event_source_arn = aws_sqs_queue.s3_sink.arn
  function_name    = aws_lambda_function.s3_sink.arn
  batch_size       = 10
}
```

## 3. 배포 후 확인
```bash
# Lambda 함수 확인
aws lambda get-function --function-name prd-ivm-lite-s3-sink

# SQS Event Source Mapping 확인
aws lambda list-event-source-mappings --function-name prd-ivm-lite-s3-sink
```

## 4. 모니터링
- CloudWatch Logs: `/aws/lambda/prd-ivm-lite-s3-sink`
- CloudWatch Metrics: Lambda Errors, Duration, Invocations
```

---

## 5. Deployment & Operations

### 5-1. 로컬 개발 환경 배포

#### Just 명령어 (Justfile)

```bash
# Lambda 패키징
package-lambda plugin:
    cd plugins/sink-{{plugin}} && ./gradlew clean shadowJar

# 로컬 환경 시작 (LocalStack)
local-infra-up:
    docker-compose -f infra/docker-compose.localstack.yml up -d

# 로컬 Terraform 적용
local-deploy:
    cd infra/terraform/local && terraform init && terraform apply -auto-approve

# 로컬 환경 전체 셋업
local-setup: local-infra-up package-lambda local-deploy
    echo "✅ Local environment ready!"

# 로컬 환경 정리
local-cleanup:
    cd infra/terraform/local && terraform destroy -auto-approve
    docker-compose -f infra/docker-compose.localstack.yml down
```

#### LocalStack Docker Compose (infra/docker-compose.localstack.yml)

```yaml
version: '3.8'

services:
  localstack:
    image: localstack/localstack:3.0
    ports:
      - "4566:4566"  # LocalStack Gateway
    environment:
      - SERVICES=lambda,sqs,s3,iam,cloudwatch
      - DEBUG=1
      - LAMBDA_RUNTIME_ENVIRONMENT_TIMEOUT=60
    volumes:
      - "./localstack-data:/var/lib/localstack"
      - "/var/run/docker.sock:/var/run/docker.sock"
```

### 5-2. 운영 환경 배포 (인프라팀 협업)

#### 개발팀 책임: 아티팩트 생성 & 전달

```bash
#!/bin/bash
# infra/scripts/build-artifacts.sh

set -e

VERSION=${1:-$(git describe --tags --always)}
OUTPUT_DIR="build/artifacts/$VERSION"

echo "📦 Building artifacts for version: $VERSION"

# 1. Lambda JAR 빌드
./gradlew :plugins:sink-s3:shadowJar
./gradlew :plugins:sink-kinesis:shadowJar

# 2. 아티팩트 디렉토리 생성
mkdir -p "$OUTPUT_DIR"

# 3. JAR 파일 복사
cp plugins/sink-s3/build/libs/s3-sink-lambda.jar "$OUTPUT_DIR/"
cp plugins/sink-kinesis/build/libs/kinesis-sink-lambda.jar "$OUTPUT_DIR/"

# 4. 배포 가이드 복사
cp infra/docs/infrastructure-requirements.md "$OUTPUT_DIR/"
cp infra/docs/lambda-deployment-guide.md "$OUTPUT_DIR/"

# 5. 메타데이터 생성
cat > "$OUTPUT_DIR/metadata.json" <<EOF
{
  "version": "$VERSION",
  "build_date": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "git_commit": "$(git rev-parse HEAD)",
  "artifacts": [
    {
      "name": "s3-sink-lambda.jar",
      "handler": "com.oliveyoung.ivmlite.plugins.s3.lambda.S3SinkLambdaHandler::handleRequest",
      "runtime": "java17",
      "memory": 512,
      "timeout": 60
    },
    {
      "name": "kinesis-sink-lambda.jar",
      "handler": "com.oliveyoung.ivmlite.plugins.kinesis.lambda.KinesisSinkLambdaHandler::handleRequest",
      "runtime": "java17",
      "memory": 512,
      "timeout": 60
    }
  ]
}
EOF

echo "✅ Artifacts created in $OUTPUT_DIR"
echo "📤 Upload to S3: aws s3 sync $OUTPUT_DIR s3://ivm-lite-artifacts/sink-plugins/$VERSION/"
```

#### 인프라팀 전달 체크리스트

```markdown
# 운영 환경 배포 전달 사항

## 📦 전달 파일
- [ ] `s3-sink-lambda.jar`
- [ ] `kinesis-sink-lambda.jar`
- [ ] `metadata.json`
- [ ] `infrastructure-requirements.md`
- [ ] `lambda-deployment-guide.md`

## 📋 환경 변수
### S3 Sink
- `S3_BUCKET`: 뷰 데이터를 저장할 S3 버킷 이름

### Kinesis Sink
- `KINESIS_STREAM`: 데이터를 전송할 Kinesis Stream 이름

## 🔗 필요한 연동 정보 (인프라팀 → 개발팀)
배포 완료 후 다음 정보를 전달받아야 함:
- [ ] SQS Queue URL (S3 Sink용)
- [ ] SQS Queue URL (Kinesis Sink용)
- [ ] Lambda Function ARN (모니터링용)

## 📊 모니터링 대시보드
- CloudWatch Logs Group: `/aws/lambda/{env}-ivm-lite-{sink-name}-sink`
- CloudWatch Metrics: Lambda Errors, Duration, Throttles
```

---

## 6. Responsibilities & Boundaries

### 6-1. 역할 분리

| 계층 | 모듈 | 책임 | 상태 | 의존성 |
|------|------|------|------|--------|
| **계약 모듈** | `sinks-contract/` | `SinkEnvelopeV1`, `SinkPlugin`, `SinkError`, `SinkRoutingTable` 정의 | ✅ | 없음 |
| **엔진 도메인** | `pkg/sinks/domain/` | `SinkRule` 비즈니스 라우팅 규칙 | ✅ | `sinks-contract/` |
| **엔진 로직** | `pkg/sinks/application/` | `SinkDispatcher` - SQS 발행, 라우팅 | ✅ | `domain/`, `adapters/`, `contract/` |
| **엔진 어댑터** | `pkg/sinks/adapters/` | `SqsSinkPublisher`, `OpenSearchSinkAdapter`, `PersonalizeSinkAdapter` | ✅ | AWS SDK, `contract/` |
| **엔진 포트** | `pkg/sinks/ports/` | `SinkPort`, `SinkRuleRegistryPort` | ✅ | `domain/`, `contract/` |
| **플러그인** | `plugins/sink-*/` | Sink별 비즈니스 로직 (S3, Kinesis, OpenSearch 등) | ⏳ | `sinks-contract/` (인터페이스만) |
| **Lambda Handler** | `plugins/sink-*/lambda/` | AWS Lambda 진입점, DI 설정 | ⏳ | 플러그인 도메인 |
| **인프라** | `infra/terraform/` | AWS 리소스 프로비저닝 | ⏳ | 없음 (IaC) |

**핵심 개념 분리**:
- **`SinkRule`** (비즈니스): 어떤 Slice를 어느 Sink로 보낼지 (input/target/docId)
- **`SinkRoutingTable`** (인프라): target → queueUrl 물리적 매핑
- **`SinkEnvelopeV1`** (계약): 엔진 ↔ 플러그인 데이터 교환 포맷

### 6-2. 의존성 방향

```
┌───────────────────────────────────────┐
│  Core (Engine)                        │
│  ├── domain/ (인터페이스)            │
│  ├── application/ (SQS 발행)          │
│  └── adapters/ (SQS 클라이언트)       │
└───────────────────────────────────────┘
           ▲
           │ implements (인터페이스만 의존)
           │
┌──────────┴──────────┬───────────────┐
│ Plugin: S3          │ Plugin: Kinesis│
│ ├── S3SinkPlugin    │ ├── KinesisSink│
│ └── Lambda Handler  │ └── Lambda      │
└─────────────────────┴────────────────┘
```

### 6-3. 독립성 보장

- **빌드 독립성**: 각 플러그인은 독립 빌드 가능 (`./gradlew :plugins:sink-s3:build`)
- **배포 독립성**: 엔진 재배포 없이 플러그인만 배포 가능
- **런타임 독립성**: 플러그인 장애가 엔진에 영향 없음 (SQS DLQ 활용)

---

## 7. Testing Strategy

### 7-1. 유닛 테스트

```kotlin
// plugins/sink-s3/src/test/kotlin/S3SinkPluginTest.kt
class S3SinkPluginTest {

    @Test
    fun `execute should upload to S3 successfully`() {
        // Given
        val mockS3 = mockk<S3Client>()
        val plugin = S3SinkPlugin(mockS3, "test-bucket")
        val payload = SinkEnvelopeV1(
            version = 123,
            entityType = "product",
            sliceType = "core",
            viewName = "view-product-core",
            viewData = buildJsonObject { put("id", "P001") },
            metadata = emptyMap()
        )

        every { mockS3.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns mockk()

        // When
        val result = plugin.execute(payload)

        // Then
        result.shouldBeRight()
        verify { mockS3.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
    }
}
```

### 7-2. 통합 테스트

```kotlin
// plugins/sink-s3/src/test/kotlin/S3SinkIntegrationTest.kt
@IntegrationTag
class S3SinkIntegrationTest {

    @Test
    fun `end-to-end S3 upload test with LocalStack`() {
        // LocalStack S3로 실제 업로드 테스트
    }
}
```

### 7-3. Lambda 로컬 테스트

```bash
# SAM CLI를 사용한 로컬 테스트
sam local invoke S3SinkLambda --event test-event.json
```

---

## 8. Rollout Plan

### Phase 1: 계약 모듈 (✅ COMPLETED)
- [x] 독립 `sinks-contract/` 모듈 생성
- [x] `SinkEnvelopeV1`: 표준 Envelope (버저닝 포함)
- [x] `SinkPlugin`: 플러그인 인터페이스
- [x] `SinkError`: 에러 타입 (retryable 포함)
- [x] `SinkRoutingTable`: target → queueUrl 매핑
- [x] `SinkJson`: 직렬화 설정

### Phase 2: 엔진 코어 (✅ COMPLETED)
- [x] `pkg/sinks/domain/SinkRule`: 비즈니스 라우팅 규칙
- [x] `pkg/sinks/application/SinkDispatcher`: SQS 발행 로직
- [x] `pkg/sinks/adapters/SqsSinkPublisher`: SQS 어댑터
- [x] `pkg/sinks/adapters/OpenSearchSinkAdapter`: OpenSearch 어댑터
- [x] `pkg/sinks/adapters/PersonalizeSinkAdapter`: Personalize 어댑터
- [x] `pkg/sinks/ports/`: Port 인터페이스

### Phase 3: 플러그인 모듈 (⏳ IN PROGRESS)
- [ ] `plugins/sink-s3/`: S3 플러그인
- [ ] `plugins/sink-kinesis/`: Kinesis 플러그인
- [ ] `plugins/sink-opensearch/`: OpenSearch 플러그인 (어댑터 마이그레이션)
- [ ] Lambda Handler 구현
- [ ] 유닛/통합 테스트

### Phase 4: 로컬 개발 환경 (⏳ PLANNED)
- [ ] `infra/docker-compose.localstack.yml`
- [ ] `infra/terraform/local/`: LocalStack Terraform
- [ ] LocalStack 배포 자동화
- [ ] 로컬 E2E 테스트

### Phase 5: 운영 환경 준비 (⏳ PLANNED)
- [ ] 인프라 요구사항 문서
- [ ] Lambda 배포 가이드
- [ ] 아티팩트 빌드 스크립트
- [ ] 인프라팀 협업
- [ ] 모니터링/알람 설정

**현재 상태**: Phase 2 완료, Phase 3 진행 중
**현재 대안**: OpenSearch/Personalize는 어댑터로 직접 구현 (Lambda 전환 예정)

---

## 9. Monitoring & Observability

### 9-1. CloudWatch Metrics

```hcl
# Lambda 메트릭
resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.environment}-${var.sink_name}-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"

  dimensions = {
    FunctionName = aws_lambda_function.sink.function_name
  }
}
```

### 9-2. Logging

```kotlin
// 구조화된 로깅
logger.info {
    mapOf(
        "event" to "sink_execution",
        "plugin_id" to pluginId,
        "version" to payload.version,
        "entity_type" to payload.entityType,
        "duration_ms" to duration
    )
}
```

---

## 10. Future Extensions

### 10-1. 추가 플러그인 예시

- **ElasticSearch Sink**: 검색 인덱싱
- **Kafka Sink**: 이벤트 스트리밍
- **Redis Sink**: 캐시 업데이트
- **Webhook Sink**: 외부 API 호출

### 10-2. 플러그인 마켓플레이스

```
plugins-marketplace/
├── community/
│   ├── sink-redis/
│   ├── sink-webhook/
│   └── sink-custom/
└── official/
    ├── sink-s3/
    └── sink-kinesis/
```

---

## 11. Decision Log

| 항목 | RFC 제안 | 실제 구현 | 상태 | 근거 |
|------|----------|----------|------|------|
| **계약 모듈** | `core/pkg/sinks/domain/` | `sinks-contract/` 독립 모듈 | ✅ 개선 | 독립 배포, 버전 관리 용이 |
| **페이로드 타입** | `SinkPayload` | `SinkEnvelopeV1` | ✅ 개선 | 버전 관리, 풍부한 메타데이터 |
| **라우팅** | queueUrl 직접 전달 | `SinkRoutingTable` + `SinkRule` | ✅ 개선 | 비즈니스/인프라 분리 |
| **에러 타입** | 단순 에러 | `retryable` 포함 | ✅ 개선 | 재시도 가능 여부 명시 |
| **Sink 구현** | Lambda 플러그인 | Adapter 직접 구현 (임시) | ⏳ 전환 예정 | OpenSearch/Personalize 우선 구현 |
| **모듈 분리** | Gradle 멀티모듈 | 현재 단일 모듈 | ⏳ 예정 | 플러그인 독립 배포 위해 |
| **배포 방식** | AWS Lambda | 현재 엔진 내장 | ⏳ 예정 | 서버리스, Auto-scaling |
| **IaC (로컬)** | Terraform + LocalStack | 미구현 | ⏳ 예정 | 로컬 개발 환경 재현성 |
| **IaC (운영)** | 인프라팀 관리 | 미구현 | ⏳ 예정 | 조직 표준 준수 |
| **메시징** | SQS | SQS | ✅ | 완전 관리형, Lambda 통합 |
| **에러 처리** | Arrow Either | Arrow Either | ✅ | 함수형, 타입 안전 |

**핵심 개선 사항**:
1. ✅ **독립 계약 모듈**: `sinks-contract/` 분리로 플러그인 독립성 강화
2. ✅ **SinkEnvelopeV1**: 버전 관리, traceId/correlationId 지원
3. ✅ **SinkRule**: 비즈니스 라우팅 규칙 명시적 분리
4. ⏳ **Lambda 플러그인**: Phase 3 진행 중 (현재 Adapter로 대체)

---

## 12. Current Implementation vs RFC

### 12-1. 구현된 부분 (✅)

**계약 모듈** (`sinks-contract/`):
```kotlin
✅ SinkEnvelopeV1    // 표준 Envelope (RFC보다 풍부)
✅ SinkPlugin        // 플러그인 인터페이스
✅ SinkError         // 에러 타입 (retryable 추가)
✅ SinkRoutingTable  // Target → QueueUrl 매핑 (RFC 누락 개념)
✅ SinkJson          // 직렬화 설정
```

**엔진 코어** (`pkg/sinks/`):
```kotlin
✅ SinkRule               // 비즈니스 라우팅 규칙 (RFC 누락)
✅ SinkDispatcher         // SQS 발행 + 라우팅 테이블
✅ SqsSinkPublisher       // SQS 어댑터
✅ OpenSearchSinkAdapter  // OpenSearch 직접 구현
✅ PersonalizeSinkAdapter // Personalize 직접 구현
```

**주요 개선점**:
- 🔥 **독립 계약 모듈**: RFC는 core 내부 제안 → 실제는 독립 모듈
- 🔥 **SinkRule 추가**: 비즈니스 라우팅 규칙 (input/target/docId/commit)
- 🔥 **Envelope 버저닝**: 계약 진화 추적
- 🔥 **분산 추적**: traceId/correlationId 지원

### 12-2. 미구현 부분 (⏳)

**플러그인 모듈**:
```
⏳ plugins/sink-s3/
⏳ plugins/sink-kinesis/
⏳ plugins/sink-opensearch/  (현재 Adapter로 구현)
```

**인프라**:
```
⏳ infra/terraform/local/     (LocalStack)
⏳ infra/terraform/modules/
⏳ infra/scripts/
```

**현재 대안**:
- OpenSearch/Personalize: `pkg/sinks/adapters/` 직접 구현
- Lambda 전환: Phase 3 계획

### 12-3. RFC 업데이트 요약

**명명 통일**:
- `SinkPayload` → `SinkEnvelopeV1` (전역 치환)

**모듈 구조**:
- `core/pkg/sinks/domain/` → `sinks-contract/` (독립)

**새 개념 추가**:
- `SinkRule`: 비즈니스 라우팅 규칙
- `SinkRoutingTable`: 인프라 매핑
- `envelopeVersion`: 계약 버전 추적
- `traceId`/`correlationId`: 분산 추적

**Implementation Status 명시**:
- ✅ Phase 1-2: 완료
- ⏳ Phase 3-5: 진행 중/예정

---

## 13. Infrastructure Team Handoff

### 13-1. 협업 프로세스

```
1. 개발팀: 플러그인 구현 완료
   └→ Just: just build-artifacts v1.0.0

2. 개발팀: 아티팩트 전달
   └→ S3 업로드: aws s3 sync build/artifacts/v1.0.0/ s3://ivm-lite-artifacts/sink-plugins/v1.0.0/

3. 개발팀: 인프라팀에 배포 요청
   └→ Jira 티켓 생성 + 문서 전달

4. 인프라팀: 리소스 프로비저닝
   └→ Lambda, SQS, IAM 생성

5. 인프라팀 → 개발팀: 연동 정보 전달
   └→ SQS Queue URL, Lambda ARN

6. 개발팀: 엔진 설정 업데이트
   └→ application.yaml에 Queue URL 추가

7. 검증 & 모니터링
```

### 13-2. 필수 전달 정보

#### 개발팀 → 인프라팀
```yaml
# deployment-request.yaml
version: "1.0.0"
plugins:
  - name: "s3-sink"
    artifact: "s3://ivm-lite-artifacts/sink-plugins/v1.0.0/s3-sink-lambda.jar"
    handler: "com.oliveyoung.ivmlite.plugins.s3.lambda.S3SinkLambdaHandler::handleRequest"
    runtime: "java17"
    memory: 512
    timeout: 60
    environment:
      S3_BUCKET: "{env}-ivm-lite-sink-data"
    sqs_config:
      batch_size: 10
      visibility_timeout: 300
```

#### 인프라팀 → 개발팀
```yaml
# deployment-result.yaml
environment: "dev"
deployed_at: "2026-02-12T10:00:00Z"
resources:
  s3_sink:
    queue_url: "https://sqs.ap-northeast-2.amazonaws.com/123456789/dev-ivm-lite-s3-sink-queue"
    lambda_arn: "arn:aws:lambda:ap-northeast-2:123456789:function:dev-ivm-lite-s3-sink"
    cloudwatch_log_group: "/aws/lambda/dev-ivm-lite-s3-sink"
```

---

## 14. References

### 14-1. Internal

- **ADR-0007**: Sink Orchestration
- **RFC-V4-010**: Architecture Layout
- **RFC-IMPL-013**: Slice → Sink 자동 라우팅
- **sinks-contract/**: `/Users/mac/Documents/code-oyg-v2/ivm-lite-oliveyoung-full/sinks-contract/`
- **pkg/sinks/**: `/Users/mac/Documents/code-oyg-v2/ivm-lite-oliveyoung-full/src/main/kotlin/com/oliveyoung/ivmlite/pkg/sinks/`

### 14-2. External

- **AWS Lambda Best Practices**: https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html
- **LocalStack Docs**: https://docs.localstack.cloud/
- **Gradle Multi-Project Builds**: https://docs.gradle.org/current/userguide/multi_project_builds.html
- **Arrow Either**: https://arrow-kt.io/docs/core/either/
- **Kotlin Serialization**: https://github.com/Kotlin/kotlinx.serialization

### 14-3. Implementation Examples

**현재 구현 참고**:
```bash
# 계약 모듈
sinks-contract/src/main/kotlin/com/oliveyoung/ivmlite/sinks/contract/
  ├── SinkEnvelopeV1.kt
  ├── SinkPlugin.kt
  ├── SinkError.kt
  ├── SinkRoutingTable.kt
  └── SinkJson.kt

# 엔진 코어
src/main/kotlin/com/oliveyoung/ivmlite/pkg/sinks/
  ├── domain/SinkRule.kt
  ├── application/SinkDispatcher.kt
  ├── adapters/SqsSinkPublisher.kt
  └── ports/SinkRuleRegistryPort.kt
```

---

**Authors**: Platform Team
**Reviewers**: Infrastructure Team
**Status**: Partially Implemented (Phase 1-2 Complete)
**Last Updated**: 2026-02-12

**Change Log**:
- 2026-02-12: RFC 업데이트 - 실제 구현 반영
  - `SinkPayload` → `SinkEnvelopeV1` 전역 치환
  - `sinks-contract/` 독립 모듈 반영
  - `SinkRule` 추가
  - Phase별 구현 상태 명시
