#!/usr/bin/env python3
"""
원격 rawdata DB의 raw 상품 데이터 확인 스크립트

크롤러와 동일한 환경 변수 사용:
- RAWDATA_PGHOST 또는 PGHOST: PostgreSQL 호스트
- RAWDATA_PGPORT 또는 PGPORT: 포트 (기본: 5432)
- RAWDATA_PGUSER 또는 PGUSER: 사용자명
- RAWDATA_PGPASSWORD 또는 PGPASSWORD: 비밀번호
- RAWDATA_PGDATABASE 또는 PGDATABASE: 데이터베이스명 (기본: rawdata)

또는 ivmlite DB_URL에서 자동 추출 (같은 호스트 사용):
- DB_URL, DB_USER, DB_PASSWORD가 설정되어 있으면 자동으로 rawdata DB 연결에 사용

사용 예:
  # 방법 1: 직접 환경 변수 설정
  export RAWDATA_PGHOST=your-db.xxxxx.ap-northeast-2.rds.amazonaws.com
  export RAWDATA_PGUSER=your_user
  export RAWDATA_PGPASSWORD=your_password
  python3 scripts/check-rawdata-products.py
  
  # 방법 2: .env 파일에서 DB_URL 사용 (ivmlite와 같은 호스트)
  # .env에 DB_URL, DB_USER, DB_PASSWORD가 있으면 자동으로 rawdata DB에 연결
  python3 scripts/check-rawdata-products.py
"""

import os
import sys
from datetime import datetime
from pathlib import Path
import re

def parse_jdbc_url(url: str):
    """jdbc:postgresql://host:port/db?params 형식 파싱"""
    if not url.startswith("jdbc:postgresql://"):
        return None
    
    url_part = url.replace("jdbc:postgresql://", "")
    if "?" in url_part:
        url_part = url_part.split("?")[0]
    
    parts = url_part.split("/")
    if len(parts) < 1:
        return None
    
    host_port = parts[0]
    if ":" in host_port:
        host, port = host_port.split(":")
        return {"host": host, "port": port}
    else:
        return {"host": host_port, "port": "5432"}

# .env 파일 로드 (프로젝트 루트)
_project_root = Path(__file__).resolve().parent.parent
env_file = _project_root / ".env"
if env_file.exists():
    with open(env_file, "r") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                # export 키워드 제거
                line = re.sub(r'^\s*export\s+', '', line)
                if "=" not in line:
                    continue
                    
                key, value = line.split("=", 1)
                key = key.strip()
                # 따옴표 제거
                value = value.strip().strip('"').strip("'")
                
                # 환경 변수에 설정 (이미 있으면 덮어쓰지 않음)
                if not os.getenv(key):
                    os.environ[key] = value

# DB_URL에서 rawdata DB 정보 추출 (ivmlite와 같은 호스트 사용)
db_url = os.getenv("DB_URL")
if db_url:
    db_info = parse_jdbc_url(db_url)
    if db_info:
        if not os.getenv("RAWDATA_PGHOST"):
            os.environ["RAWDATA_PGHOST"] = db_info["host"]
        if not os.getenv("RAWDATA_PGPORT"):
            os.environ["RAWDATA_PGPORT"] = db_info["port"]

# DB_USER, DB_PASSWORD도 rawdata용으로 복사 (없는 경우만)
if not os.getenv("RAWDATA_PGUSER") and os.getenv("DB_USER"):
    os.environ["RAWDATA_PGUSER"] = os.getenv("DB_USER")
if not os.getenv("RAWDATA_PGPASSWORD") and os.getenv("DB_PASSWORD"):
    os.environ["RAWDATA_PGPASSWORD"] = os.getenv("DB_PASSWORD")

# tools/crawler 경로를 sys.path에 추가
sys.path.insert(0, str(_project_root / "tools" / "crawler"))

from rawdata_db import connect_pg, get_pg_config


def format_number(num: int) -> str:
    """숫자를 읽기 쉬운 형식으로 포맷"""
    return f"{num:,}"


