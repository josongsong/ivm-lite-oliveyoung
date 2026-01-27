#!/usr/bin/env python3
"""
ID 매핑 일관성 확인: 제품-카테고리, 제품-브랜드 매핑 검증
"""

import json
import sys
from collections import defaultdict
from pathlib import Path

import psycopg2

# rawdata_db 유틸 import
sys.path.append(str(Path(__file__).resolve().parent))
from rawdata_db import connect_pg, ensure_raw_tables, init_rawdata_database_and_schema


def build_category_id(category):
    """카테고리 ID 생성 (extract-categories-from-products.py와 동일한 로직)"""
    if not category:
        return None
    
    large = category.get("large", {})
    medium = category.get("medium", {})
    small = category.get("small", {})
    
    large_code = large.get("code", "")
    medium_code = medium.get("code", "")
    small_code = small.get("code", "")
    
    category_id = f"{large_code}_{medium_code}_{small_code}".strip("_")
    return category_id if category_id else None


def check_mappings():
    init_rawdata_database_and_schema()
    conn = connect_pg()
    ensure_raw_tables(conn)
    cursor = conn.cursor()
    
    # 1. 브랜드 매핑 확인
    print("=== 브랜드 ID 매핑 확인 ===")
    cursor.execute("""
        SELECT brand_id FROM raw_brand_document
    """)
    existing_brands = {row[0] for row in cursor.fetchall()}
    print(f"총 브랜드 수: {len(existing_brands)}")
    
    cursor.execute("""
        SELECT product_id, document->'masterInfo'->'brand'->>'code' as brand_code
        FROM raw_product_document
        WHERE product_id NOT LIKE '%SYN%'
          AND document->'masterInfo'->'brand'->>'code' IS NOT NULL
    """)
    
    brand_refs = defaultdict(int)
    missing_brands = set()
    
    for product_id, brand_code in cursor.fetchall():
        if brand_code:
            brand_refs[brand_code] += 1
            if brand_code not in existing_brands:
                missing_brands.add(brand_code)
    
    print(f"제품에서 참조하는 브랜드 수: {len(brand_refs)}")
    print(f"매핑되지 않은 브랜드 수: {len(missing_brands)}")
    if missing_brands:
        print(f"  매핑 안 된 브랜드 (상위 10개): {list(missing_brands)[:10]}")
    
    # 2. 카테고리 매핑 확인
    print("\n=== 카테고리 ID 매핑 확인 ===")
    cursor.execute("""
        SELECT category_id FROM raw_category_document
    """)
    existing_categories = {row[0] for row in cursor.fetchall()}
    print(f"총 카테고리 수: {len(existing_categories)}")
    
    cursor.execute("""
        SELECT product_id, document->'masterInfo'->'standardCategory' as category
        FROM raw_product_document
        WHERE product_id NOT LIKE '%SYN%'
          AND document->'masterInfo'->'standardCategory' IS NOT NULL
    """)
    
    category_refs = defaultdict(int)
    missing_categories = set()
    
    for product_id, category_json in cursor.fetchall():
        if category_json:
            category = json.loads(json.dumps(category_json))
            category_id = build_category_id(category)
            if category_id:
                category_refs[category_id] += 1
                if category_id not in existing_categories:
                    missing_categories.add(category_id)
    
    print(f"제품에서 참조하는 카테고리 수: {len(category_refs)}")
    print(f"매핑되지 않은 카테고리 수: {len(missing_categories)}")
    if missing_categories:
        print(f"  매핑 안 된 카테고리: {list(missing_categories)[:10]}")
    
    # 3. 매핑 통계
    print("\n=== 매핑 통계 ===")
    print(f"브랜드 매핑률: {(len(brand_refs) - len(missing_brands)) / len(brand_refs) * 100:.1f}% ({len(brand_refs) - len(missing_brands)}/{len(brand_refs)})")
    print(f"카테고리 매핑률: {(len(category_refs) - len(missing_categories)) / len(category_refs) * 100:.1f}% ({len(category_refs) - len(missing_categories)}/{len(category_refs)})")
    
    # 4. 상위 브랜드 매핑 상태
    print("\n=== 상위 브랜드 매핑 상태 ===")
    sorted_brands = sorted(brand_refs.items(), key=lambda x: x[1], reverse=True)
    for brand_code, count in sorted_brands[:10]:
        status = "✓" if brand_code in existing_brands else "✗"
        print(f"  {status} {brand_code}: {count}개 제품")
    
    # 5. 카테고리별 제품 수
    print("\n=== 카테고리별 제품 수 ===")
    sorted_categories = sorted(category_refs.items(), key=lambda x: x[1], reverse=True)
    for category_id, count in sorted_categories:
        status = "✓" if category_id in existing_categories else "✗"
        print(f"  {status} {category_id}: {count}개 제품")
    
    cursor.close()
    conn.close()
    
    return {
        "brands": {
            "total": len(existing_brands),
            "referenced": len(brand_refs),
            "missing": len(missing_brands),
        },
        "categories": {
            "total": len(existing_categories),
            "referenced": len(category_refs),
            "missing": len(missing_categories),
        },
    }


if __name__ == "__main__":
    check_mappings()
