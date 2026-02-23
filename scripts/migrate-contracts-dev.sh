#!/bin/bash
# Dev(QA) 환경 Contract 마이그레이션
#
# 1. schema-registry 테이블 생성 (없으면)
# 2. YAML Contract 시드
#
# Usage: ./scripts/migrate-contracts-dev.sh [--dry-run]
#
# 전제조건:
#   - aws sso login --profile qa-dev
#   - AWS_PROFILE=qa-dev 또는 --profile qa-dev

set -e

TABLE_NAME="${TABLE_NAME:-ivm-lite-schema-registry-qa}"
PROFILE="${AWS_PROFILE:-qa-dev}"
REGION="${AWS_REGION:-ap-northeast-2}"
DRY_RUN=""

for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && DRY_RUN="--dry-run"
done

echo "📦 Dev Contract 마이그레이션"
echo "   Table: $TABLE_NAME"
echo "   Profile: $PROFILE"
echo "   Region: $REGION"
echo ""

# 1. 테이블 존재 여부 확인
if aws dynamodb describe-table --table-name "$TABLE_NAME" --region "$REGION" --profile "$PROFILE" > /dev/null 2>&1; then
  echo "✅ Table '$TABLE_NAME' already exists."
else
  echo "📦 Creating Schema Registry table: $TABLE_NAME"
  if [[ -n "$DRY_RUN" ]]; then
    echo "   (DRY RUN - skipping table creation)"
  else
    aws dynamodb create-table \
      --table-name "$TABLE_NAME" \
      --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
        AttributeName=kind,AttributeType=S \
        AttributeName=status,AttributeType=S \
      --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
      --global-secondary-indexes \
        '[{
          "IndexName": "kind-status-index",
          "KeySchema": [
            {"AttributeName": "kind", "KeyType": "HASH"},
            {"AttributeName": "status", "KeyType": "RANGE"}
          ],
          "Projection": {"ProjectionType": "ALL"}
        }]' \
      --billing-mode PAY_PER_REQUEST \
      --region "$REGION" \
      --profile "$PROFILE"

    echo "⏳ Waiting for table to be active..."
    aws dynamodb wait table-exists --table-name "$TABLE_NAME" --region "$REGION" --profile "$PROFILE"
    echo "✅ Table created!"
  fi
fi

echo ""
echo "📦 Seeding contracts..."

# 2. Contract 시드
TABLE_NAME="$TABLE_NAME" ./scripts/seed-contracts.sh --table "$TABLE_NAME" --profile "$PROFILE" $DRY_RUN

echo ""
echo "🎉 Dev Contract 마이그레이션 완료!"
