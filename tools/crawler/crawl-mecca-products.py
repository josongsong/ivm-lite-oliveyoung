#!/usr/bin/env python3
"""
MECCA 제품 크롤링 스크립트
카테고리별로 제품 목록과 상세 정보를 수집하여 PostgreSQL에 삽입

사용법:
    python3 tools/crawl-mecca-products.py --category makeup --limit 10
    python3 tools/crawl-mecca-products.py --category skincare --limit 20
"""

import argparse
import json
import re
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, List
from urllib.parse import urljoin, urlparse

import psycopg2
import requests
from bs4 import BeautifulSoup

# rawdata_db 유틸 import
sys.path.append(str(Path(__file__).resolve().parent))
from rawdata_db import connect_pg, ensure_raw_tables, init_rawdata_database_and_schema

BASE_URL = "https://www.mecca.com/en-au"
USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"


def normalize_product_code(product_name: str, brand_name: str) -> str:
    """제품 이름과 브랜드로부터 제품 코드 생성"""
    code = re.sub(r"[^A-Z0-9]", "", (brand_name + product_name).upper())
    return code[:50] if code else f"PRDT_{hash(product_name) % 100000}"


def extract_product_links(listing_url: str, max_pages: int = 5) -> List[Dict]:
    """
    제품 목록 페이지에서 제품 링크 추출
    
    참고: MECCA 웹사이트의 실제 HTML 구조에 따라 선택자를 조정해야 할 수 있습니다.
    필요시 브라우저 개발자 도구로 실제 제품 카드의 클래스명/구조를 확인하세요.
    """
    products = []
    page = 1
    
    while page <= max_pages:
        url = f"{listing_url}?page={page}" if page > 1 else listing_url
        print(f"  Fetching page {page}...", file=sys.stderr)
        
        try:
            response = requests.get(url, timeout=30, headers={"User-Agent": USER_AGENT})
            response.raise_for_status()
            soup = BeautifulSoup(response.text, "html.parser")
            
            # 제품 링크 찾기 (더 구체적인 패턴)
            # MECCA 제품 URL 패턴: /brand-name/product-name/product-code
            all_links = soup.find_all("a", href=True)
            found_count = 0
            seen_urls = set()
            
            for link in all_links:
                href = link.get("href", "")
                if not href or href.startswith("#") or href.startswith("javascript:"):
                    continue
                
                # 절대 URL로 변환
                full_url = urljoin(BASE_URL, href)
                if full_url in seen_urls:
                    continue
                
                # MECCA 도메인이 아니면 제외
                if "mecca.com" not in full_url:
                    continue
                
                # 제품 상세 페이지 패턴 확인: /en-au/brand-name/product-name/ 또는 /en-au/makeup/brand/product/
                url_path = urlparse(full_url).path
                path_parts = [p for p in url_path.strip("/").split("/") if p]
                
                # 최소 3개 파트 필요 (en-au, brand, product)
                if len(path_parts) < 3:
                    continue
                
                # en-au 제거
                if path_parts and path_parts[0] == "en-au":
                    path_parts = path_parts[1:]
                
                # 최소 2개 파트 필요 (brand, product)
                if len(path_parts) < 2:
                    continue
                
                # 카테고리/네비게이션 페이지 제외
                exclude_segments = [
                    "new", "brands", "categories", "makeup", "skincare", 
                    "fragrance", "haircare", "body", "wellness", "men", 
                    "gifts", "shop", "view", "learn", "find", "book",
                    "stores", "services", "help", "account", "wishlist",
                    "services-events", "mecca-memo", "bag", "filter",
                    "sort", "page", "search", "travel-sized", "mini"
                ]
                # 첫 번째나 두 번째 파트가 exclude_segments에 있으면 제외
                if any(seg in path_parts[:2] for seg in exclude_segments):
                    continue
                
                # 제품 페이지는 보통 브랜드명-제품명 형태
                # 예: /mecca-max/off-duty-blush-stick/
                brand_part = path_parts[0]
                product_part = path_parts[1] if len(path_parts) > 1 else ""
                
                # 브랜드명이나 제품명이 너무 짧으면 제외
                if len(brand_part) < 3 or len(product_part) < 5:
                    continue
                
                # 제품명이 하이픈으로 구분된 여러 단어여야 함 (일반적으로)
                # 단, 단일 단어 제품명도 있을 수 있으므로 너무 엄격하지 않게
                if len(product_part.split("-")) == 1 and len(product_part) < 10:
                    # 단일 단어인 경우 더 긴 길이 요구
                    if len(product_part) < 12:
                        continue
                
                # 일반적인 카테고리 단어 제외
                category_words = ["travel", "mini", "concern", "type", "ingredient", 
                                 "wash", "washes", "soap", "sanitizer", "scrubs", 
                                 "exfoliators", "perfumes", "cologne", "extrait"]
                if any(word in product_part.lower() for word in category_words):
                    continue
                
                # URL이 카테고리 페이지처럼 보이면 제외 (3개 이상 파트는 보통 카테고리)
                if len(path_parts) > 2:
                    # 세 번째 파트가 일반적인 카테고리 단어면 제외
                    if path_parts[2] in category_words:
                        continue
                
                # 제품명과 브랜드명 추출
                product_name = None
                brand_name = None
                
                # 링크의 부모 요소에서 정보 찾기
                parent = link.find_parent(["article", "div", "li", "section"])
                if not parent:
                    parent = link
                
                # 제품명 찾기
                name_selectors = [
                    ("h2", re.compile(r"product.*name|product.*title", re.I)),
                    ("h3", re.compile(r"product.*name|product.*title", re.I)),
                    ("span", re.compile(r"product.*name|title", re.I)),
                    ("div", re.compile(r"product.*name|title", re.I)),
                ]
                
                for tag, pattern in name_selectors:
                    name_elem = parent.find(tag, class_=pattern)
                    if name_elem:
                        product_name = name_elem.get_text(strip=True)
                        break
                
                # 브랜드명 찾기
                brand_elem = parent.find(["span", "div", "a"], class_=re.compile(r"brand", re.I))
                if brand_elem:
                    brand_name = brand_elem.get_text(strip=True)
                
                # 링크 텍스트에서 추출 (최후의 수단)
                if not product_name:
                    link_text = link.get_text(strip=True)
                    if link_text and len(link_text) > 5 and len(link_text) < 100:
                        # 여러 줄인 경우 첫 번째는 브랜드, 두 번째는 제품명
                        lines = [l.strip() for l in link_text.split("\n") if l.strip()]
                        if len(lines) >= 2:
                            brand_name = lines[0]
                            product_name = lines[1]
                        else:
                            product_name = link_text
                
                # URL에서 제품명 추출 (최후의 수단)
                if not product_name:
                    product_name = product_part.replace("-", " ").title()
                
                # 유효성 검사
                if not product_name or len(product_name) < 5:
                    continue
                if product_name.lower() in ["new", "view all", "shop now", "learn more", "view products", "add to"]:
                    continue
                
                seen_urls.add(full_url)
                products.append({
                    "name": product_name,
                    "brand": brand_name or "",
                    "url": full_url,
                })
                found_count += 1
            
            if found_count == 0:
                break
                
            page += 1
            time.sleep(1)  # Rate limiting
            
        except Exception as e:
            print(f"  Error fetching page {page}: {e}", file=sys.stderr)
            break
    
    return products


