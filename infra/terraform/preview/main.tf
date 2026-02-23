# =============================================================================
# IVM-Lite Sink Infrastructure (DynamoDB Streams → Lambda → SinkPlugin)
#
# 아키텍처:
#   DynamoDB SinkEvents (Streams) → Lambda (SinkStreamHandler) → S3 / OpenSearch
#
# 리소스:
#   - DynamoDB x3: sink-events, sink-ledger, sink-failures
#   - S3 x2: sink-data (뷰 저장), lambda-deployments (JAR 배포)
#   - Lambda x1: SinkStreamHandler (DynamoDB Streams 트리거)
#   - OpenSearch x1: 검색 인덱스
#   - IAM Role + Policy
#   - Event Source Mapping (DynamoDB Streams → Lambda)
#   - CloudWatch Log Group
# =============================================================================

terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# =============================================================================
# Variables
# =============================================================================

variable "aws_region" {
  description = "AWS Region"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "Environment name (테이블/버킷 suffix)"
  type        = string
  default     = "registry"
}

variable "lambda_s3_key" {
  description = "S3 key for Lambda JAR"
  type        = string
  default     = "ivm-ingest-lambda-1.0.0.jar"
}

variable "opensearch_instance_type" {
  description = "OpenSearch instance type"
  type        = string
  default     = "t3.small.search"
}

variable "opensearch_instance_count" {
  description = "OpenSearch node count"
  type        = number
  default     = 1
}

variable "opensearch_volume_size" {
  description = "OpenSearch EBS volume size (GB)"
  type        = number
  default     = 20
}

variable "opensearch_master_user" {
  description = "OpenSearch master username"
  type        = string
  default     = "admin"
}

variable "opensearch_master_password" {
  description = "OpenSearch master password"
  type        = string
  sensitive   = true
}

# =============================================================================
# DynamoDB Tables
# =============================================================================

