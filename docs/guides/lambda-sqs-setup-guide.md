# Lambda & SQS 환경 설정 가이드

> 다른 환경(dev/staging/prod)에 Lambda와 SQS를 세팅할 때 필요한 정보를 한 곳에 모았습니다.

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [Lambda 종류별 설정](#2-lambda-종류별-설정)
3. [SQS 설정](#3-sqs-설정)
4. [환경 변수 전체 목록](#4-환경-변수-전체-목록)
5. [IAM 권한](#5-iam-권한)
6. [인프라 (Terraform)](#6-인프라-terraform)
7. [빌드 & 배포](#7-빌드--배포)
8. [체크리스트](#8-체크리스트)

---

## 1. 아키텍처 개요

### 경로 A: DynamoDB Streams (기본)

```
IngestionWorkflow → SinkEvent → DynamoDB (sink-events)
                                    ↓
                            DynamoDB Streams
                                    ↓
                    Lambda (SinkStreamHandler) → SinkPlugin (OpenSearch/S3)
```

- **트리거**: DynamoDB Streams
- **Lambda Handler**: `com.oliveyoung.ivmlite.apps.lambda.SinkStreamHandler`
- **특징**: SinkEvent 상태(PENDING→COMPLETED/FAILED) DynamoDB에 갱신

### 경로 B: SQS (버퍼링)

```
IngestionWorkflow → SinkEvent → SQS (ivm-sink-events)
                                    ↓
                    Lambda (SinkBatchHandler) [batchSize=500, batchWindow=60초]
                                    ↓
                    SinkPlugin (OpenSearch/S3)
```

- **트리거**: SQS (Event Source Mapping)
- **Lambda Handler**: `com.oliveyoung.ivmlite.apps.lambda.SinkBatchHandler`
- **특징**: 500건 또는 60초 모아서 벌크 처리, DynamoDB 미사용
- **활성화**: Runtime API에 `SQS_SINK_QUEUE_URL` 환경변수 설정 시 SqsSinkEventRepository 사용

---

## 2. Lambda 종류별 설정

### 2-1. SinkStreamHandler (DynamoDB Streams)

| 항목 | 값 |
|------|-----|
| **Handler** | `com.oliveyoung.ivmlite.apps.lambda.SinkStreamHandler` |
| **Runtime** | Java 17 (Corretto) |
| **Memory** | 512 MB (권장) |
| **Timeout** | 60 초 |
| **Event Source** | DynamoDB Streams (sink-events 테이블) |
| **Batch Size** | 10 |
| **Batching Window** | 5 초 |

**필수 환경변수**:

| 변수 | 설명 | 예시 |
|------|------|------|
| SINK_EVENT_TABLE | SinkEvent DynamoDB 테이블 | `ivm-sink-events-registry` |
| SINK_LEDGER_TABLE | SinkLedger 테이블 (멱등성) | `ivm-sink-ledger` |
| SINK_FAILURE_TABLE | 실패 레코드 테이블 | `ivm-sink-failures` |
| OPENSEARCH_ENDPOINT | OpenSearch 엔드포인트 | `https://search-xxx.es.amazonaws.com` |
| OPENSEARCH_USERNAME | Basic 인증 사용자 | `admin` |
| OPENSEARCH_PASSWORD | Basic 인증 비밀번호 | (Secret) |
| S3_BUCKET | (선택) S3 Sink 활성화 시 | `ivm-lite-sink-data-registry` |

### 2-2. SinkBatchHandler (SQS)

| 항목 | 값 |
|------|-----|
| **Handler** | `com.oliveyoung.ivmlite.apps.lambda.SinkBatchHandler` |
| **Runtime** | Java 17 (Corretto) |
| **Memory** | 1024 MB (권장, bulk 처리) |
| **Timeout** | 60 초 |
| **Event Source** | SQS (ivm-sink-events 큐) |
| **Batch Size** | 500 |
| **Batching Window** | 60 초 |

**필수 환경변수**: SinkStreamHandler와 동일 (SINK_EVENT_TABLE 제외, SQS는 DynamoDB 미사용)

### 2-3. IngestLambdaHandler (API Gateway)

| 항목 | 값 |
|------|-----|
| **Handler** | `com.oliveyoung.ivmlite.apps.lambda.IngestLambdaHandler` |
| **Runtime** | Java 17 (Corretto) |
| **Memory** | 1024 MB |
| **Timeout** | 30 초 |
| **Event Source** | API Gateway (Lambda Proxy) |

**필수 환경변수**: DB, DynamoDB, Contract 등 (별도 문서 참조)

---

## 3. SQS 설정

### 3-1. 큐 생성

```bash
aws sqs create-queue \
  --queue-name ivm-sink-events-{환경명} \
  --attributes '{
    "VisibilityTimeout": "300",
    "MessageRetentionPeriod": "1209600",
    "ReceiveMessageWaitTimeSeconds": "20"
  }'
```

| 속성 | 권장값 | 설명 |
|------|--------|------|
| VisibilityTimeout | 300 (5분) | Lambda 처리 시간 + 재시도 여유 |
| MessageRetentionPeriod | 1209600 (14일) | 메시지 보관 기간 |
| ReceiveMessageWaitTimeSeconds | 20 | Long Polling (비용 절감) |

### 3-2. Lambda Event Source Mapping

```bash
aws lambda create-event-source-mapping \
  --function-name ivm-sink-batch-handler \
  --event-source-arn arn:aws:sqs:ap-northeast-2:ACCOUNT_ID:ivm-sink-events-dev \
  --batch-size 500 \
  --maximum-batching-window-in-seconds 60
```

### 3-3. SQS 메시지 포맷 (SinkEventMessageDto)

Lambda가 기대하는 JSON 구조:

```json
{
  "id": "uuid",
  "jobId": "job-001",
  "idempotencyKey": "e2e_xxx_entityKey_version",
  "tenantId": "oliveyoung",
  "entityKey": "PRODUCT#oliveyoung:UA11279226",
  "version": 1,
  "viewType": "product-search",
  "payload": "{ \"CORE\": {...}, \"PRICE\": {...}, \"INDEX\": {...}, ... }",
  "sinkTargets": ["opensearch-sink"]
}
```

- **payload**: PRODUCT_SEARCH View JSON 문자열 (이스케이프된 JSON)
- **sinkTargets**: `opensearch-sink`, `s3-sink`, `personalize-sink` 등

### 3-4. Runtime API에서 SQS 사용

Runtime API(또는 Ingest API)에 다음 환경변수 설정 시 SinkEvent를 DynamoDB 대신 SQS로 전송:

```bash
export SQS_SINK_QUEUE_URL="https://sqs.ap-northeast-2.amazonaws.com/ACCOUNT_ID/ivm-sink-events-dev"
```

---

## 4. 환경 변수 전체 목록

### Lambda 공통 (SinkStreamHandler, SinkBatchHandler)

| 변수 | 필수 | 설명 | 기본값 |
|------|------|------|--------|
| AWS_REGION | N | AWS 리전 | `ap-northeast-2` |
| SINK_LEDGER_TABLE | Y | 멱등성 Ledger 테이블 | `ivm-sink-ledger` |
| SINK_FAILURE_TABLE | Y | 실패 레코드 테이블 | `ivm-sink-failures` |
| OPENSEARCH_ENDPOINT | Y* | OpenSearch 엔드포인트 | - |
| OPENSEARCH_USERNAME | N | Basic 인증 | - |
| OPENSEARCH_PASSWORD | N | Basic 인증 | - |
| OPENSEARCH_STATIC_WRITE_ALIAS | N | Static write alias | `ivm-products-{tenantId}__write` |
| OPENSEARCH_INDEX_PATTERN | N | alias 대체 | - |
| S3_BUCKET | N | S3 Sink 활성화 | - |
| PERSONALIZE_DATASET_ARN | N | Personalize 활성화 | - |
| TRACING_ENABLED | N | OpenTelemetry | `true` |
| OTEL_EXPORTER_OTLP_ENDPOINT | N | OTLP Collector | - |

*OPENSEARCH_ENDPOINT 없으면 OpenSearch Plugin 비활성화

### SinkStreamHandler 전용

| 변수 | 필수 | 설명 | 기본값 |
|------|------|------|--------|
| SINK_EVENT_TABLE | Y | SinkEvent DynamoDB 테이블 | `ivm-sink-events` |

### OpenSearch 인덱스 alias (opensearch-index-plan)

| 변수 | 설명 |
|------|------|
| OPENSEARCH_STATIC_WRITE_ALIAS | Static write (우선) |
| OPENSEARCH_INDEX_PATTERN | Static write (대체) |
| OPENSEARCH_INDEX | Static write (최종 fallback) |

---

## 5. IAM 권한

### SinkStreamHandler / SinkBatchHandler Lambda Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
        "dynamodb:BatchWriteItem"
      ],
      "Resource": [
        "arn:aws:dynamodb:*:*:table/ivm-sink-events-*",
        "arn:aws:dynamodb:*:*:table/ivm-sink-ledger",
        "arn:aws:dynamodb:*:*:table/ivm-sink-failures",
        "arn:aws:dynamodb:*:*:table/ivm-sink-events-*/index/*",
        "arn:aws:dynamodb:*:*:table/ivm-sink-failures/index/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetRecords",
        "dynamodb:GetShardIterator",
        "dynamodb:DescribeStream",
        "dynamodb:ListStreams"
      ],
      "Resource": "arn:aws:dynamodb:*:*:table/ivm-sink-events-*/stream/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:*:*:ivm-sink-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::ivm-lite-sink-data-*/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "es:ESHttpGet",
        "es:ESHttpPut",
        "es:ESHttpPost",
        "es:ESHttpDelete",
        "es:ESHttpHead"
      ],
      "Resource": "arn:aws:es:*:*:domain/ivm-opensearch-*/*"
    }
  ]
}
```

### Runtime API (SQS 전송 시)

```json
{
  "Effect": "Allow",
  "Action": ["sqs:SendMessage"],
  "Resource": "arn:aws:sqs:*:*:ivm-sink-*"
}
```

---

## 6. 인프라 (Terraform)

### 6-1. DynamoDB SinkEvents 스키마

| 속성 | 타입 | 용도 |
|------|------|------|
| PK | S | `SINK_EVENT#<id>` |
| SK | S | `VERSION#<timestamp>` |
| id | S | UUID |
| idempotencyKey | S | 멱등 키 |
| tenantId | S | 테넌트 |
| entityKey | S | `PRODUCT#tenant:uaCode` |
| version | N | 버전 |
| viewType | S | `product-search` |
| payload | S | View JSON |
| sinkTargets | SS | `["opensearch-sink"]` |
| status | S | PENDING/COMPLETED/FAILED |
| jobId | S | (선택) |
| GSI1_PK | S | `JOB#<jobId>` |
| GSI1_SK | S | `CREATED#<ts>` |
| GSI2_PK | S | `STATUS#<status>` |
| GSI2_SK | S | `CREATED#<ts>` |
| ttl | N | 7일 후 삭제 (epoch) |

### 6-2. Terraform 경로

- **DynamoDB Streams 경로**: `infra/terraform/preview/main.tf`
- **SQS 경로**: `infra/terraform/preview/` (S3 Sink용 preview 예시 있음)

### 6-3. SinkBatchHandler용 Terraform 예시

```hcl
# SQS 큐
resource "aws_sqs_queue" "sink_events" {
  name                       = "ivm-sink-events-${var.environment}"
  visibility_timeout_seconds  = 300
  message_retention_seconds  = 1209600
  receive_wait_time_seconds  = 20
}

# Lambda (SinkBatchHandler)
resource "aws_lambda_function" "sink_batch_handler" {
  function_name = "ivm-sink-batch-handler"
  handler       = "com.oliveyoung.ivmlite.apps.lambda.SinkBatchHandler"
  runtime       = "java17"
  s3_bucket     = aws_s3_bucket.lambda_deployments.id
  s3_key        = "ivm-ingest-lambda-1.0.0.jar"
  timeout       = 60
  memory_size   = 1024

  environment {
    variables = {
      SINK_LEDGER_TABLE       = aws_dynamodb_table.sink_ledger.name
      SINK_FAILURE_TABLE      = aws_dynamodb_table.sink_failures.name
      OPENSEARCH_ENDPOINT     = "https://${aws_opensearch_domain.main.endpoint}"
      OPENSEARCH_USERNAME     = var.opensearch_master_user
      OPENSEARCH_PASSWORD     = var.opensearch_master_password
    }
  }
}

# Event Source Mapping
resource "aws_lambda_event_source_mapping" "sink_batch_sqs" {
  event_source_arn                   = aws_sqs_queue.sink_events.arn
  function_name                      = aws_lambda_function.sink_batch_handler.function_name
  batch_size                         = 500
  maximum_batching_window_in_seconds  = 60
}
```

---

## 7. 빌드 & 배포

### 7-1. Lambda JAR 빌드

```bash
./gradlew shadowJar
# 결과: build/libs/ivm-ingest-lambda-1.0.0.jar
```

- **포함 Handler**: IngestLambdaHandler, SinkStreamHandler, SinkBatchHandler (동일 JAR)
- **Lambda별 Handler 클래스만 다르게 설정**

### 7-2. Lambda 배포 (AWS CLI)

```bash
# SinkStreamHandler
aws lambda update-function-code \
  --function-name ivm-sink-stream-handler \
  --zip-file fileb://build/libs/ivm-ingest-lambda-1.0.0.jar

# SinkBatchHandler
aws lambda update-function-code \
  --function-name ivm-sink-batch-handler \
  --zip-file fileb://build/libs/ivm-ingest-lambda-1.0.0.jar
```

### 7-3. S3 업로드 후 Terraform

```bash
aws s3 cp build/libs/ivm-ingest-lambda-1.0.0.jar \
  s3://ivm-lite-lambda-deployments-{region}/ivm-ingest-lambda-1.0.0.jar

cd infra/terraform/preview
terraform apply
```

---

## 8. 체크리스트

### Lambda (SinkStreamHandler)

- [ ] Handler: `com.oliveyoung.ivmlite.apps.lambda.SinkStreamHandler`
- [ ] Runtime: Java 17
- [ ] SINK_EVENT_TABLE, SINK_LEDGER_TABLE, SINK_FAILURE_TABLE 설정
- [ ] OPENSEARCH_ENDPOINT, USERNAME, PASSWORD 설정
- [ ] DynamoDB Streams Event Source Mapping (batchSize=10)
- [ ] IAM: DynamoDB Streams 읽기, DynamoDB 쓰기, OpenSearch, S3, Logs

### Lambda (SinkBatchHandler)

- [ ] Handler: `com.oliveyoung.ivmlite.apps.lambda.SinkBatchHandler`
- [ ] Runtime: Java 17
- [ ] SINK_LEDGER_TABLE, SINK_FAILURE_TABLE 설정
- [ ] OPENSEARCH_* 설정
- [ ] SQS Event Source Mapping (batchSize=500, batchWindow=60)
- [ ] IAM: SQS 수신/삭제, DynamoDB, OpenSearch, S3, Logs

### SQS

- [ ] 큐 생성 (ivm-sink-events-{env})
- [ ] VisibilityTimeout ≥ 300
- [ ] Lambda Event Source Mapping 연결
- [ ] Runtime API에 SQS_SINK_QUEUE_URL 설정 (SQS 경로 사용 시)

### OpenSearch

- [ ] 인덱스/alias 생성 (ivm-products-{tenantId}__write)
- [ ] Index template 적용 (index-template-static.v1.json)
- [ ] Lambda VPC/보안그룹에서 OpenSearch 접근 가능

---

## 참고 문서

- [LAMBDA-DEPLOYMENT.md](../LAMBDA-DEPLOYMENT.md) - Ingest Lambda 상세
- [opensearch-index-plan.md](opensearch-index-plan.md) - OpenSearch 환경변수 (15절)
- [sink-buffering-strategy.md](sink-buffering-strategy.md) - SQS 버퍼링 전략
- [DYNAMODB-STREAMS-FINAL.md](../DYNAMODB-STREAMS-FINAL.md) - DynamoDB Streams 상세
