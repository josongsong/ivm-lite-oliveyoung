#!/usr/bin/env python3
"""
MECCA 데이터 통합 크롤링 스크립트

브랜드 → 상품 → 카테고리 순서로 크롤링하여 AWS PostgreSQL rawdata DB에 저장합니다.

사용법:
  # 환경 변수 설정 (필수)
  export RAWDATA_PGHOST=your-db.xxxxx.ap-northeast-2.rds.amazonaws.com
  export RAWDATA_PGPORT=5432
  export RAWDATA_PGUSER=your_user
  export RAWDATA_PGPASSWORD=your_password
  export RAWDATA_PGDATABASE=rawdata

  # 전체 실행
  python3 tools/crawler/ingest-mecca-to-rawdata.py

  # 특정 단계만 실행
  python3 tools/crawler/ingest-mecca-to-rawdata.py --step brands
  python3 tools/crawler/ingest-mecca-to-rawdata.py --step products --category makeup --limit 50
  python3 tools/crawler/ingest-mecca-to-rawdata.py --step categories
"""

import argparse
import subprocess
import sys
from pathlib import Path

# 현재 스크립트 위치 기준으로 크롤러 경로 설정
SCRIPT_DIR = Path(__file__).resolve().parent


def run_step(step_name: str, args: list = None) -> int:
    """크롤러 스크립트 실행"""
    if args is None:
        args = []

    script_map = {
        "brands": "crawl-mecca-brands.py",
        "products": "crawl-mecca-products.py",
        "categories": "extract-categories-from-products.py",
    }

    if step_name not in script_map:
        print(f"Unknown step: {step_name}", file=sys.stderr)
        return 1

    script_path = SCRIPT_DIR / script_map[step_name]
    if not script_path.exists():
        print(f"Script not found: {script_path}", file=sys.stderr)
        return 1

    cmd = [sys.executable, str(script_path)] + args
    print(f"\n{'='*60}", file=sys.stderr)
    print(f"Running: {' '.join(cmd)}", file=sys.stderr)
    print(f"{'='*60}\n", file=sys.stderr)

    result = subprocess.run(cmd, cwd=SCRIPT_DIR.parent.parent)
    return result.returncode


def main() -> int:
    parser = argparse.ArgumentParser(
        description="MECCA 데이터 통합 크롤링 (브랜드 → 상품 → 카테고리)"
    )
    parser.add_argument(
        "--step",
        type=str,
        choices=["brands", "products", "categories", "all"],
        default="all",
        help="실행할 단계 (기본: all)",
    )
    parser.add_argument(
        "--category",
        type=str,
        choices=["makeup", "skincare", "fragrance", "haircare", "body"],
        default="makeup",
        help="상품 크롤링 카테고리 (기본: makeup)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="상품 크롤링 제한 수 (기본: 제한 없음)",
    )
    parser.add_argument(
        "--skip-brands",
        action="store_true",
        help="브랜드 크롤링 건너뛰기 (all 실행 시)",
    )
    parser.add_argument(
        "--skip-products",
        action="store_true",
        help="상품 크롤링 건너뛰기 (all 실행 시)",
    )
    parser.add_argument(
        "--skip-categories",
        action="store_true",
        help="카테고리 추출 건너뛰기 (all 실행 시)",
    )

    args = parser.parse_args()

    # 환경 변수 확인
    import os

    required_vars = ["RAWDATA_PGHOST", "RAWDATA_PGUSER", "RAWDATA_PGPASSWORD"]
    missing_vars = [v for v in required_vars if not os.getenv(v)]
    if missing_vars:
        print(
            f"Error: 필수 환경 변수가 설정되지 않았습니다: {', '.join(missing_vars)}",
            file=sys.stderr,
        )
        print(
            "\n예시:\n"
            "  export RAWDATA_PGHOST=your-db.xxxxx.ap-northeast-2.rds.amazonaws.com\n"
            "  export RAWDATA_PGPORT=5432\n"
            "  export RAWDATA_PGUSER=your_user\n"
            "  export RAWDATA_PGPASSWORD=your_password\n"
            "  export RAWDATA_PGDATABASE=rawdata",
            file=sys.stderr,
        )
        return 1

    if args.step == "all":
        # 전체 실행: 브랜드 → 상품 → 카테고리
        steps = []
        if not args.skip_brands:
            steps.append("brands")
        if not args.skip_products:
            product_args = ["--category", args.category]
            if args.limit:
                product_args.extend(["--limit", str(args.limit)])
            steps.append(("products", product_args))
        if not args.skip_categories:
            steps.append("categories")

        for step in steps:
            if isinstance(step, tuple):
                step_name, step_args = step
                code = run_step(step_name, step_args)
            else:
                code = run_step(step)
            if code != 0:
                print(f"\nError: Step '{step}' failed with code {code}", file=sys.stderr)
                return code

        print("\n" + "=" * 60, file=sys.stderr)
        print("✅ 모든 단계 완료!", file=sys.stderr)
        print("=" * 60, file=sys.stderr)
        return 0
    else:
        # 단일 단계 실행
        step_args = []
        if args.step == "products":
            step_args = ["--category", args.category]
            if args.limit:
                step_args.extend(["--limit", str(args.limit)])

        return run_step(args.step, step_args)


if __name__ == "__main__":
    sys.exit(main())
