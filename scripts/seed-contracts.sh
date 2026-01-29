#!/bin/bash
# DynamoDB에 계약 업로드 스크립트 (Flyway 스타일)
# Usage: ./scripts/seed-contracts.sh [--table TABLE_NAME] [--endpoint ENDPOINT] [--dry-run]

set -e

TABLE_NAME=${TABLE_NAME:-${DYNAMODB_TABLE:-}}
ENDPOINT=${ENDPOINT:-${DYNAMODB_ENDPOINT:-}}
CONTRACTS_DIR=${CONTRACTS_DIR:-src/main/resources/contracts/v1}

if [[ -z "$TABLE_NAME" ]]; then
  echo "ERROR: DynamoDB table name is required. Set DYNAMODB_TABLE (or TABLE_NAME) or pass --table." >&2
  exit 1
fi

echo "📦 Seeding contracts to DynamoDB..."
echo "   Table: $TABLE_NAME"
echo "   Endpoint: ${ENDPOINT:-"(AWS default)"}"
echo "   Directory: $CONTRACTS_DIR"
echo ""

ARGS="seed-contracts-to-dynamo --table $TABLE_NAME --dir $CONTRACTS_DIR"
if [[ -n "${ENDPOINT:-}" ]]; then
  ARGS="$ARGS --endpoint $ENDPOINT"
fi
./gradlew run --args="$ARGS $@"

echo ""
echo "✅ Done!"