def extract_product_details(product_url: str) -> Optional[Dict]:
    """제품 상세 페이지에서 정보 추출"""
    try:
        response = requests.get(product_url, timeout=30, headers={"User-Agent": USER_AGENT})
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")
        
        details = {}
        
        # 제품 이름
        name_elem = soup.find("h1", class_=re.compile(r"product.*title|name", re.I))
        if not name_elem:
            name_elem = soup.find("h1")
        details["name"] = name_elem.get_text(strip=True) if name_elem else ""
        
        # 브랜드 이름
        brand_elem = soup.find(["a", "span"], class_=re.compile(r"brand", re.I))
        if not brand_elem:
            brand_elem = soup.find("meta", property="product:brand")
        if brand_elem:
            details["brand"] = brand_elem.get("content") if brand_elem.name == "meta" else brand_elem.get_text(strip=True)
        
        # 가격
        price_elem = soup.find(["span", "div"], class_=re.compile(r"price", re.I))
        if price_elem:
            price_text = price_elem.get_text(strip=True)
            price_match = re.search(r"\$?([\d,]+\.?\d*)", price_text)
            if price_match:
                details["price"] = float(price_match.group(1).replace(",", ""))
        
        # 설명
        desc_elem = soup.find("meta", property="og:description")
        if desc_elem:
            details["description"] = desc_elem.get("content", "")
        else:
            desc_elem = soup.find(["div", "p"], class_=re.compile(r"description|detail", re.I))
            if desc_elem:
                details["description"] = desc_elem.get_text(strip=True)[:500]
        
        # 이미지
        img_elem = soup.find("img", class_=re.compile(r"product.*image|hero", re.I))
        if not img_elem:
            img_elem = soup.find("meta", property="og:image")
        if img_elem:
            details["imageUrl"] = img_elem.get("src") or img_elem.get("content") or img_elem.get("data-src")
        
        # 리뷰 수
        review_elem = soup.find(string=re.compile(r"\((\d+)\)"))
        if review_elem:
            review_match = re.search(r"\((\d+)\)", str(review_elem))
            if review_match:
                details["reviewCount"] = int(review_match.group(1))
        
        # 재고 상태
        stock_elem = soup.find(string=re.compile(r"in stock|out of stock|available", re.I))
        if stock_elem:
            details["stockStatus"] = str(stock_elem).strip()
        
        return details
        
    except Exception as e:
        print(f"    Error fetching details: {e}", file=sys.stderr)
        return None


