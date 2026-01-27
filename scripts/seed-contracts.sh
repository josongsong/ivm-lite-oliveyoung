#!/bin/bash
# DynamoDB에 계약 업로드 스크립트 (Flyway 스타일)
# Usage: ./scripts/seed-contracts.sh [--table TABLE_NAME] [--endpoint ENDPOINT] [--dry-run]

set -e

TABLE_NAME=${TABLE_NAME:-ivm-lite-schema-registry-local}
ENDPOINT=${ENDPOINT:-http://localhost:8000}
CONTRACTS_DIR=${CONTRACTS_DIR:-src/main/resources/contracts/v1}

echo "📦 Seeding contracts to DynamoDB..."
echo "   Table: $TABLE_NAME"
echo "   Endpoint: $ENDPOINT"
echo "   Directory: $CONTRACTS_DIR"
echo ""

./gradlew run --args="seed-contracts-to-dynamo --table $TABLE_NAME --dir $CONTRACTS_DIR --endpoint $ENDPOINT $@"

echo ""
echo "✅ Done!"
