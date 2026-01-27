#!/usr/bin/env python3
"""
MECCA 브랜드 크롤링 스크립트
https://www.mecca.com/en-au/brands/ 페이지에서 브랜드 정보를 수집하여 PostgreSQL에 삽입
"""

import json
import re
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Optional
from urllib.parse import urljoin

import psycopg2
import requests
from bs4 import BeautifulSoup

# rawdata_db 유틸 import
sys.path.append(str(Path(__file__).resolve().parent))
from rawdata_db import connect_pg, ensure_raw_tables, init_rawdata_database_and_schema

BASE_URL = "https://www.mecca.com/en-au/brands/"


def normalize_brand_code(brand_name: str) -> str:
    """브랜드 이름을 코드로 변환"""
    # 영문/숫자만 남기고 대문자로 변환
    code = re.sub(r"[^A-Z0-9]", "", brand_name.upper())
    # 숫자로 시작하는 경우 앞에 BRAND_ 추가
    if code and code[0].isdigit():
        code = f"BRAND_{code}"
    return code if code else f"BRAND_{hash(brand_name) % 10000}"


def extract_brands_from_page(html: str) -> list[dict]:
    """HTML에서 브랜드 정보 추출"""
    soup = BeautifulSoup(html, "html.parser")
    brands = []
    seen_brands = set()

    # 브랜드 링크 찾기 (href에 /brands/ 포함)
    brand_links = soup.find_all("a", href=re.compile(r"/brands/[^/]+"))
    
    for link in brand_links:
        brand_name = link.get_text(strip=True)
        if not brand_name or brand_name in seen_brands:
            continue
        
        # 단일 문자(A-Z)나 섹션 헤더는 제외
        if len(brand_name) == 1 and brand_name.isalpha():
            continue
        if brand_name in ["#", "New", "Featured Brands", "All Brands A-Z"]:
            continue
        if brand_name in ["Makeup Brands", "Skincare Brands", "Fragrance Brands", "Hair Brands", "Body Brands"]:
            continue

        seen_brands.add(brand_name)
        brand_url = urljoin(BASE_URL, link.get("href", ""))

        # "Exclusive to MECCA", "New brand" 등의 태그 제거
        brand_name = re.sub(r"\s*(Exclusive to MECCA|Online only|New brand|Trending).*$", "", brand_name, flags=re.I).strip()

        if brand_name and len(brand_name) > 1:
            brands.append({
                "name": brand_name,
                "url": brand_url,
            })

    return brands


def fetch_brand_details(brand_url: str) -> Optional[dict]:
    """브랜드 상세 페이지에서 추가 정보 추출"""
    try:
        response = requests.get(brand_url, timeout=10, headers={
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
        })
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")

        details = {}

        # 설명 찾기
        desc_elem = soup.find("meta", property="og:description")
        if desc_elem:
            details["description"] = desc_elem.get("content", "")

        # 로고 이미지 찾기
        logo_elem = soup.find("img", class_=re.compile(r"logo|brand.*image", re.I))
        if not logo_elem:
            logo_elem = soup.find("meta", property="og:image")
        if logo_elem:
            details["logoUrl"] = logo_elem.get("src") or logo_elem.get("content")

        return details
    except Exception as e:
        print(f"  Warning: Could not fetch details for {brand_url}: {e}", file=sys.stderr)
        return None


def create_brand_document(brand: dict, brand_code: str, details: Optional[dict] = None) -> dict:
    """브랜드 문서 생성 (스키마 준수)"""
    now = datetime.utcnow().isoformat() + "Z"

    # 브랜드 이름에서 영문명 추출 시도
    brand_name_en = None
    if re.match(r"^[A-Za-z0-9\s&.'-]+$", brand["name"]):
        brand_name_en = brand["name"]

    return {
        "brandId": brand_code,
        "brandCode": brand_code,
        "brandName": brand["name"],
        "brandNameEn": brand_name_en,
        "logoUrl": details.get("logoUrl") if details else None,
        "bannerUrl": None,
        "description": details.get("description") if details else None,
        "slogan": None,
        "story": None,
        "websiteUrl": brand.get("url"),
        "countryCode": None,
        "foundedYear": None,
        "tier": "STANDARD",
        "displayYn": True,
        "searchKeywords": [brand["name"]],
        "mainCategoryIds": [],
        "socialLinks": {
            "instagram": None,
            "facebook": None,
            "youtube": None,
            "tiktok": None,
            "twitter": None,
        },
        "tags": [],
        "meta": {
            "createdAt": now,
            "updatedAt": now,
            "createdBy": "crawler",
            "updatedBy": "crawler",
            "version": 1,
        },
    }