def check_rawdata_products():
    """rawdata DB의 상품 데이터 통계 확인"""
    try:
        # 환경 변수 확인 (디버깅용)
        debug = os.getenv("DEBUG", "").lower() == "true"
        if debug:
            print("🔍 환경 변수 확인:")
            for key in ["RAWDATA_PGHOST", "PGHOST", "RAWDATA_PGUSER", "PGUSER", 
                       "RAWDATA_PGPASSWORD", "PGPASSWORD", "DB_URL", "DB_USER", "DB_PASSWORD"]:
                val = os.getenv(key)
                if val:
                    if "PASSWORD" in key:
                        print(f"   {key}: {'*' * min(len(val), 8)}")
                    else:
                        print(f"   {key}: {val}")
            print()
        
        # DB 연결 정보 확인
        config = get_pg_config()
        print(f"🔌 연결 정보:")
        print(f"   호스트: {config.host}")
        print(f"   포트: {config.port}")
        print(f"   데이터베이스: {config.database}")
        print(f"   사용자: {config.user}")
        print()
        
        # DB 연결
        print("📡 데이터베이스 연결 중...")
        conn = connect_pg()
        cursor = conn.cursor()
        
        # 1. 전체 상품 수 확인
        print("\n📊 === Raw 상품 데이터 통계 ===")
        cursor.execute("SELECT COUNT(*) FROM raw_product_document")
        total_count = cursor.fetchone()[0]
        print(f"총 상품 수: {format_number(total_count)}개")
        
        if total_count == 0:
            print("\n⚠️  상품 데이터가 없습니다.")
            conn.close()
            return
        
        # 2. 최신/오래된 데이터 확인
        cursor.execute("""
            SELECT 
                MIN(created_at) as oldest,
                MAX(created_at) as newest,
                MAX(updated_at) as last_updated
            FROM raw_product_document
        """)
        oldest, newest, last_updated = cursor.fetchone()
        print(f"\n📅 데이터 기간:")
        print(f"   최초 생성: {oldest}")
        print(f"   최신 생성: {newest}")
        print(f"   마지막 업데이트: {last_updated}")
        
        # 3. 일별 통계 (최근 7일)
        print(f"\n📈 최근 7일간 일별 상품 수:")
        cursor.execute("""
            SELECT 
                DATE(created_at) as date,
                COUNT(*) as count
            FROM raw_product_document
            WHERE created_at >= NOW() - INTERVAL '7 days'
            GROUP BY DATE(created_at)
            ORDER BY date DESC
        """)
        daily_stats = cursor.fetchall()
        if daily_stats:
            for date, count in daily_stats:
                print(f"   {date}: {format_number(count)}개")
        else:
            print("   (최근 7일간 데이터 없음)")
        
        # 4. 샘플 상품 ID 확인
        print(f"\n🔍 샘플 상품 ID (최대 10개):")
        cursor.execute("""
            SELECT product_id, created_at
            FROM raw_product_document
            ORDER BY created_at DESC
            LIMIT 10
        """)
        samples = cursor.fetchall()
        for product_id, created_at in samples:
            print(f"   - {product_id} (생성: {created_at})")
        
        # 5. 브랜드/카테고리 통계도 함께 확인
        cursor.execute("SELECT COUNT(*) FROM raw_brand_document")
        brand_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM raw_category_document")
        category_count = cursor.fetchone()[0]
        
        print(f"\n📦 관련 데이터:")
        print(f"   브랜드 수: {format_number(brand_count)}개")
        print(f"   카테고리 수: {format_number(category_count)}개")
        
        # 6. 테이블 크기 확인 (PostgreSQL)
        try:
            cursor.execute("""
                SELECT 
                    pg_size_pretty(pg_total_relation_size('raw_product_document')) as total_size,
                    pg_size_pretty(pg_relation_size('raw_product_document')) as table_size,
                    pg_size_pretty(pg_indexes_size('raw_product_document')) as indexes_size
            """)
            total_size, table_size, indexes_size = cursor.fetchone()
            print(f"\n💾 테이블 크기:")
            print(f"   총 크기: {total_size}")
            print(f"   테이블: {table_size}")
            print(f"   인덱스: {indexes_size}")
        except Exception as e:
            print(f"\n⚠️  테이블 크기 조회 실패: {e}")
        
        conn.close()
        print(f"\n✅ 확인 완료!")
        
    except ValueError as e:
        print(f"❌ 환경 변수 오류: {e}", file=sys.stderr)
        print("\n필요한 환경 변수:")
        print("  - RAWDATA_PGHOST 또는 PGHOST")
        print("  - RAWDATA_PGUSER 또는 PGUSER")
        print("  - RAWDATA_PGPASSWORD 또는 PGPASSWORD")
        sys.exit(1)
    except Exception as e:
        print(f"❌ 오류 발생: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    check_rawdata_products()
