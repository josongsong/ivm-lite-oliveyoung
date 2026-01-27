#!/usr/bin/env python3
"""
크롤링된 제품 데이터에서 카테고리 정보를 추출하여 raw_category_document 테이블에 삽입
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple
from collections import defaultdict

import psycopg2

# rawdata_db 유틸 import
sys.path.append(str(Path(__file__).resolve().parent))
from rawdata_db import connect_pg, ensure_raw_tables, init_rawdata_database_and_schema


def extract_categories_from_products(conn) -> List[Dict]:
    """제품 데이터에서 카테고리 정보 추출"""
    cursor = conn.cursor()
    
    # 크롤링된 제품에서 카테고리 정보 추출 (SYN 제외)
    cursor.execute("""
        SELECT document->'masterInfo'->'standardCategory' as category
        FROM raw_product_document
        WHERE product_id NOT LIKE '%SYN%'
          AND document->'masterInfo'->'standardCategory' IS NOT NULL
    """)
    
    categories = defaultdict(lambda: {"count": 0, "data": None})
    
    for row in cursor.fetchall():
        category = row[0]
        if not category:
            continue
            
        # 카테고리 키 생성 (large.code + medium.code + small.code)
        large = category.get("large", {})
        medium = category.get("medium", {})
        small = category.get("small", {})
        
        large_code = large.get("code", "")
        medium_code = medium.get("code", "")
        small_code = small.get("code", "")
        
        # 카테고리 ID 생성
        category_id = f"{large_code}_{medium_code}_{small_code}".strip("_")
        if not category_id:
            continue
            
        # 카운트 증가 및 데이터 저장
        categories[category_id]["count"] += 1
        if categories[category_id]["data"] is None:
            categories[category_id]["data"] = {
                "large": large,
                "medium": medium,
                "small": small,
            }
    
    cursor.close()
    
    # 카테고리 문서 생성
    category_docs = []
    for category_id, info in categories.items():
        large = info["data"]["large"]
        medium = info["data"]["medium"]
        small = info["data"]["small"]
        
        # 카테고리 이름 생성
        names = []
        if large.get("name"):
            names.append(large["name"])
        if medium.get("name"):
            names.append(medium["name"])
        if small.get("name"):
            names.append(small["name"])
        
        category_name = " > ".join(names) if names else category_id
        
        # 카테고리 문서 생성
        doc = {
            "categoryId": category_id,
            "categoryCode": category_id,
            "categoryName": category_name,
            "categoryNameEn": category_name,  # 영어 이름은 동일하게
            "parentId": None,  # 계층 구조는 나중에 처리
            "depth": 2 if small.get("code") else (1 if medium.get("code") else 0),
            "sortOrder": 1,
            "path": {
                "ids": [x for x in [large.get("code"), medium.get("code"), small.get("code")] if x],
                "names": names,
                "fullPath": category_name,
            },
            "iconUrl": None,
            "bannerUrl": None,
            "description": f"{category_name} 카테고리 (제품 수: {info['count']})",
            "categoryType": "DISPLAY",
            "displayYn": True,
            "showInNav": True,
            "showInFilter": True,
            "searchKeywords": names,
            "attributes": [],
            "seo": {
                "title": category_name,
                "description": f"{category_name} 제품 카테고리",
                "keywords": names,
                "canonicalUrl": None,
            },
            "meta": {
                "createdAt": "2026-01-20T00:00:00Z",
                "updatedAt": "2026-01-20T00:00:00Z",
                "createdBy": "category-extractor",
                "updatedBy": "category-extractor",
                "version": 1,
            },
        }
        
        category_docs.append((category_id, doc))
    
    return category_docs


def insert_categories(conn, category_docs: List[Tuple[str, Dict]]):
    """카테고리 문서를 DB에 삽입"""
    cursor = conn.cursor()
    inserted = 0
    skipped = 0
    
    for category_id, doc in category_docs:
        # 중복 확인
        cursor.execute(
            "SELECT category_id FROM raw_category_document WHERE category_id = %s",
            (category_id,),
        )
        if cursor.fetchone():
            skipped += 1
            continue
        
        # 삽입
        try:
            cursor.execute(
                """
                INSERT INTO raw_category_document (category_id, document)
                VALUES (%s, %s::jsonb)
                ON CONFLICT (category_id) DO NOTHING
                """,
                (category_id, json.dumps(doc, ensure_ascii=False)),
            )
            inserted += 1
            
            if inserted % 10 == 0:
                conn.commit()
                print(f"  Committed {inserted} categories...", file=sys.stderr)
                
        except Exception as e:
            print(f"  Error inserting category {category_id}: {e}", file=sys.stderr)
            continue
    
    conn.commit()
    cursor.close()
    return inserted, skipped


def main():
    import sys
    
    print("Extracting categories from crawled products...", file=sys.stderr)
    
    try:
        init_rawdata_database_and_schema()
        conn = connect_pg()
        ensure_raw_tables(conn)
    except Exception as e:
        print(f"Error connecting to database: {e}", file=sys.stderr)
        return 1
    
    try:
        # 카테고리 추출
        print("Extracting category information...", file=sys.stderr)
        category_docs = extract_categories_from_products(conn)
        print(f"Found {len(category_docs)} unique categories", file=sys.stderr)
        
        # 카테고리 삽입
        print("Inserting categories...", file=sys.stderr)
        inserted, skipped = insert_categories(conn, category_docs)
        print(f"\nDone! Inserted: {inserted}, Skipped: {skipped}", file=sys.stderr)
        
    finally:
        conn.close()
    
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
