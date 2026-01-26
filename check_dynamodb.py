#!/usr/bin/env python3
import boto3
from botocore.exceptions import ClientError

# DynamoDB Local 연결
dynamodb = boto3.client(
    'dynamodb',
    endpoint_url='http://localhost:8000',
    region_name='ap-northeast-2',
    aws_access_key_id='dummy',
    aws_secret_access_key='dummy'
)

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
