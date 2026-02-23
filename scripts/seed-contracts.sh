#!/bin/bash
# DynamoDB에 계약 업로드 스크립트 (Flyway 스타일)
# Usage: ./scripts/seed-contracts.sh [--table TABLE_NAME] [--profile PROFILE] [--endpoint ENDPOINT] [--dry-run]
#
# 예시:
#   ./scripts/seed-contracts.sh --table ivm-lite-schema-registry
#   AWS_PROFILE=qa-dev ./scripts/seed-contracts.sh --table ivm-lite-schema-registry-qa
#   ./scripts/seed-contracts.sh --table ivm-lite-schema-registry-qa --profile qa-dev

set -e

TABLE_NAME=${TABLE_NAME:-${DYNAMODB_TABLE:-}}
PROFILE=${PROFILE:-${AWS_PROFILE:-}}
ENDPOINT=${ENDPOINT:-${DYNAMODB_ENDPOINT:-}}
CONTRACTS_DIR=${CONTRACTS_DIR:-src/main/resources/contracts/v1}

# 인자 파싱 (--table, --profile, --endpoint, --dry-run)
EXTRA_ARGS=()
while [[ $# -gt 0 ]]; do
  case $1 in
    --table) TABLE_NAME="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --endpoint) ENDPOINT="$2"; shift 2 ;;
    --dry-run) EXTRA_ARGS+=("--dry-run"); shift ;;
    *) EXTRA_ARGS+=("$1"); shift ;;
  esac
done

if [[ -z "$TABLE_NAME" ]]; then
  echo "ERROR: DynamoDB table name is required. Set DYNAMODB_TABLE (or TABLE_NAME) or pass --table." >&2
  exit 1
fi

echo "📦 Seeding contracts to DynamoDB..."
echo "   Table: $TABLE_NAME"
echo "   Profile: ${PROFILE:-"(default)"}"
echo "   Endpoint: ${ENDPOINT:-"(AWS default)"}"
echo "   Directory: $CONTRACTS_DIR"
echo ""

ARGS="seed-contracts-to-dynamo --table $TABLE_NAME --dir $CONTRACTS_DIR"
[[ -n "$PROFILE" ]] && ARGS="$ARGS --profile $PROFILE"
[[ -n "$ENDPOINT" ]] && ARGS="$ARGS --endpoint $ENDPOINT"
./gradlew run --args="$ARGS ${EXTRA_ARGS[*]}"

echo ""
echo "✅ Done!"
