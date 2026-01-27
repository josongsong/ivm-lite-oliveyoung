#!/usr/bin/env python3
"""
rawdata DB + 스키마(테이블) 초기화 스크립트

사용 예:
  python3 tools/crawler/init-rawdata-db.py

환경 변수:
  RAWDATA_PGHOST / RAWDATA_PGPORT / RAWDATA_PGUSER / RAWDATA_PGPASSWORD / RAWDATA_PGDATABASE
"""

import sys
from pathlib import Path

# 같은 폴더의 rawdata_db.py를 import 하기 위한 경로 추가
sys.path.append(str(Path(__file__).resolve().parent))

from rawdata_db import init_rawdata_database_and_schema  # noqa: E402


def main() -> int:
    init_rawdata_database_and_schema()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

