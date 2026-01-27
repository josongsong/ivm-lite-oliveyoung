#!/usr/bin/env python3
"""
매핑되지 않은 브랜드를 제품 데이터에서 추출하여 생성
"""

import json
import sys
from datetime import datetime
from pathlib import Path

import psycopg2

# rawdata_db 유틸 import
sys.path.append(str(Path(__file__).resolve().parent))
from rawdata_db import connect_pg, ensure_raw_tables, init_rawdata_database_and_schema


def create_missing_brands():
    init_rawdata_database_and_schema()
    conn = connect_pg()
    ensure_raw_tables(conn)
    cursor = conn.cursor()
    
    # 기존 브랜드 목록
    cursor.execute("SELECT brand_id FROM raw_brand_document")
    existing_brands = {row[0] for row in cursor.fetchall()}
    
    # 제품에서 참조하는 브랜드 추출
    cursor.execute("""
        SELECT DISTINCT 
            document->'masterInfo'->'brand'->>'code' as brand_code,
            document->'masterInfo'->'brand'->>'krName' as brand_kr_name,
            document->'masterInfo'->'brand'->>'enName' as brand_en_name
        FROM raw_product_document
        WHERE product_id NOT LIKE '%SYN%'
          AND document->'masterInfo'->'brand'->>'code' IS NOT NULL
    """)
    
    missing_brands = []
    for brand_code, brand_kr_name, brand_en_name in cursor.fetchall():
        if brand_code and brand_code not in existing_brands:
            missing_brands.append({
                "code": brand_code,
                "krName": brand_kr_name or brand_code,
                "enName": brand_en_name or brand_code,
            })
    
    if not missing_brands:
        print("매핑되지 않은 브랜드가 없습니다.")
        cursor.close()
        conn.close()
        return
    
    print(f"매핑되지 않은 브랜드 {len(missing_brands)}개 발견")
    
    # 브랜드 문서 생성 및 삽입
    inserted = 0
    for brand in missing_brands:
        brand_id = brand["code"]
        brand_doc = {
            "brandId": brand_id,
            "brandCode": brand_id,
            "brandName": brand["krName"],
            "brandNameEn": brand["enName"],
            "logoUrl": None,
            "bannerUrl": None,
            "description": f"{brand['krName']} 브랜드",
            "slogan": None,
            "story": None,
            "websiteUrl": None,
            "countryCode": None,
            "foundedYear": None,
            "tier": "STANDARD",
            "displayYn": True,
            "searchKeywords": [brand["krName"], brand["enName"]],
            "mainCategoryIds": [],
            "socialLinks": {},
            "tags": [],
            "meta": {
                "createdAt": datetime.utcnow().isoformat() + "Z",
                "updatedAt": datetime.utcnow().isoformat() + "Z",
                "createdBy": "brand-extractor",
                "updatedBy": "brand-extractor",
                "version": 1,
            },
        }
        
        try:
            cursor.execute(
                """
                INSERT INTO raw_brand_document (brand_id, document)
                VALUES (%s, %s::jsonb)
                ON CONFLICT (brand_id) DO NOTHING
                """,
                (brand_id, json.dumps(brand_doc, ensure_ascii=False)),
            )
            inserted += 1
            print(f"  생성: {brand_id} ({brand['krName']})")
        except Exception as e:
            print(f"  오류: {brand_id} - {e}")
    
    conn.commit()
    cursor.close()
    conn.close()
    
    print(f"\n완료! {inserted}개 브랜드 생성")


if __name__ == "__main__":
    create_missing_brands()