# SinkEvents - DynamoDB Streams → Lambda 트리거
resource "aws_dynamodb_table" "sink_events" {
  name         = "ivm-sink-events-${var.environment}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"

  ttl {
    enabled        = true
    attribute_name = "ttl"
  }

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "GSI1_PK"
    type = "S"
  }
  attribute {
    name = "GSI1_SK"
    type = "S"
  }
  attribute {
    name = "GSI2_PK"
    type = "S"
  }
  attribute {
    name = "GSI2_SK"
    type = "S"
  }

  # GSI1: jobId 조회 (JOB#<jobId>)
  global_secondary_index {
    name            = "GSI1"
    hash_key        = "GSI1_PK"
    range_key       = "GSI1_SK"
    projection_type = "ALL"
  }

  # GSI2: status 조회 (STATUS#<status>)
  global_secondary_index {
    name            = "GSI2"
    hash_key        = "GSI2_PK"
    range_key       = "GSI2_SK"
    projection_type = "ALL"
  }

  tags = {
    Name        = "ivm-sink-events-${var.environment}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# SinkLedger - 멱등성 보장 (RFC-020 R4)
resource "aws_dynamodb_table" "sink_ledger" {
  name         = "ivm-sink-ledger"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"

  ttl {
    enabled        = true
    attribute_name = "ttl"
  }

  attribute {
    name = "PK"
    type = "S"
  }

  tags = {
    Name        = "ivm-sink-ledger"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# SinkFailures - 실패 레코드 저장 (RFC-020 R3)
resource "aws_dynamodb_table" "sink_failures" {
  name         = "ivm-sink-failures"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  ttl {
    enabled        = true
    attribute_name = "ttl"
  }

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "GSI1_PK"
    type = "S"
  }
  attribute {
    name = "GSI1_SK"
    type = "S"
  }

  # GSI1: target별 실패 조회 (TARGET#<target>)
  global_secondary_index {
    name            = "GSI1"
    hash_key        = "GSI1_PK"
    range_key       = "GSI1_SK"
    projection_type = "ALL"
  }

  tags = {
    Name        = "ivm-sink-failures"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# =============================================================================
# S3 Buckets
# =============================================================================

# Sink 데이터 저장 (View JSON)
resource "aws_s3_bucket" "sink_data" {
  bucket = "ivm-lite-sink-data-${var.environment}"

  tags = {
    Name        = "ivm-lite-sink-data-${var.environment}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_s3_bucket_versioning" "sink_data" {
  bucket = aws_s3_bucket.sink_data.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "sink_data" {
  bucket = aws_s3_bucket.sink_data.id

  rule {
    id     = "delete-old-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

# Lambda JAR 배포용
resource "aws_s3_bucket" "lambda_deployments" {
  bucket = "ivm-lite-lambda-deployments-${var.aws_region}"

  tags = {
    Name        = "ivm-lite-lambda-deployments-${var.aws_region}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_s3_bucket_versioning" "lambda_deployments" {
  bucket = aws_s3_bucket.lambda_deployments.id
  versioning_configuration {
    status = "Enabled"
  }
}

# =============================================================================
# IAM Role + Policy
# =============================================================================

resource "aws_iam_role" "sink_lambda" {
  name = "ivm-sink-stream-lambda-role"

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

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_role_policy" "sink_lambda" {
  name = "ivm-sink-stream-lambda-policy"
  role = aws_iam_role.sink_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # DynamoDB - 3개 테이블 CRUD
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:BatchWriteItem"
        ]
        Resource = [
          aws_dynamodb_table.sink_events.arn,
          "${aws_dynamodb_table.sink_events.arn}/index/*",
          aws_dynamodb_table.sink_ledger.arn,
          aws_dynamodb_table.sink_failures.arn,
          "${aws_dynamodb_table.sink_failures.arn}/index/*"
        ]
      },
      # DynamoDB Streams - sink-events 읽기
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetRecords",
          "dynamodb:GetShardIterator",
          "dynamodb:DescribeStream",
          "dynamodb:ListStreams"
        ]
        Resource = "${aws_dynamodb_table.sink_events.arn}/stream/*"
      },
      # S3 - Sink 데이터 저장/삭제
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.sink_data.arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.sink_data.arn
      },
      # CloudWatch Logs
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:*:*"
      },
      # OpenSearch - HTTP 접근
      {
        Effect = "Allow"
        Action = [
          "es:ESHttpGet",
          "es:ESHttpPut",
          "es:ESHttpPost",
          "es:ESHttpDelete",
          "es:ESHttpHead"
        ]
        Resource = "${aws_opensearch_domain.main.arn}/*"
      }
    ]
  })
}

# =============================================================================
# Lambda Function
# =============================================================================

resource "aws_lambda_function" "sink_stream_handler" {
  function_name = "ivm-sink-stream-handler"
  handler       = "com.oliveyoung.ivmlite.apps.lambda.SinkStreamHandler"
  runtime       = "java17"
  role          = aws_iam_role.sink_lambda.arn

  s3_bucket = aws_s3_bucket.lambda_deployments.id
  s3_key    = var.lambda_s3_key

  timeout     = 60
  memory_size = 512

  environment {
    variables = {
      SINK_EVENT_TABLE   = aws_dynamodb_table.sink_events.name
      SINK_LEDGER_TABLE  = aws_dynamodb_table.sink_ledger.name
      SINK_FAILURE_TABLE = aws_dynamodb_table.sink_failures.name
      S3_BUCKET          = aws_s3_bucket.sink_data.id
      OPENSEARCH_ENDPOINT  = "https://${aws_opensearch_domain.main.endpoint}"
      OPENSEARCH_USERNAME  = var.opensearch_master_user
      OPENSEARCH_PASSWORD  = var.opensearch_master_password
    }
  }

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  depends_on = [aws_cloudwatch_log_group.sink_lambda]
}

# =============================================================================
# Event Source Mapping (DynamoDB Streams → Lambda)
# =============================================================================

resource "aws_lambda_event_source_mapping" "sink_stream_trigger" {
  event_source_arn  = aws_dynamodb_table.sink_events.stream_arn
  function_name     = aws_lambda_function.sink_stream_handler.arn
  starting_position = "LATEST"
  batch_size        = 10
  enabled           = true

  maximum_batching_window_in_seconds = 5
  maximum_retry_attempts             = 3
  bisect_batch_on_function_error     = true
}

# =============================================================================
# CloudWatch Log Group
# =============================================================================

resource "aws_cloudwatch_log_group" "sink_lambda" {
  name              = "/aws/lambda/ivm-sink-stream-handler"
  retention_in_days = 14

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# =============================================================================
# OpenSearch Service
# =============================================================================

resource "aws_opensearch_domain" "main" {
  domain_name    = "ivm-opensearch-${var.environment}"
  engine_version = "OpenSearch_2.11"

  cluster_config {
    instance_type  = var.opensearch_instance_type
    instance_count = var.opensearch_instance_count
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.opensearch_volume_size
  }

  encrypt_at_rest {
    enabled = true
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-2019-07"
  }

  advanced_security_options {
    enabled                        = true
    internal_user_database_enabled = true

    master_user_options {
      master_user_name     = var.opensearch_master_user
      master_user_password = var.opensearch_master_password
    }
  }

  # FGAC 활성화 상태 → 리소스 정책은 open, 인증/인가는 Internal User DB에서 관리
  access_policies = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { AWS = "*" }
        Action    = "es:*"
        Resource  = "arn:aws:es:${var.aws_region}:*:domain/ivm-opensearch-${var.environment}/*"
      }
    ]
  })

  tags = {
    Name        = "ivm-opensearch-${var.environment}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# =============================================================================
# Outputs
# =============================================================================

output "sink_events_table_name" {
  value       = aws_dynamodb_table.sink_events.name
  description = "SinkEvents DynamoDB table name"
}

output "sink_events_stream_arn" {
  value       = aws_dynamodb_table.sink_events.stream_arn
  description = "SinkEvents DynamoDB Streams ARN"
}

output "sink_ledger_table_name" {
  value       = aws_dynamodb_table.sink_ledger.name
  description = "SinkLedger DynamoDB table name"
}

output "sink_failures_table_name" {
  value       = aws_dynamodb_table.sink_failures.name
  description = "SinkFailures DynamoDB table name"
}

output "sink_data_bucket" {
  value       = aws_s3_bucket.sink_data.id
  description = "S3 bucket for sink data"
}

output "lambda_deployments_bucket" {
  value       = aws_s3_bucket.lambda_deployments.id
  description = "S3 bucket for Lambda JAR deployments"
}

output "lambda_function_name" {
  value       = aws_lambda_function.sink_stream_handler.function_name
  description = "Lambda function name"
}

output "lambda_function_arn" {
  value       = aws_lambda_function.sink_stream_handler.arn
  description = "Lambda function ARN"
}

output "opensearch_endpoint" {
  value       = aws_opensearch_domain.main.endpoint
  description = "OpenSearch domain endpoint"
}

output "opensearch_dashboard_endpoint" {
  value       = aws_opensearch_domain.main.dashboard_endpoint
  description = "OpenSearch Dashboards endpoint"
}
