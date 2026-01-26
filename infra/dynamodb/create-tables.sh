#!/bin/bash
# DynamoDB Local - Schema Registry 테이블 생성
# Usage: ./create-tables.sh

set -e

DYNAMODB_ENDPOINT=${DYNAMODB_ENDPOINT:-http://localhost:8000}
AWS_REGION=${AWS_REGION:-ap-northeast-2}
TABLE_NAME="ivm-lite-schema-registry-local"

echo "⏳ Waiting for DynamoDB Local to be ready..."
until aws dynamodb list-tables --endpoint-url "$DYNAMODB_ENDPOINT" --region "$AWS_REGION" > /dev/null 2>&1; do
    echo "   DynamoDB not ready yet, retrying in 3s..."
    sleep 3
done

echo "✅ DynamoDB Local is ready!"

# 테이블 존재 여부 확인
if aws dynamodb describe-table --table-name "$TABLE_NAME" --endpoint-url "$DYNAMODB_ENDPOINT" --region "$AWS_REGION" > /dev/null 2>&1; then
    echo "ℹ️  Table '$TABLE_NAME' already exists. Skipping creation."
else
    echo "📦 Creating Schema Registry table: $TABLE_NAME"
    
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
            '[
                {
                    "IndexName": "kind-status-index",
                    "KeySchema": [
                        {"AttributeName": "kind", "KeyType": "HASH"},
                        {"AttributeName": "status", "KeyType": "RANGE"}
                    ],
                    "Projection": {"ProjectionType": "ALL"}
                }
            ]' \
        --billing-mode PAY_PER_REQUEST \
        --endpoint-url "$DYNAMODB_ENDPOINT" \
        --region "$AWS_REGION"
    
    echo "✅ Table created!"
fi

echo ""
echo "📊 Table status:"
aws dynamodb describe-table --table-name "$TABLE_NAME" --endpoint-url "$DYNAMODB_ENDPOINT" --region "$AWS_REGION" --query 'Table.{Name:TableName,Status:TableStatus,ItemCount:ItemCount}' --output table

echo ""
echo "🎉 Done! Schema Registry table is ready."
echo "   Endpoint: $DYNAMODB_ENDPOINT"
echo "   Table: $TABLE_NAME"