def create_product_document(
    product: Dict,
    category: str,
    listing_info: Optional[Dict] = None
) -> Dict:
    """제품 문서 생성 (스키마 준수)"""
    now = datetime.utcnow().isoformat() + "Z"
    product_code = normalize_product_code(product.get("name", ""), product.get("brand", ""))
    
    # 카테고리 매핑
    category_map = {
        "makeup": {"large": "화장품", "medium": "메이크업", "small": "기타"},
        "skincare": {"large": "화장품", "medium": "스킨케어", "small": "기타"},
        "fragrance": {"large": "향수", "medium": "향수", "small": "기타"},
        "haircare": {"large": "헤어케어", "medium": "헤어케어", "small": "기타"},
        "body": {"large": "바디케어", "medium": "바디케어", "small": "기타"},
    }
    cat_info = category_map.get(category, {"large": "기타", "medium": "기타", "small": "기타"})
    
    brand_name = product.get("brand", "Unknown")
    product_name = product.get("name", "Unknown Product")
    
    return {
        "_meta": {
            "schemaVersion": 1,
            "savedAt": now,
            "clientInfo": {
                "userAgent": USER_AGENT,
                "appVersion": "1.0.0",
            },
        },
        "_audit": {
            "createdBy": "crawler",
            "createdAt": now,
            "updatedBy": "crawler",
            "updatedAt": now,
        },
        "masterInfo": {
            "gtin": f"MECCA{product_code[:10]}",
            "manufacturerGtin": None,
            "gdsCd": product_code,
            "gaCode": None,
            "gdsNm": product_name,
            "gdsEngNm": product_name if re.match(r"^[A-Za-z0-9\s&.'-]+$", product_name) else None,
            "standardCategory": {
                "large": {"code": cat_info["large"][:2].upper(), "name": cat_info["large"]},
                "medium": {"code": cat_info["medium"][:2].upper(), "name": cat_info["medium"]},
                "small": {"code": cat_info["small"][:2].upper(), "name": cat_info["small"]},
            },
            "packaging": None,
            "productDimensions": None,
            "caseDimensions": None,
            "boxDimensions": None,
            "manufacturingCountry": {"code": "AU", "name": "호주"},
            "manufacturer": brand_name,
            "supplier": {"code": "MECCA", "name": "MECCA"},
            "supplierType": "RETAILER",
            "supplierIsImporter": True,
            "md": {"empNo": "MECCA001", "name": "MECCA MD"},
            "scm": {"empNo": "MECCA002", "name": "MECCA SCM"},
            "brand": {
                "code": normalize_product_code(brand_name, ""),
                "krName": brand_name,
                "enName": brand_name,
            },
            "flags": {
                "dermoYn": False,
                "premBrndYn": False,
                "ebGdsYn": True,
                "onlineExclGdsYn": False,
                "harmgdsYn": False,
                "selBanYn": False,
                "medapYn": False,
                "infnSelImpsYn": False,
                "poutTlmtDdNumYn": False,
                "medicalDeviceYn": False,
            },
            "onyoneSpNm": None,
            "buyTypNm": "일반구매",
            "gdsStatNm": "정상",
            "manBabySpNm": None,
            "gdsRegYmd": datetime.now().strftime("%Y-%m-%d"),
            "validPrdDdNum": None,
            "poutTlmtDdNum": None,
            "infnSelImpsYnValue": None,
            "salesEndPlannedDate": None,
            "salesEndDate": None,
            "salesEndReason": None,
            "foodStorageMethod": None,
            "healthSupplementAvailableDays": None,
            "derivingProductYn": False,
            "derivingProduct": None,
            "clearanceYn": False,
            "clearanceBaseDays": None,
            "clearanceDisposalType": None,
            "disposalAllowed": True,
            "returnAllowed": True,
            "shelfLifeManageYn": False,
            "shelfLifeAvailableDays": None,
            "shelfLifeInboundAvailableDays": None,
            "shelfLifeOutboundAvailableDays": None,
        },
        "onlineInfo": {
            "prdtNo": product_code,
            "agoodsNo": None,
            "aGoodsNm": None,
            "prdtName": product_name,
            "onlinePrdtName": product_name,
            "prdtSbttlName": None,
            "prdtStatCode": "01",
            "prdtStatCodeName": "판매중",
            "sellStatCode": "01",
            "sellStatCodeName": "판매중",
            "displayYn": "Y",
            "saleEndText": None,
            "prdtGbnCode": "01",
            "prdtGbnCodeName": "일반상품",
            "onlineMd": {"empNo": "MECCA003", "name": "MECCA Online MD"},
            "onlineBrand": {
                "code": normalize_product_code(brand_name, ""),
                "name": brand_name,
                "useYn": True,
            },
            "orderQuantity": {
                "min": 1,
                "max": 10,
                "increaseUnit": 1,
            },
            "orderLimits": {
                "brandMin": None,
                "brandMax": None,
                "classMin": None,
                "classMax": None,
            },
            "appExcluPrdtYn": False,
        },
        "languageDisplayList": [],
        "shippingInfo": {
            "hsCode": {"code": "3304", "name": "화장품"},
            "exportCategory": {"code": "EXP001", "name": "일반"},
            "posterYn": False,
            "taxCode": None,
            "restrictedCountries": None,
        },
        "reservationSale": {
            "rsvCheckYn": False,
            "restrictionPeriod": None,
            "restrictShipmentYn": False,
            "expectedInbound": None,
        },
        "options": [],
        "displayCategories": [],
        "thumbnailImages": [
            {
                "index": 0,
                "path": product.get("imageUrl", "").split("/")[-1] if product.get("imageUrl") else "",
                "fullUrl": product.get("imageUrl", ""),
                "originalName": None,
                "typeCode": "MAIN",
                "seq": 1,
            }
        ] if product.get("imageUrl") else [],
        "additionalInfo": {
            "srchKeyWordText": f"{brand_name},{product_name}",
        },
        "emblemInfo": {
            "cleanBeautyYn": False,
            "crueltyFreeYn": False,
            "dermaTestedYn": False,
            "glutenFreeYn": False,
            "parabenFreeYn": False,
            "veganYn": False,
        },
        "descriptionInfo": {
            "sellingPoint": product.get("description"),
            "whyWeLoveIt": None,
            "featuredIngredients": None,
            "howToUse": None,
        },
        "techSpecInfo": {
            "type": "HTML",
            "htmlContent": None,
            "images": [],
        },
        "noticeInfo": {
            "noticeItemCode": "NOTICE001",
            "ingredients": None,
        },
        "globalInfo": {
            "prop65": None,
            "prop65Message": None,
        },
        "videoInfo": {
            "exposureType": "MAIN",
            "entries": [],
        },
        "misc": {
            "colorChipUseYn": None,
        },
    }