def insert_brands_to_db(brands: list[dict], conn):
    """브랜드 데이터를 데이터베이스에 삽입"""
    cursor = conn.cursor()
    inserted = 0
    skipped = 0

    for brand in brands:
        brand_code = normalize_brand_code(brand["name"])

        # 중복 확인
        cursor.execute(
            "SELECT brand_id FROM raw_brand_document WHERE brand_id = %s",
            (brand_code,),
        )
        if cursor.fetchone():
            skipped += 1
            continue

        # 상세 정보 가져오기 (선택적)
        details = None
        if brand.get("url"):
            details = fetch_brand_details(brand["url"])
            time.sleep(0.5)  # Rate limiting

        # 문서 생성
        document = create_brand_document(brand, brand_code, details)

        # 삽입
        cursor.execute(
            """
            INSERT INTO raw_brand_document (brand_id, document)
            VALUES (%s, %s::jsonb)
            ON CONFLICT (brand_id) DO NOTHING
            """,
            (brand_code, json.dumps(document)),
        )
        inserted += 1

        if inserted % 10 == 0:
            print(f"  Inserted {inserted} brands...", file=sys.stderr)
            conn.commit()

    conn.commit()
    cursor.close()
    return inserted, skipped


def main():
    print("Fetching MECCA brands page...", file=sys.stderr)
    try:
        response = requests.get(BASE_URL, timeout=30, headers={
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
        })
        response.raise_for_status()
    except Exception as e:
        print(f"Error fetching page: {e}", file=sys.stderr)
        return 1

    print("Extracting brands...", file=sys.stderr)
    brands = extract_brands_from_page(response.text)

    # 실제 브랜드가 적으면 웹 검색 결과의 전체 목록 사용
    if len(brands) < 50:
        print(f"Only found {len(brands)} brands. Using full list from web search...", file=sys.stderr)
        # 웹 검색 결과에서 브랜드 목록 사용 (중복 제거)
        seen = {b["name"] for b in brands}
        additional_brands = [
            {"name": "111SKIN", "url": None},
            {"name": "16Brand", "url": None},
            {"name": "3MoreInches", "url": None},
            {"name": "A.N OTHER", "url": None},
            {"name": "ABHATI Suisse", "url": None},
            {"name": "Act+Acre", "url": None},
            {"name": "Allies of Skin", "url": None},
            {"name": "Alpha-H", "url": None},
            {"name": "ALTAIA", "url": None},
            {"name": "Aman", "url": None},
            {"name": "Anastasia Beverly Hills", "url": None},
            {"name": "anatome", "url": None},
            {"name": "ARKIVE", "url": None},
            {"name": "Arpa", "url": None},
            {"name": "Augustinus Bader", "url": None},
            {"name": "AVEDA", "url": None},
            {"name": "Bamford", "url": None},
            {"name": "Bare Mum", "url": None},
            {"name": "bareMinerals", "url": None},
            {"name": "Bawdy", "url": None},
            {"name": "Biologique Recherche", "url": None},
            {"name": "bkr", "url": None},
            {"name": "Bobbi Brown", "url": None},
            {"name": "BORNTOSTANDOUT", "url": None},
            {"name": "Briogeo", "url": None},
            {"name": "Brow Code", "url": None},
            {"name": "Bumble and bumble", "url": None},
            {"name": "By Terry", "url": None},
            {"name": "BYREDO", "url": None},
            {"name": "Ceremonia", "url": None},
            {"name": "Chantecaille", "url": None},
            {"name": "Charlotte Tilbury", "url": None},
            {"name": "Clinique", "url": None},
            {"name": "Coco de Mer", "url": None},
            {"name": "Comme des Garçons", "url": None},
            {"name": "Commune", "url": None},
            {"name": "Control & Chaos", "url": None},
            {"name": "CORPUS", "url": None},
            {"name": "Cosmetics 27", "url": None},
            {"name": "Cosmic Dealer", "url": None},
            {"name": "Costa Brazil", "url": None},
            {"name": "Crown Affair", "url": None},
            {"name": "Cultured", "url": None},
            {"name": "D.S. & DURGA", "url": None},
            {"name": "Darphin", "url": None},
            {"name": "DedCool", "url": None},
            {"name": "DeoDoc", "url": None},
            {"name": "Dermalogica", "url": None},
            {"name": "DIPTYQUE", "url": None},
            {"name": "Dr. Barbara Sturm", "url": None},
            {"name": "Dr. Dennis Gross", "url": None},
            {"name": "Dr. Few Skincare", "url": None},
            {"name": "Dr. Jart+", "url": None},
            {"name": "Dr. Lara Devgan", "url": None},
            {"name": "Dr. Lipp", "url": None},
            {"name": "Dries Van Noten", "url": None},
            {"name": "Drunk Elephant", "url": None},
            {"name": "DUO", "url": None},
            {"name": "Dyson", "url": None},
            {"name": "Eau d'Italie", "url": None},
            {"name": "Editions de Parfums By Frédéric Malle", "url": None},
            {"name": "ELEFFECT", "url": None},
            {"name": "Element Eight", "url": None},
            {"name": "ELEMIS", "url": None},
            {"name": "Ellis Brooklyn", "url": None},
            {"name": "Ellis Faas", "url": None},
            {"name": "Emma Lewisham", "url": None},
            {"name": "Escentric Molecules", "url": None},
            {"name": "Estée Lauder", "url": None},
            {"name": "Eve Lom", "url": None},
            {"name": "FaceGym", "url": None},
            {"name": "Fig.1", "url": None},
            {"name": "Flamingo Estate", "url": None},
            {"name": "Floral Street", "url": None},
            {"name": "Foreo", "url": None},
            {"name": "Frank Body", "url": None},
            {"name": "FUCA", "url": None},
            {"name": "ghd", "url": None},
            {"name": "Gisou", "url": None},
            {"name": "Glossier", "url": None},
            {"name": "Glow Recipe", "url": None},
            {"name": "Go-To", "url": None},
            {"name": "Goldfaden MD", "url": None},
            {"name": "GOOP", "url": None},
            {"name": "Hair by Sam McKnight", "url": None},
            {"name": "Half Magic", "url": None},
            {"name": "Herbar", "url": None},
            {"name": "Herbario", "url": None},
            {"name": "Horace", "url": None},
            {"name": "HOURGLASS", "url": None},
            {"name": "ILIA Beauty", "url": None},
            {"name": "INTU WELLNESS", "url": None},
            {"name": "ISAMAYA", "url": None},
            {"name": "Isle of Paradise", "url": None},
            {"name": "James Read Tan", "url": None},
            {"name": "Jo Malone London", "url": None},
            {"name": "Josh Wood Colour", "url": None},
            {"name": "Jouer", "url": None},
            {"name": "Juice Beauty", "url": None},
            {"name": "JUNO", "url": None},
            {"name": "Kai", "url": None},
            {"name": "KARUNA", "url": None},
            {"name": "Kate Somerville", "url": None},
            {"name": "Kérastase", "url": None},
            {"name": "Kevyn Aucoin", "url": None},
            {"name": "Kiehl's", "url": None},
            {"name": "Kiki de Montparnasse", "url": None},
            {"name": "kit:", "url": None},
            {"name": "Kitsch", "url": None},
            {"name": "Korres", "url": None},
            {"name": "Kosas", "url": None},
            {"name": "Kylie Cosmetics", "url": None},
            {"name": "La Bonne Brosse", "url": None},
            {"name": "LA MER", "url": None},
            {"name": "Lancome", "url": None},
            {"name": "Lanolips", "url": None},
            {"name": "Laura Mercier", "url": None},
            {"name": "Le Labo", "url": None},
            {"name": "Le Sourcil", "url": None},
            {"name": "Lilly Lashes", "url": None},
            {"name": "Living Proof", "url": None},
            {"name": "Lola", "url": None},
            {"name": "LolaVie", "url": None},
            {"name": "LOOPS", "url": None},
            {"name": "LOVEBYT", "url": None},
            {"name": "LoveSeen", "url": None},
            {"name": "M·A·C Cosmetics", "url": None},
            {"name": "Maison Crivelli", "url": None},
            {"name": "Maison Francis Kurkdjian", "url": None},
            {"name": "MAISON MARGIELA", "url": None},
            {"name": "MALIN+GOETZ", "url": None},
            {"name": "Manta", "url": None},
            {"name": "manucurist", "url": None},
            {"name": "Margaret Dabbs London", "url": None},
            {"name": "MARIA TASH", "url": None},
            {"name": "Mario Badescu", "url": None},
            {"name": "MECCA COSMETICA", "url": None},
            {"name": "MECCA M-Power", "url": None},
            {"name": "MECCA MAX", "url": None},
            {"name": "Mecca-ssentials", "url": None},
            {"name": "Merci Handy", "url": None},
            {"name": "Mimétique", "url": None},
            {"name": "Moon Juice", "url": None},
            {"name": "Morphe", "url": None},
            {"name": "Murdock Barbers of London", "url": None},
            {"name": "MUTHA", "url": None},
            {"name": "Naked Sundays", "url": None},
            {"name": "NARS", "url": None},
            {"name": "Naydaya", "url": None},
            {"name": "Nécessaire", "url": None},
            {"name": "NOPALERA", "url": None},
            {"name": "NuFACE", "url": None},
            {"name": "Officine Universelle Buly", "url": None},
            {"name": "Olaplex", "url": None},
            {"name": "Omorovicza", "url": None},
            {"name": "Oribe", "url": None},
            {"name": "Origins", "url": None},
            {"name": "OSEA", "url": None},
            {"name": "Patchology", "url": None},
            {"name": "Paul Smith", "url": None},
            {"name": "Perfumer H", "url": None},
            {"name": "Perricone MD", "url": None},
            {"name": "Philip B.", "url": None},
            {"name": "PHLUR", "url": None},
            {"name": "Polite Society", "url": None},
            {"name": "Pure Mama", "url": None},
            {"name": "Radical Skincare", "url": None},
            {"name": "Rae Morris", "url": None},
            {"name": "REN Clean Skincare", "url": None},
            {"name": "REOME", "url": None},
            {"name": "Rituel de Fille", "url": None},
            {"name": "RMS Beauty", "url": None},
            {"name": "Sachajuan", "url": None},
            {"name": "Sage & Salt", "url": None},
            {"name": "sans [ceuticals]", "url": None},
            {"name": "Shiseido", "url": None},
            {"name": "Sisley", "url": None},
            {"name": "Skinstitut", "url": None},
            {"name": "Slip", "url": None},
            {"name": "Smashbox", "url": None},
            {"name": "Soap & Glory", "url": None},
            {"name": "Sodashi", "url": None},
            {"name": "Sol de Janeiro", "url": None},
            {"name": "SolBiome", "url": None},
            {"name": "Spray Aus", "url": None},
            {"name": "St Soleil", "url": None},
            {"name": "Starface", "url": None},
            {"name": "Stila", "url": None},
            {"name": "Summer Fridays", "url": None},
            {"name": "Sunday Riley", "url": None},
            {"name": "Susanne Kaufmann", "url": None},
            {"name": "Talika", "url": None},
            {"name": "Tammy Fender", "url": None},
            {"name": "Tatcha", "url": None},
            {"name": "The Beauty Chef", "url": None},
            {"name": "The Gut Cø", "url": None},
            {"name": "The Maker", "url": None},
            {"name": "The Naxos Apothecary", "url": None},
            {"name": "The Nue Co.", "url": None},
            {"name": "This Works", "url": None},
            {"name": "To My Ships", "url": None},
            {"name": "TOCCA", "url": None},
            {"name": "Tom Ford", "url": None},
            {"name": "Too Faced", "url": None},
            {"name": "Tower 28", "url": None},
            {"name": "TSU LANGE YOR", "url": None},
            {"name": "TULA", "url": None},
            {"name": "Uni", "url": None},
            {"name": "Urban Decay", "url": None},
            {"name": "Verso Skincare", "url": None},
            {"name": "Victoria Beckham Beauty", "url": None},
            {"name": "Vilhelm Parfumerie", "url": None},
            {"name": "VIOLETTE_FR", "url": None},
            {"name": "Vyrao", "url": None},
            {"name": "Walden", "url": None},
            {"name": "Westman Atelier", "url": None},
            {"name": "Youth To The People", "url": None},
            {"name": "Yves Saint Laurent", "url": None},
            {"name": "ZO Skin Health", "url": None},
        ]
        for brand in additional_brands:
            if brand["name"] not in seen:
                brands.append(brand)
                seen.add(brand["name"])

    print(f"Found {len(brands)} brands", file=sys.stderr)

    print("Initializing rawdata database...", file=sys.stderr)
    try:
        init_rawdata_database_and_schema()
        conn = connect_pg()
        ensure_raw_tables(conn)
    except Exception as e:
        print(f"Error connecting to database: {e}", file=sys.stderr)
        return 1

    print("Inserting brands...", file=sys.stderr)
    try:
        inserted, skipped = insert_brands_to_db(brands, conn)
        print(f"Done! Inserted: {inserted}, Skipped: {skipped}", file=sys.stderr)
    finally:
        conn.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
