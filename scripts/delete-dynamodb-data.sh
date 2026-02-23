#!/bin/bash
# DynamoDB 테이블 내 모든 아이템 삭제 (테이블 구조는 유지)
# .env 로드 후 실행

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# .env 로드 (있으면)
ENV_FILE="$PROJECT_ROOT/.env"
if [[ -f "$ENV_FILE" ]]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

REGION="${AWS_REGION:-ap-northeast-2}"
TABLE_BASE="${DYNAMODB_TABLE:-ivm-lite-schema-registry}"
SUFFIX="${TABLE_BASE##*-}"
DATA_TABLE="ivm-lite-data-${SUFFIX}"
SINK_TABLE="ivm-sink-events-${SUFFIX}"

delete_table_items() {
    local TABLE="$1"
    local DELETED=0

    echo "🗑️  $TABLE 삭제 중..."

    while true; do
        SCAN=$(aws dynamodb scan \
            --table-name "$TABLE" \
            --region "$REGION" \
            --projection-expression "PK, SK" \
            --max-items 25 \
            --output json 2>/dev/null)

        ITEMS=$(echo "$SCAN" | jq -c '.Items')
        COUNT=$(echo "$ITEMS" | jq 'length')

        [[ "$COUNT" -eq 0 ]] && break

        # BatchWriteItem 요청 생성 (DeleteRequest)
        REQUEST=$(echo "$ITEMS" | jq -c --arg tbl "$TABLE" '
            [.[] | {"DeleteRequest": {"Key": .}}]
            | {($tbl): .}
        ')
        TMPFILE=$(mktemp)
        echo "$REQUEST" > "$TMPFILE"
        aws dynamodb batch-write-item --request-items "file://$TMPFILE" --region "$REGION" --output text 2>/dev/null
        rm -f "$TMPFILE"

        DELETED=$((DELETED + COUNT))
        echo "   → $COUNT 건 삭제 (누적: $DELETED)"

        [[ "$COUNT" -lt 25 ]] && break
    done

    echo "   ✅ 완료: $DELETED 건"
}

echo "=========================================="
echo "DynamoDB 데이터 삭제"
echo "=========================================="
echo "Region: $REGION"
echo "Schema: $TABLE_BASE"
echo "Data: $DATA_TABLE"
echo "SinkEvents: $SINK_TABLE"
echo ""

if ! command -v aws &> /dev/null; then
    echo "❌ AWS CLI 필요"
    exit 1
fi
if ! command -v jq &> /dev/null; then
    echo "❌ jq 필요 (brew install jq)"
    exit 1
fi

delete_table_items "$TABLE_BASE"
echo ""
delete_table_items "$DATA_TABLE"
echo ""
delete_table_items "$SINK_TABLE"
echo ""

echo "✅ 전체 삭제 완료"
