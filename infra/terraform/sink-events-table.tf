# DynamoDB SinkEvents Table (DynamoDB Streams 기반)
#
# 용도: Sink 이벤트 저장 및 Lambda 자동 트리거
# Streams: Lambda가 실시간으로 이벤트 처리

resource "aws_dynamodb_table" "sink_events" {
  name           = "ivm-sink-events-${var.environment}"
  billing_mode   = "PAY_PER_REQUEST"  # On-demand (Auto Scaling)
  hash_key       = "PK"
  range_key      = "SK"

  # TTL 활성화 (7일 후 자동 삭제)
  ttl {
    enabled        = true
    attribute_name = "ttl"
  }

  # DynamoDB Streams 활성화 (Lambda 트리거)
  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"

  # Primary Key
  attribute {
    name = "PK"
    type = "S"
  }

  attribute {
    name = "SK"
    type = "S"
  }

  # GSI1: jobId 조회용
  attribute {
    name = "GSI1_PK"
    type = "S"
  }

  attribute {
    name = "GSI1_SK"
    type = "S"
  }

  global_secondary_index {
    name            = "GSI1"
    hash_key        = "GSI1_PK"
    range_key       = "GSI1_SK"
    projection_type = "ALL"
  }

  # GSI2: status 조회용 (Admin UI)
  attribute {
    name = "GSI2_PK"
    type = "S"
  }

  attribute {
    name = "GSI2_SK"
    type = "S"
  }

  global_secondary_index {
    name            = "GSI2"
    hash_key        = "GSI2_PK"
    range_key       = "GSI2_SK"
    projection_type = "ALL"
  }

  tags = {
    Name        = "ivm-sink-events-${var.environment}"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Streams ARN 출력 (Lambda Event Source Mapping용)
output "sink_events_stream_arn" {
  value = aws_dynamodb_table.sink_events.stream_arn
}