def insert_products_to_db(products: List[Dict], conn, category: str, limit: Optional[int] = None):
    """제품 데이터를 데이터베이스에 삽입"""
    cursor = conn.cursor()
    inserted = 0
    skipped = 0
    errors = 0
    
    products_to_process = products[:limit] if limit else products
    
    for idx, product in enumerate(products_to_process, 1):
        product_code = normalize_product_code(product.get("name", ""), product.get("brand", ""))
        
        print(f"  [{idx}/{len(products_to_process)}] Processing: {product.get('name', 'Unknown')}", file=sys.stderr)
        
        # 중복 확인
        cursor.execute(
            "SELECT product_id FROM raw_product_document WHERE product_id = %s",
            (product_code,),
        )
        if cursor.fetchone():
            skipped += 1
            continue
        
        # 상세 정보 가져오기
        details = extract_product_details(product.get("url", ""))
        if details:
            product.update(details)
        
        time.sleep(0.5)  # Rate limiting
        
        # 문서 생성
        try:
            document = create_product_document(product, category)
            
            # 삽입
            cursor.execute(
                """
                INSERT INTO raw_product_document (product_id, document)
                VALUES (%s, %s::jsonb)
                ON CONFLICT (product_id) DO NOTHING
                """,
                (product_code, json.dumps(document)),
            )
            inserted += 1
            
            if inserted % 10 == 0:
                conn.commit()
                print(f"    Committed {inserted} products...", file=sys.stderr)
                
        except Exception as e:
            errors += 1
            print(f"    Error creating document: {e}", file=sys.stderr)
            continue
    
    conn.commit()
    cursor.close()
    return inserted, skipped, errors


