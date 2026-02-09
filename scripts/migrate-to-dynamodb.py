#!/usr/bin/env python3
"""
PostgreSQL → DynamoDB 마이그레이션 스크립트
raw_data, slices, inverted_index 테이블을 DynamoDB로 이전
"""

import boto3
import psycopg2
import json
from datetime import datetime
from decimal import Decimal

# PostgreSQL 연결 설정
PG_HOST = "ivm-lite.crcikgmci55c.ap-northeast-2.rds.amazonaws.com"
PG_PORT = 5432
PG_DB = "ivmlite"
PG_USER = "postgres"
PG_PASSWORD = "Dhfflqmdud9("

# DynamoDB 설정
AWS_REGION = "ap-northeast-2"
DATA_TABLE = "ivm-lite-data"
SCHEMA_TABLE = "ivm-lite-schema-registry"

# AWS 자격 증명 (환경 변수에서 로드)
import os
AWS_ACCESS_KEY = os.getenv("AWS_ACCESS_KEY_ID", "YOUR_AWS_ACCESS_KEY_ID")
AWS_SECRET_KEY = os.getenv("AWS_SECRET_ACCESS_KEY", "YOUR_AWS_SECRET_ACCESS_KEY")

def get_pg_connection():
    """PostgreSQL 연결"""
    return psycopg2.connect(
        host=PG_HOST,
        port=PG_PORT,
        dbname=PG_DB,
        user=PG_USER,
        password=PG_PASSWORD
    )

def get_dynamodb():
    """DynamoDB 클라이언트"""
    return boto3.resource(
        'dynamodb',
        region_name=AWS_REGION,
        aws_access_key_id=AWS_ACCESS_KEY,
        aws_secret_access_key=AWS_SECRET_KEY
    )

