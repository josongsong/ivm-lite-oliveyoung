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
    logs       = "http://localhost:4566"
  }
}

# SQS Queue (S3 Sink용)
resource "aws_sqs_queue" "s3_sink_local" {
  name                       = "local-s3-sink-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 86400  # 1일

  tags = {
    Environment = "local"
    SinkType    = "s3"
  }
}

# S3 Bucket
resource "aws_s3_bucket" "sink_data_local" {
  bucket = "local-ivm-lite-sink-data"

  tags = {
    Environment = "local"
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

# IAM Policy (S3 + SQS 권한)
resource "aws_iam_role_policy" "lambda_policy_local" {
  name = "local-lambda-policy"
  role = aws_iam_role.lambda_exec_local.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject"
        ]
        Resource = "${aws_s3_bucket.sink_data_local.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.s3_sink_local.arn
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "*"
      }
    ]
  })
}

# Lambda Function (LocalStack용)
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

# Lambda SQS Event Source Mapping
resource "aws_lambda_event_source_mapping" "s3_sink_sqs_trigger" {
  event_source_arn = aws_sqs_queue.s3_sink_local.arn
  function_name    = aws_lambda_function.s3_sink_local.arn
  batch_size       = 10
  enabled          = true

  function_response_types = ["ReportBatchItemFailures"]
}

# Outputs
output "s3_sink_queue_url" {
  value       = aws_sqs_queue.s3_sink_local.url
  description = "S3 Sink SQS Queue URL"
}

output "s3_bucket_name" {
  value       = aws_s3_bucket.sink_data_local.id
  description = "S3 Bucket name"
}

output "lambda_function_name" {
  value       = aws_lambda_function.s3_sink_local.function_name
  description = "Lambda function name"
}
