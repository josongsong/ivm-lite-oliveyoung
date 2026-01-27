#!/usr/bin/env python3
"""
MECCA sitemap 기반 제품 크롤링 (requests + JSON-LD)

목표:
- Mecca sitemapindex에서 catalog sitemap들을 읽어 제품 URL을 확보
- product URL을 샤딩(shard)하여 여러 프로세스를 동시에 돌릴 수 있게 함
- 제품 상세 HTML의 JSON-LD(Product)로부터 이미지(여러 장), 브랜드, sku/mpn 등을 추출
- raw_*_document 테이블에 insert (중복은 ON CONFLICT DO NOTHING)

사용 예:
  # 3개 프로세스를 동시에 돌릴 때 (각각 터미널에서 실행)
  python3 tools/crawl-mecca-products-sitemap.py --limit 250 --shard-count 3 --shard-index 0
  python3 tools/crawl-mecca-products-sitemap.py --limit 250 --shard-count 3 --shard-index 1
  python3 tools/crawl-mecca-products-sitemap.py --limit 250 --shard-count 3 --shard-index 2
"""

import argparse
import importlib.util
import json
import sys
import time
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path
from typing import Iterable, List, Optional, Set, Tuple
from urllib.parse import urlparse

import psycopg2
import requests

# rawdata_db 유틸 import
sys.path.append(str(Path(__file__).resolve().parent))
from rawdata_db import connect_pg, ensure_raw_tables, init_rawdata_database_and_schema

DEFAULT_SITEMAP_INDEX_URL = "https://www.mecca.com/en-au/sitemap.xml"
DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"


def load_mecca_module():
    # 현재 스크립트 위치 기준으로 playwright 모듈 로드
    current_dir = Path(__file__).resolve().parent
    path = current_dir / "crawl-mecca-products-playwright.py"
    spec = importlib.util.spec_from_file_location("mecca_crawler", str(path))
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


def fetch_text(url: str, timeout_seconds: int = 30) -> str:
    response = requests.get(url, timeout=timeout_seconds, headers={"User-Agent": DEFAULT_USER_AGENT})
    response.raise_for_status()
    return response.text


def parse_sitemap_index(xml_text: str) -> List[str]:
    root = ET.fromstring(xml_text)
    # 케이스 1) 표준 sitemapindex: xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
    ns = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    locs: List[str] = []

    sitemaps = root.findall("sm:sitemap", ns)
    if sitemaps:
        for sitemap in sitemaps:
            loc = sitemap.find("sm:loc", ns)
            if loc is not None and loc.text:
                locs.append(loc.text.strip())
        return locs

    # 케이스 2) 비표준(또는 ns 없는) sitemapindex: <sitemap><loc>...</loc></sitemap>
    for sitemap in root.findall("sitemap"):
        loc = sitemap.find("loc")
        if loc is not None and loc.text:
            locs.append(loc.text.strip())
    return locs


def iter_urlset_locs(xml_text: str) -> Iterable[str]:
    root = ET.fromstring(xml_text)
    ns = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    for url in root.findall("sm:url", ns):
        loc = url.find("sm:loc", ns)
        if loc is not None and loc.text:
            yield loc.text.strip()


def is_mecca_product_url(url: str) -> bool:
    parsed = urlparse(url)
    if parsed.netloc != "www.mecca.com":
        return False
    if not parsed.path.startswith("/en-au/"):
        return False
    # 기존 크롤러의 코드 추출 규칙과 동일하게 I-/V- 코드가 있어야 제품으로 간주
    return ("-I-" in parsed.path) or ("-V-" in parsed.path)


def shard_filter(url: str, shard_count: int, shard_index: int) -> bool:
    if shard_count <= 1:
        return True
    # Python의 hash()는 프로세스마다 seed가 달라져 shard가 뒤섞인다.
    # 안정적인 샤딩을 위해 crc32를 사용한다.
    h = zlib.crc32(url.encode("utf-8")) & 0xFFFFFFFF
    return (h % shard_count) == shard_index


def ensure_tables(conn) -> None:
    """테이블 생성은 rawdata_db.ensure_raw_tables()를 사용"""
    ensure_raw_tables(conn)


def load_existing_product_ids(conn) -> Set[str]:
    cursor = conn.cursor()
    cursor.execute("SELECT product_id FROM raw_product_document")
    return {row[0] for row in cursor.fetchall()}


def upsert_category(conn, mecca, category: str) -> None:
    cursor = conn.cursor()
    cat_doc = mecca.create_category_document(category, depth=1, parent_id=None)
    cursor.execute(
        """
        INSERT INTO raw_category_document (category_id, document)
        VALUES (%s, %s::jsonb)
        ON CONFLICT (category_id) DO UPDATE SET document = EXCLUDED.document
        """,
        (cat_doc["categoryId"], json.dumps(cat_doc)),
    )
    conn.commit()