def convert_to_dynamodb_format(obj):
    """Python 객체를 DynamoDB 형식으로 변환"""
    if isinstance(obj, dict):
        return {k: convert_to_dynamodb_format(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [convert_to_dynamodb_format(i) for i in obj]
    elif isinstance(obj, float):
        return Decimal(str(obj))
    elif isinstance(obj, datetime):
        return obj.isoformat()
    else:
        return obj

def migrate_raw_data():
    """raw_data 테이블 마이그레이션"""
    print("\n=== RawData 마이그레이션 시작 ===")
    
    conn = get_pg_connection()
    cur = conn.cursor()
    dynamodb = get_dynamodb()
    table = dynamodb.Table(DATA_TABLE)
    
    cur.execute("""
        SELECT tenant_id, entity_key, version, schema_id, schema_version, 
               content, content_hash, created_at
        FROM raw_data
    """)
    
    rows = cur.fetchall()
    print(f"마이그레이션할 raw_data: {len(rows)}건")
    
    for row in rows:
        tenant_id, entity_key, version, schema_id, schema_version, content, content_hash, created_at = row
        
        # DynamoDB 키 형식
        pk = f"TENANT#{tenant_id}#ENTITY#{entity_key}"
        sk = f"RAWDATA#v{version}"
        
        # content를 JSON 문자열로 변환
        payload_json = json.dumps(content, ensure_ascii=False) if content else '{}'
        
        item = {
            'PK': pk,
            'SK': sk,
            'tenant_id': tenant_id,
            'entity_key': entity_key,
            'version': version,
            'schema_id': schema_id,
            'schema_version': schema_version,
            'payload_json': payload_json,
            'payload_hash': content_hash or '',
            'created_at': created_at.isoformat() if created_at else '',
        }
        
        # Latest 마커도 추가
        latest_item = {
            'PK': pk,
            'SK': 'RAWDATA#LATEST',
            'tenant_id': tenant_id,
            'entity_key': entity_key,
            'version': version,
            'schema_id': schema_id,
            'schema_version': schema_version,
            'payload_json': payload_json,
            'payload_hash': content_hash or '',
            'created_at': created_at.isoformat() if created_at else '',
        }
        
        try:
            table.put_item(Item=item)
            table.put_item(Item=latest_item)
            print(f"  ✓ {tenant_id}/{entity_key} v{version}")
        except Exception as e:
            print(f"  ✗ {tenant_id}/{entity_key}: {e}")
    
    cur.close()
    conn.close()
    print(f"=== RawData 마이그레이션 완료: {len(rows)}건 ===\n")

def migrate_slices():
    """slices 테이블 마이그레이션"""
    print("\n=== Slices 마이그레이션 시작 ===")
    
    conn = get_pg_connection()
    cur = conn.cursor()
    dynamodb = get_dynamodb()
    table = dynamodb.Table(DATA_TABLE)
    
    cur.execute("""
        SELECT tenant_id, entity_key, slice_version, slice_type, 
               content, content_hash, created_at
        FROM slices
    """)
    
    rows = cur.fetchall()
    print(f"마이그레이션할 slices: {len(rows)}건")
    
    for row in rows:
        tenant_id, entity_key, version, slice_type, content, content_hash, created_at = row
        
        # DynamoDB 키 형식
        pk = f"TENANT#{tenant_id}#ENTITY#{entity_key}"
        sk = f"SLICE#v{version}#{slice_type}"
        
        # content를 JSON 문자열로 변환
        data_json = json.dumps(content, ensure_ascii=False) if content else '{}'
        
        item = {
            'PK': pk,
            'SK': sk,
            'tenant_id': tenant_id,
            'entity_key': entity_key,
            'version': version,
            'slice_type': slice_type,
            'data': data_json,
            'hash': content_hash or '',
            'created_at': created_at.isoformat() if created_at else '',
        }
        
        # Latest 마커도 추가
        latest_item = {
            'PK': pk,
            'SK': f'SLICE#LATEST#{slice_type}',
            'tenant_id': tenant_id,
            'entity_key': entity_key,
            'version': version,
            'slice_type': slice_type,
            'data': data_json,
            'hash': content_hash or '',
            'created_at': created_at.isoformat() if created_at else '',
        }
        
        try:
            table.put_item(Item=item)
            table.put_item(Item=latest_item)
            print(f"  ✓ {tenant_id}/{entity_key}/{slice_type} v{version}")
        except Exception as e:
            print(f"  ✗ {tenant_id}/{entity_key}/{slice_type}: {e}")
    
    cur.close()
    conn.close()
    print(f"=== Slices 마이그레이션 완료: {len(rows)}건 ===\n")

def migrate_inverted_index():
    """inverted_index 테이블 마이그레이션"""
    print("\n=== InvertedIndex 마이그레이션 시작 ===")
    
    conn = get_pg_connection()
    cur = conn.cursor()
    dynamodb = get_dynamodb()
    table = dynamodb.Table(DATA_TABLE)
    
    cur.execute("""
        SELECT tenant_id, index_type, index_value, entity_key, slice_type, slice_version
        FROM inverted_index
        WHERE index_type IS NOT NULL AND index_value IS NOT NULL
    """)
    
    rows = cur.fetchall()
    print(f"마이그레이션할 inverted_index: {len(rows)}건")
    
    for row in rows:
        tenant_id, index_type, index_value, entity_key, slice_type, version = row
        
        # DynamoDB 키 형식
        pk = f"TENANT#{tenant_id}#INDEX#{index_type}#{index_value}"
        sk = f"ENTITY#{entity_key}#SLICE#{slice_type or 'UNKNOWN'}"
        
        item = {
            'PK': pk,
            'SK': sk,
            'type': 'INVERTED_INDEX',
            'tenantId': tenant_id,
            'indexType': index_type or '',
            'indexValue': index_value or '',
            'entityKey': entity_key,
            'sliceType': slice_type or '',
            'version': version or 0,
        }
        
        try:
            table.put_item(Item=item)
            print(f"  ✓ {tenant_id}/{index_type}:{index_value} -> {entity_key}")
        except Exception as e:
            print(f"  ✗ {tenant_id}/{index_type}:{index_value}: {e}")
    
    cur.close()
    conn.close()
    print(f"=== InvertedIndex 마이그레이션 완료: {len(rows)}건 ===\n")

def migrate_contracts():
    """Contract YAML을 DynamoDB Schema Registry에 등록"""
    print("\n=== Contract 마이그레이션 시작 ===")
    
    import yaml
    import os
    
    dynamodb = get_dynamodb()
    table = dynamodb.Table(SCHEMA_TABLE)
    
    contracts_path = "/Users/mac/Documents/code-oyg-v2/ivm-lite-oliveyoung-full/src/main/resources/contracts/v1"
    
    if not os.path.exists(contracts_path):
        print(f"계약 디렉토리를 찾을 수 없습니다: {contracts_path}")
        return
    
    count = 0
    for filename in os.listdir(contracts_path):
        if filename.endswith('.yaml') or filename.endswith('.yml'):
            filepath = os.path.join(contracts_path, filename)
            
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
                try:
                    contract = yaml.safe_load(content)
                except:
                    print(f"  ✗ YAML 파싱 실패: {filename}")
                    continue
            
            if not contract:
                continue
                
            contract_id = contract.get('id') or contract.get('name') or filename.replace('.yaml', '').replace('.yml', '')
            kind = contract.get('kind', 'UNKNOWN')
            version = contract.get('version', '1.0.0')
            status = contract.get('status', 'ACTIVE')
            
            # DynamoDB 키 형식
            pk = f"CONTRACT#{contract_id}"
            sk = f"VERSION#{version}"
            
            item = {
                'PK': pk,
                'SK': sk,
                'kind': kind,
                'status': status,
                'id': contract_id,
                'version': version,
                'content': content,
                'contentJson': json.dumps(contract, ensure_ascii=False),
                'createdAt': datetime.now().isoformat(),
                'updatedAt': datetime.now().isoformat(),
            }
            
            # Latest 마커
            latest_item = {
                'PK': pk,
                'SK': 'LATEST',
                'kind': kind,
                'status': status,
                'id': contract_id,
                'latestVersion': version,
                'content': content,
                'contentJson': json.dumps(contract, ensure_ascii=False),
                'createdAt': datetime.now().isoformat(),
                'updatedAt': datetime.now().isoformat(),
            }
            
            try:
                table.put_item(Item=item)
                table.put_item(Item=latest_item)
                print(f"  ✓ {contract_id} ({kind}) v{version}")
                count += 1
            except Exception as e:
                print(f"  ✗ {contract_id}: {e}")
    
    print(f"=== Contract 마이그레이션 완료: {count}건 ===\n")

def main():
    print("=" * 60)
    print("PostgreSQL → DynamoDB 마이그레이션")
    print("=" * 60)
    
    # 1. Contract (스키마) 마이그레이션
    migrate_contracts()
    
    # 2. RawData 마이그레이션
    migrate_raw_data()
    
    # 3. Slices 마이그레이션
    migrate_slices()
    
    # 4. InvertedIndex 마이그레이션
    migrate_inverted_index()
    
    print("=" * 60)
    print("마이그레이션 완료!")
    print("=" * 60)

if __name__ == "__main__":
    main()