def main():
    parser = argparse.ArgumentParser(description="MECCA 제품 크롤링")
    parser.add_argument(
        "--category",
        type=str,
        default="makeup",
        choices=["makeup", "skincare", "fragrance", "haircare", "body"],
        help="크롤링할 카테고리",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="처리할 제품 수 제한",
    )
    parser.add_argument(
        "--max-pages",
        type=int,
        default=3,
        help="크롤링할 최대 페이지 수",
    )
    args = parser.parse_args()
    
    listing_url = f"{BASE_URL}/{args.category}/"
    
    print(f"Crawling MECCA {args.category} products...", file=sys.stderr)
    print(f"Listing URL: {listing_url}", file=sys.stderr)
    
    # 제품 링크 추출
    print("Extracting product links...", file=sys.stderr)
    products = extract_product_links(listing_url, max_pages=args.max_pages)
    
    if not products:
        print("No products found!", file=sys.stderr)
        return 1
    
    print(f"Found {len(products)} products", file=sys.stderr)
    
    # 데이터베이스 연결
    print("Initializing rawdata database...", file=sys.stderr)
    try:
        init_rawdata_database_and_schema()
        conn = connect_pg()
        ensure_raw_tables(conn)
    except Exception as e:
        print(f"Error connecting to database: {e}", file=sys.stderr)
        return 1
    
    # 제품 삽입
    print("Inserting products...", file=sys.stderr)
    try:
        inserted, skipped, errors = insert_products_to_db(products, conn, args.category, args.limit)
        print(f"\nDone! Inserted: {inserted}, Skipped: {skipped}, Errors: {errors}", file=sys.stderr)
    finally:
        conn.close()
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
