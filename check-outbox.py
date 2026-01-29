#!/usr/bin/env python3
"""
Outbox 테이블 조회 스크립트

실제 PostgreSQL outbox 테이블에 쌓인 데이터를 확인합니다.
"""

import os
import sys
import psycopg2
from psycopg2.extras import RealDictCursor
from datetime import datetime
import json

def parse_jdbc_url(jdbc_url):
    """JDBC URL을 파싱하여 psycopg2 연결 파라미터 추출"""
    import re
    # jdbc:postgresql://host:port/database?params
    pattern = r'jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)(?:\?(.+))?'
    match = re.match(pattern, jdbc_url)
    if match:
        host = match.group(1)
        port = int(match.group(2)) if match.group(2) else 5432
        database = match.group(3)
        params = match.group(4) or ""
        return host, port, database, params
    return None, None, None, None

def load_env_file():
    """프로젝트 루트의 .env 파일 로드"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir) if script_dir.endswith('scripts') else script_dir
    env_file = os.path.join(project_root, '.env')
    
    if os.path.exists(env_file):
        with open(env_file, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    # export 제거
                    key = key.replace('export ', '').strip()
                    value = value.strip().strip('"').strip("'")
                    os.environ[key] = value
        return True
    return False

def main():
    # .env 파일 로드 시도
    env_loaded = load_env_file()
    if env_loaded:
        print("✅ .env 파일 로드 완료\n")
    
    # 환경 변수에서 DB 연결 정보 가져오기
    db_url = os.getenv("DB_URL")
    db_user = os.getenv("DB_USER")
    db_password = os.getenv("DB_PASSWORD")
    
    if not db_url:
        print("❌ DB_URL 환경 변수가 설정되지 않았습니다.")
        print("예: export DB_URL=jdbc:postgresql://host:5432/dbname")
        print("또는: .env 파일에 DB_URL을 설정하세요")
        sys.exit(1)
    
    if not db_user:
        print("⚠️  DB_USER가 설정되지 않았습니다. 기본값 'postgres' 사용")
        db_user = "postgres"
    
    if not db_password:
        print("⚠️  DB_PASSWORD가 설정되지 않았습니다.")
        print("환경 변수나 .env 파일에 DB_PASSWORD를 설정하세요.")
        sys.exit(1)
    
    # JDBC URL 파싱 또는 직접 PostgreSQL URL
    if db_url.startswith("jdbc:postgresql://"):
        host, port, database, params = parse_jdbc_url(db_url)
        if not host:
            print(f"❌ DB_URL 파싱 실패: {db_url}")
            sys.exit(1)
        connect_params = {
            "host": host,
            "port": port,
            "database": database,
            "user": db_user,
            "password": db_password
        }
    elif db_url.startswith("postgresql://"):
        # 직접 PostgreSQL URL 형식 파싱
        # postgresql://user:password@host:port/database
        import urllib.parse
        parsed = urllib.parse.urlparse(db_url)
        connect_params = {
            "host": parsed.hostname or "localhost",
            "port": parsed.port or 5432,
            "database": parsed.path.lstrip("/"),
            "user": parsed.username or db_user or "postgres",
            "password": parsed.password or db_password or ""
        }
    else:
        # 직접 연결 문자열 (key=value 형식)
        connect_params = None
        connect_string = db_url
    
    # DB 연결
    try:
        if connect_params:
            conn = psycopg2.connect(**connect_params)
        else:
            conn = psycopg2.connect(connect_string)
        print("✅ PostgreSQL 연결 성공\n")
    except Exception as e:
        print(f"❌ PostgreSQL 연결 실패: {e}")
        print(f"\n연결 정보:")
        print(f"  DB_URL: {db_url[:50]}...")
        print(f"  DB_USER: {db_user}")
        print(f"\n로컬 PostgreSQL 확인:")
        print("  docker-compose up -d postgres")
        print("  또는 실제 DB 연결 정보를 확인하세요.")
        sys.exit(1)
    
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            # 전체 통계
            cur.execute("""
                SELECT 
                    status,
                    COUNT(*) as count,
                    MIN(created_at) as oldest,
                    MAX(created_at) as newest
                FROM outbox
                GROUP BY status
                ORDER BY status
            """)
            stats = cur.fetchall()
            
            print("=" * 80)
            print("📊 Outbox 통계")
            print("=" * 80)
            if stats:
                for row in stats:
                    print(f"  {row['status']:12} | {row['count']:6}개 | 최신: {row['newest']}")
                print()
            else:
                print("  Outbox에 데이터가 없습니다.\n")
            
            # PENDING 상태 상세 조회
            cur.execute("""
                SELECT 
                    id,
                    aggregatetype,
                    aggregateid,
                    type,
                    status,
                    created_at,
                    processed_at,
                    retry_count,
                    failure_reason
                FROM outbox
                WHERE status = 'PENDING'
                ORDER BY created_at ASC
                LIMIT 20
            """)
            pending = cur.fetchall()
            
            print("=" * 80)
            print(f"⏳ PENDING 상태 ({len(pending)}개, 최대 20개 표시)")
            print("=" * 80)
            if pending:
                for row in pending:
                    print(f"\n  ID: {row['id']}")
                    print(f"  AggregateType: {row['aggregatetype']}")
                    print(f"  AggregateID: {row['aggregateid']}")
                    print(f"  EventType: {row['type']}")
                    print(f"  CreatedAt: {row['created_at']}")
                    print(f"  RetryCount: {row['retry_count']}")
                    if row['failure_reason']:
                        print(f"  FailureReason: {row['failure_reason']}")
            else:
                print("  PENDING 상태의 엔트리가 없습니다.")
            
            print("\n")
            
            # PROCESSED 상태 최근 10개
            cur.execute("""
                SELECT 
                    id,
                    aggregatetype,
                    aggregateid,
                    type,
                    created_at,
                    processed_at,
                    retry_count
                FROM outbox
                WHERE status = 'PROCESSED'
                ORDER BY processed_at DESC
                LIMIT 10
            """)
            processed = cur.fetchall()
            
            print("=" * 80)
            print(f"✅ PROCESSED 상태 (최근 10개)")
            print("=" * 80)
            if processed:
                for row in processed:
                    print(f"\n  {row['type']:30} | {row['aggregatetype']:15} | {row['aggregateid']:40}")
                    print(f"  Created: {row['created_at']} | Processed: {row['processed_at']}")
            else:
                print("  PROCESSED 상태의 엔트리가 없습니다.")
            
            print("\n")
            
            # FAILED 상태
            cur.execute("""
                SELECT 
                    id,
                    aggregatetype,
                    aggregateid,
                    type,
                    created_at,
                    retry_count,
                    failure_reason
                FROM outbox
                WHERE status = 'FAILED'
                ORDER BY created_at DESC
                LIMIT 10
            """)
            failed = cur.fetchall()
            
            print("=" * 80)
            print(f"❌ FAILED 상태 (최근 10개)")
            print("=" * 80)
            if failed:
                for row in failed:
                    print(f"\n  {row['type']:30} | {row['aggregatetype']:15} | {row['aggregateid']:40}")
                    print(f"  Created: {row['created_at']} | RetryCount: {row['retry_count']}")
                    print(f"  Reason: {row['failure_reason']}")
            else:
                print("  FAILED 상태의 엔트리가 없습니다.")
            
            print("\n")
            
            # AggregateType별 통계
            cur.execute("""
                SELECT 
                    aggregatetype,
                    status,
                    COUNT(*) as count
                FROM outbox
                GROUP BY aggregatetype, status
                ORDER BY aggregatetype, status
            """)
            by_type = cur.fetchall()
            
            print("=" * 80)
            print("📋 AggregateType별 통계")
            print("=" * 80)
            if by_type:
                current_type = None
                for row in by_type:
                    if current_type != row['aggregatetype']:
                        if current_type is not None:
                            print()
                        current_type = row['aggregatetype']
                        print(f"  {row['aggregatetype']}:")
                    print(f"    {row['status']:12} | {row['count']:6}개")
                print()
            else:
                print("  데이터가 없습니다.\n")
            
    except Exception as e:
        print(f"❌ 쿼리 실행 실패: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        conn.close()

if __name__ == "__main__":
    main()
