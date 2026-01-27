#!/usr/bin/env python3
"""
PostgreSQL rawdata DB 유틸

목표:
- AWS PostgreSQL (또는 로컬)에 `rawdata` 데이터베이스를 생성
- raw_*_document 테이블(상품/브랜드/카테고리)을 생성/보장
- 크롤러들이 공통 DB 설정/연결 로직을 공유

환경 변수 (필수):
- RAWDATA_PGHOST 또는 PGHOST: PostgreSQL 호스트 (예: your-db.xxxxx.ap-northeast-2.rds.amazonaws.com)
- RAWDATA_PGPORT 또는 PGPORT: 포트 (기본: 5432)
- RAWDATA_PGUSER 또는 PGUSER: 사용자명
- RAWDATA_PGPASSWORD 또는 PGPASSWORD: 비밀번호
- RAWDATA_PGDATABASE 또는 PGDATABASE: 데이터베이스명 (기본: rawdata)
- RAWDATA_ADMIN_DB: DB 생성용 admin 연결 DB (기본: postgres)

사용 예:
  export RAWDATA_PGHOST=your-db.xxxxx.ap-northeast-2.rds.amazonaws.com
  export RAWDATA_PGPORT=5432
  export RAWDATA_PGUSER=your_user
  export RAWDATA_PGPASSWORD=your_password
  export RAWDATA_PGDATABASE=rawdata
  python3 tools/crawler/crawl-mecca-brands.py
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Any, Dict, Optional

import psycopg2


@dataclass(frozen=True)
class PgConfig:
    host: str
    port: int
    database: str
    user: str
    password: str

    def as_psycopg2_kwargs(self) -> Dict[str, Any]:
        return {
            "host": self.host,
            "port": self.port,
            "database": self.database,
            "user": self.user,
            "password": self.password,
        }


def _env(name: str) -> Optional[str]:
    v = os.getenv(name)
    return v if v is not None and v != "" else None


def get_pg_config(target_db: Optional[str] = None) -> PgConfig:
    """
    환경 변수에서 PostgreSQL 연결 정보를 읽어온다.
    AWS RDS 사용 시 환경 변수 필수 설정 필요.
    """
    host = _env("RAWDATA_PGHOST") or _env("PGHOST")
    if not host:
        raise ValueError(
            "RAWDATA_PGHOST 또는 PGHOST 환경 변수가 필요합니다. "
            "예: export RAWDATA_PGHOST=your-db.xxxxx.ap-northeast-2.rds.amazonaws.com"
        )

    port_str = _env("RAWDATA_PGPORT") or _env("PGPORT") or "5432"
    user = _env("RAWDATA_PGUSER") or _env("PGUSER")
    if not user:
        raise ValueError(
            "RAWDATA_PGUSER 또는 PGUSER 환경 변수가 필요합니다. "
            "예: export RAWDATA_PGUSER=your_user"
        )

    password = _env("RAWDATA_PGPASSWORD") or _env("PGPASSWORD")
    if not password:
        raise ValueError(
            "RAWDATA_PGPASSWORD 또는 PGPASSWORD 환경 변수가 필요합니다. "
            "예: export RAWDATA_PGPASSWORD=your_password"
        )

    database = (
        target_db
        or _env("RAWDATA_PGDATABASE")
        or _env("PGDATABASE")
        or "rawdata"
    )

    try:
        port = int(port_str)
    except ValueError as e:
        raise ValueError(f"Invalid PGPORT: {port_str}") from e

    return PgConfig(host=host, port=port, database=database, user=user, password=password)


def connect_pg(target_db: Optional[str] = None):
    cfg = get_pg_config(target_db=target_db)
    return psycopg2.connect(**cfg.as_psycopg2_kwargs())


def ensure_database_exists(db_name: Optional[str] = None) -> str:
    """
    rawdata DB가 없으면 생성한다.
    - Postgres는 CREATE DATABASE IF NOT EXISTS를 지원하지 않으므로 존재 여부를 조회 후 생성한다.
    - AWS RDS의 경우 DB 생성 권한이 없을 수 있으므로, 이미 존재하는 DB를 사용하도록 한다.
    """
    target_db = db_name or get_pg_config().database
    admin_db = _env("RAWDATA_ADMIN_DB") or "postgres"

    # admin DB로 먼저 연결 시도 (DB 생성 권한이 있는 경우)
    conn = None
    try:
        conn = connect_pg(target_db=admin_db)
        conn.autocommit = True
        cur = conn.cursor()
        cur.execute("SELECT 1 FROM pg_database WHERE datname = %s", (target_db,))
        exists = cur.fetchone() is not None
        if not exists:
            # DB 이름은 식별자라 파라미터 바인딩이 안되므로 엄격히 검증 후 문자열로 삽입
            if not target_db.replace("_", "").isalnum():
                raise ValueError(f"Unsafe database name: {target_db!r}")
            try:
                cur.execute(f'CREATE DATABASE "{target_db}"')
                print(f"Created database: {target_db}", file=__import__("sys").stderr)
            except Exception as e:
                # AWS RDS 등에서 DB 생성 권한이 없을 수 있음
                print(
                    f"Warning: Could not create database {target_db}: {e}. "
                    f"Assuming it exists or will be created manually.",
                    file=__import__("sys").stderr,
                )
        cur.close()
        conn.close()
        return target_db
    except Exception as e:
        # admin DB 연결 실패 시, 타겟 DB가 이미 존재한다고 가정하고 진행
        print(
            f"Warning: Could not connect to admin DB ({admin_db}): {e}. "
            f"Assuming target database {target_db} exists.",
            file=__import__("sys").stderr,
        )
        if conn:
            conn.close()
        return target_db


def ensure_raw_tables(conn) -> None:
    """
    raw_*_document 테이블(상품/브랜드/카테고리)을 생성한다.
    - 크롤러가 JSON 원문을 그대로 저장하는 raw 영역 목적
    """
    cursor = conn.cursor()

    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS raw_brand_document (
            brand_id   TEXT PRIMARY KEY,
            document   JSONB NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS raw_category_document (
            category_id TEXT PRIMARY KEY,
            document    JSONB NOT NULL,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS raw_product_document (
            product_id TEXT PRIMARY KEY,
            document   JSONB NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        """
    )

    # 자주 쓰는 인덱스 (PK 외 보조)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_raw_product_document_created_at ON raw_product_document(created_at)"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_raw_brand_document_created_at ON raw_brand_document(created_at)"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_raw_category_document_created_at ON raw_category_document(created_at)"
    )

    conn.commit()
    cursor.close()


def init_rawdata_database_and_schema() -> None:
    """
    rawdata DB 생성 + 테이블 생성까지 한 번에 수행.
    크롤러 실행 전에 호출하면 안전하다.
    """
    db_name = ensure_database_exists()
    conn = connect_pg(target_db=db_name)
    try:
        ensure_raw_tables(conn)
    finally:
        conn.close()

