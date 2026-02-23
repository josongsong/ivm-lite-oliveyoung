#!/bin/bash
# DynamoDB 데이터 확인 스크립트
# .env 로드 후 테이블 목록 및 데이터 건수 조회

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
SUFFIX="${TABLE_BASE##*-}"  # 마지막 - 이후
DATA_TABLE="ivm-lite-data-${SUFFIX}"
SINK_TABLE="ivm-sink-events-${SUFFIX}"

echo "=========================================="
echo "DynamoDB 데이터 확인"
echo "=========================================="
echo "Region: $REGION"
echo "Schema Table: $TABLE_BASE"
echo "Data Table: $DATA_TABLE"
echo "Sink Events Table: $SINK_TABLE"
echo ""

# AWS CLI 확인
if ! command -v aws &> /dev/null; then
    echo "❌ AWS CLI가 설치되어 있지 않습니다."
    exit 1
fi

# 1. 테이블 목록
echo "📋 1. 테이블 목록"
aws dynamodb list-tables --region "$REGION" --output table 2>/dev/null || { echo "연결 실패 (자격 증명 확인)"; exit 1; }
echo ""

# 2. 각 테이블 아이템 수
for TBL in "$TABLE_BASE" "$DATA_TABLE" "$SINK_TABLE"; do
    echo "📊 2. $TBL 아이템 수"
    if aws dynamodb describe-table --table-name "$TBL" --region "$REGION" &>/dev/null; then
        COUNT=$(aws dynamodb scan --table-name "$TBL" --region "$REGION" --select "COUNT" --output json 2>/dev/null | jq -r '.Count')
        echo "   → $COUNT 건"
    else
        echo "   → 테이블 없음"
    fi
    echo ""
done

# 3. Schema 테이블 샘플 (Contract)
echo "📄 3. Schema 테이블 샘플 (최대 5건)"
aws dynamodb scan --table-name "$TABLE_BASE" --region "$REGION" --max-items 5 --output json 2>/dev/null | jq -r '.Items[]? | "PK: \(.PK.S // .PK.N // "-") | SK: \(.SK.S // .SK.N // "-")"' 2>/dev/null || echo "   (조회 실패 또는 데이터 없음)"
echo ""

# 4. Data 테이블 샘플 (RawData/Slice)
echo "📄 4. Data 테이블 샘플 (최대 5건)"
if aws dynamodb describe-table --table-name "$DATA_TABLE" --region "$REGION" &>/dev/null; then
    aws dynamodb scan --table-name "$DATA_TABLE" --region "$REGION" --max-items 5 --output json 2>/dev/null | jq -r '.Items[]? | "PK: \(.PK.S // .PK.N // "-") | SK: \(.SK.S // .SK.N // "-")"' 2>/dev/null || echo "   (조회 실패)"
else
    echo "   (테이블 없음)"
fi
echo ""

echo "✅ 확인 완료"
