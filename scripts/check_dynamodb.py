#!/usr/bin/env python3
import os
import boto3
from botocore.exceptions import ClientError

# DynamoDB 연결 (Remote-only: 기본은 AWS 엔드포인트, endpoint override는 opt-in)
endpoint = os.getenv("DYNAMODB_ENDPOINT", "")
region = os.getenv("AWS_REGION", "ap-northeast-2")

client_kwargs = {
    "service_name": "dynamodb",
    "region_name": region,
}
if endpoint:
    client_kwargs["endpoint_url"] = endpoint

dynamodb = boto3.client(**client_kwargs)

try:
    # 테이블 목록 확인
    print("📋 테이블 목록:")
    tables = dynamodb.list_tables()
    for table_name in tables.get('TableNames', []):
        print(f"  - {table_name}")
        
        # 각 테이블의 아이템 개수 확인
        response = dynamodb.scan(
            TableName=table_name,
            Select='COUNT'
        )
        print(f"    → 아이템 개수: {response['Count']}개")
        
        # 실제 데이터 일부 확인 (최대 5개)
        if response['Count'] > 0:
            items = dynamodb.scan(TableName=table_name, Limit=5)
            print(f"    → 저장된 데이터 예시:")
            for item in items.get('Items', [])[:5]:
                pk = item.get('PK', {}).get('S', '')
                sk = item.get('SK', {}).get('S', '')
                print(f"       • PK={pk}, SK={sk}")
    
    if not tables.get('TableNames'):
        print("  ⚠️  테이블이 없습니다.")
        
except ClientError as e:
    print(f"❌ 오류: {e}")
except Exception as e:
    print(f"❌ 연결 실패: {e}")