def maybe_upsert_brand(conn, mecca, brand_name: str, saved_brands: Set[str]) -> None:
    if not brand_name or brand_name == "Unknown":
        return
    if brand_name in saved_brands:
        return
    cursor = conn.cursor()
    brand_doc = mecca.create_brand_document(brand_name)
    cursor.execute(
        """
        INSERT INTO raw_brand_document (brand_id, document)
        VALUES (%s, %s::jsonb)
        ON CONFLICT (brand_id) DO UPDATE SET document = EXCLUDED.document
        """,
        (brand_doc["brandId"], json.dumps(brand_doc)),
    )
    conn.commit()
    saved_brands.add(brand_name)


def insert_product(conn, mecca, product_data: dict, category: str) -> Tuple[bool, str]:
    doc = mecca.create_product_document(product_data, category)
    product_id = doc["masterInfo"]["gdsCd"]
    cursor = conn.cursor()
    cursor.execute(
        """
        INSERT INTO raw_product_document (product_id, document)
        VALUES (%s, %s::jsonb)
        ON CONFLICT (product_id) DO NOTHING
        """,
        (product_id, json.dumps(doc)),
    )
    inserted = cursor.rowcount == 1
    if inserted:
        conn.commit()
    return inserted, product_id


def iter_candidate_sitemaps(index_url: str) -> List[str]:
    # en-au sitemap.xml은 sitemapindex 형태
    xml = fetch_text(index_url, timeout_seconds=30)
    locs = parse_sitemap_index(xml)
    # catalog 쪽이 제품 상세 URL을 포함하므로 우선
    catalog = [u for u in locs if "sitemaps_catalog_" in u]
    others = [u for u in locs if u not in catalog]
    return catalog + others


def main() -> int:
    parser = argparse.ArgumentParser(description="MECCA sitemap crawler (parallel shards supported)")
    parser.add_argument("--limit", type=int, default=500, help="삽입할 신규 제품 수")
    parser.add_argument(
        "--default-category",
        type=str,
        default="makeup",
        choices=["makeup", "skincare", "fragrance", "haircare", "body", "wellness"],
        help="카테고리 정보를 추론할 수 없을 때 사용할 기본 카테고리",
    )
    parser.add_argument("--sitemap-index-url", type=str, default=DEFAULT_SITEMAP_INDEX_URL)
    parser.add_argument("--shard-count", type=int, default=1)
    parser.add_argument("--shard-index", type=int, default=0)
    parser.add_argument("--max-sitemaps", type=int, default=10, help="읽을 catalog sitemap 개수 상한")
    parser.add_argument("--sleep-ms", type=int, default=0, help="요청 사이에 고정 sleep(ms) (차단 완화용)")
    args = parser.parse_args()

    if args.shard_index < 0 or args.shard_index >= args.shard_count:
        raise SystemExit("--shard-index must be in [0, shard-count)")

    mecca = load_mecca_module()

    init_rawdata_database_and_schema()
    conn = connect_pg()
    ensure_tables(conn)
    existing = load_existing_product_ids(conn)
    saved_brands: Set[str] = set()
    upsert_category(conn, mecca, args.default_category)

    inserted = 0
    scanned_urls = 0

    sitemaps = iter_candidate_sitemaps(args.sitemap_index_url)[: args.max_sitemaps]
    print(f"Found {len(sitemaps)} sitemaps to scan (max={args.max_sitemaps})", file=sys.stderr)

    for sitemap_url in sitemaps:
        if inserted >= args.limit:
            break

        try:
            xml = fetch_text(sitemap_url, timeout_seconds=60)
        except Exception as e:
            print(f"Failed to fetch sitemap: {sitemap_url} ({e})", file=sys.stderr)
            continue

        for loc in iter_urlset_locs(xml):
            if inserted >= args.limit:
                break
            if not is_mecca_product_url(loc):
                continue
            if not shard_filter(loc, args.shard_count, args.shard_index):
                continue

            scanned_urls += 1
            code = mecca.extract_product_code_from_url(loc)
            if code and code in existing:
                continue

            product_data = mecca.fetch_product_details_from_jsonld(loc)
            if not product_data:
                continue

            brand_name = product_data.get("brand") or "Unknown"
            maybe_upsert_brand(conn, mecca, brand_name, saved_brands)

            ok, product_id = insert_product(conn, mecca, product_data, args.default_category)
            if ok:
                inserted += 1
                existing.add(product_id)
                if inserted % 25 == 0:
                    print(f"Inserted {inserted}/{args.limit} (scanned={scanned_urls})", file=sys.stderr)

            if args.sleep_ms > 0:
                time.sleep(args.sleep_ms / 1000.0)

    conn.close()
    print(f"Done. inserted={inserted} scanned={scanned_urls} shard={args.shard_index}/{args.shard_count}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

