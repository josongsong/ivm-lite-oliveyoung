#!/usr/bin/env python3
"""
MECCA 제품 크롤링 스크립트 (Playwright 버전)
JavaScript로 렌더링되는 페이지를 크롤링하여 제품 목록과 상세 정보를 수집

사용법:
    # Playwright 설치 (최초 1회)
    pip install playwright
    playwright install chromium

    # 실행
    python3 tools/crawl-mecca-products-playwright.py --category makeup --limit 10
"""

import argparse
import concurrent.futures
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
from playwright.sync_api import sync_playwright

# rawdata_db 유틸 import
sys.path.append(str(Path(__file__).resolve().parent))
from rawdata_db import connect_pg, ensure_raw_tables, init_rawdata_database_and_schema

BASE_URL = "https://www.mecca.com/en-au"
BASE_SITE_URL = "https://www.mecca.com"

DEFAULT_PAGE_TIMEOUT_MS = 60_000
DEFAULT_NAVIGATION_TIMEOUT_MS = 60_000
DEFAULT_SCROLL_WAIT_SECONDS = 1.0
DEFAULT_MAX_SCROLLS = 60
DEFAULT_CONCURRENCY = 8
DEFAULT_INSERT_BATCH_SIZE = 25
DEFAULT_MAX_CATEGORY_PAGES_TO_SCAN = 80
DEFAULT_MAX_IMAGES_PER_PRODUCT = 20

MECCA_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Safari/537.36"
)

# /en-au/{brand}/{product-slug}-{I|V}-{digits}/
PRODUCT_CODE_IN_URL_PATTERN = re.compile(r"-([IViv]-\d+)(?:/|\\?|$)")
PRODUCT_NUMERIC_ID_IN_URL_PATTERN = re.compile(r"-(\d+)(?:/|\\?|$)")
IMAGE_URL_PATTERN = re.compile(
    r"https://[^\"'\\s]+\\.(?:jpg|jpeg|png|webp)(?:\\?[^\"'\\s]+)?",
    re.IGNORECASE,
)

URL_PATH_BRAND_PRODUCT_PATTERN = re.compile(r"^/en-au/([^/]+)/([^/]+)/?$")


def create_brand_document(brand_name: str) -> Dict:
    """브랜드 문서 생성 (스키마 준수)"""
    now = datetime.utcnow().isoformat() + "Z"
    brand_code = re.sub(r"[^A-Z0-9]", "", brand_name.upper())[:20]
    brand_id = f"MECCA-{brand_code}"
    
    return {
        "brandId": brand_id,
        "brandCode": brand_code,
        "brandName": brand_name,
        "brandNameEn": brand_name,
        "logoUrl": None,
        "bannerUrl": None,
        "description": None,
        "slogan": None,
        "story": None,
        "websiteUrl": None,
        "countryCode": "AU",
        "foundedYear": None,
        "tier": "STANDARD",
        "displayYn": True,
        "searchKeywords": [brand_name.lower()],
        "mainCategoryIds": [],
        "socialLinks": {
            "instagram": None,
            "facebook": None,
            "youtube": None,
            "tiktok": None,
            "twitter": None,
        },
        "tags": ["mecca"],
        "meta": {
            "createdAt": now,
            "updatedAt": now,
            "createdBy": "crawler",
            "updatedBy": "crawler",
            "version": 1,
        },
    }


def create_category_document(category: str, depth: int, parent_id: Optional[str] = None) -> Dict:
    """카테고리 문서 생성 (스키마 준수)"""
    now = datetime.utcnow().isoformat() + "Z"
    
    # MECCA 카테고리 매핑
    category_names = {
        "makeup": {"kr": "메이크업", "en": "Makeup"},
        "skincare": {"kr": "스킨케어", "en": "Skincare"},
        "fragrance": {"kr": "향수", "en": "Fragrance"},
        "haircare": {"kr": "헤어케어", "en": "Haircare"},
        "body": {"kr": "바디케어", "en": "Body"},
        "wellness": {"kr": "웰니스", "en": "Wellness"},
    }
    
    cat_info = category_names.get(category, {"kr": category, "en": category})
    category_code = category.upper()
    category_id = f"MECCA-{category_code}"
    
    # path 구성
    if parent_id:
        path_ids = [parent_id, category_id]
        path_names = ["MECCA", cat_info["kr"]]
        full_path = f"MECCA > {cat_info['kr']}"
    else:
        path_ids = [category_id]
        path_names = [cat_info["kr"]]
        full_path = cat_info["kr"]
    
    return {
        "categoryId": category_id,
        "categoryCode": category_code,
        "categoryName": cat_info["kr"],
        "categoryNameEn": cat_info["en"],
        "parentId": parent_id,
        "depth": depth,
        "sortOrder": 0,
        "path": {
            "ids": path_ids,
            "names": path_names,
            "fullPath": full_path,
        },
        "iconUrl": None,
        "bannerUrl": None,
        "description": f"MECCA {cat_info['en']} products",
        "categoryType": "DISPLAY",
        "displayYn": True,
        "showInNav": True,
        "showInFilter": True,
        "searchKeywords": [category, cat_info["kr"], cat_info["en"].lower()],
        "attributes": [],
        "seo": {
            "title": f"{cat_info['en']} - MECCA",
            "description": f"Shop {cat_info['en']} products from MECCA",
            "keywords": [category, "mecca", cat_info["en"].lower()],
            "canonicalUrl": f"https://www.mecca.com/en-au/{category}/",
        },
        "meta": {
            "createdAt": now,
            "updatedAt": now,
            "createdBy": "crawler",
            "updatedBy": "crawler",
            "version": 1,
        },
    }


def normalize_product_code(product_name: str, brand_name: str, url: str = "") -> str:
    """제품 URL에서 코드를 추출하거나 이름 기반으로 생성"""
    # URL에서 코드 추출 시도 (예: -V-038949, -I-038949)
    product_code = extract_product_code_from_url(url) if url else None
    if product_code:
        return product_code

    # 코드가 없으면 기존 방식대로 이름 기반 생성
    code = re.sub(r"[^A-Z0-9]", "", (brand_name + product_name).upper())
    return code[:50] if code else f"PRDT_{hash(product_name) % 100000}"


def extract_product_code_from_url(url: str) -> Optional[str]:
    """MECCA 제품 상세 URL에서 제품 코드를 추출한다."""
    if not url:
        return None

    match = PRODUCT_CODE_IN_URL_PATTERN.search(url)
    if match:
        return match.group(1).upper()

    match = PRODUCT_NUMERIC_ID_IN_URL_PATTERN.search(url)
    if match:
        return f"ITEM-{match.group(1)}"

    return None


def _title_from_slug(slug: str) -> str:
    parts = [p for p in slug.replace("_", "-").split("-") if p]
    return " ".join(p.capitalize() for p in parts)


def _infer_brand_and_name_from_url(product_url: str) -> Dict:
    parsed = urlparse(product_url)
    match = URL_PATH_BRAND_PRODUCT_PATTERN.match(parsed.path)
    if not match:
        return {"brand": "Unknown", "name": "Unknown Product"}

    brand_slug = match.group(1)
    product_slug = match.group(2)

    # product slug에서 -V-12345 / -I-12345 제거
    product_slug_wo_code = PRODUCT_CODE_IN_URL_PATTERN.sub("", product_slug).strip("-")

    return {
        "brand": _title_from_slug(brand_slug),
        "name": _title_from_slug(product_slug_wo_code),
    }


def fetch_product_details_from_jsonld(product_url: str) -> Optional[Dict]:
    """
    제품 상세 페이지 HTML에서 JSON-LD(Product)를 파싱하여 최소 필드를 추출한다.
    - name
    - brand
    - imageUrls
    - description
    - productCode (sku/mpn)
    """
    try:
        response = requests.get(
            product_url,
            timeout=30,
            headers={"User-Agent": MECCA_USER_AGENT},
        )
        response.raise_for_status()

        html = response.text
        soup = BeautifulSoup(html, "html.parser")
        scripts = soup.find_all("script", {"type": "application/ld+json"})

        extracted_image_urls: List[str] = []

        # JSON-LD(Product)의 image 배열 우선
        for script in scripts:
            raw = script.get_text(strip=True)
            if not raw:
                continue

            try:
                data = json.loads(raw)
            except Exception:
                continue

            candidates: List[Dict] = []
            if isinstance(data, dict):
                candidates = [data]
            elif isinstance(data, list):
                candidates = [d for d in data if isinstance(d, dict)]

            for candidate in candidates:
                if candidate.get("@type") != "Product":
                    continue

                name = candidate.get("name")
                description = candidate.get("description")
                sku = candidate.get("sku") or candidate.get("mpn") or extract_product_code_from_url(product_url)

                brand = candidate.get("brand")
                brand_name: Optional[str] = None
                if isinstance(brand, dict):
                    brand_name = brand.get("name")
                elif isinstance(brand, str):
                    brand_name = brand

                image = candidate.get("image")
                if isinstance(image, str):
                    extracted_image_urls.append(image)
                elif isinstance(image, list):
                    for item in image:
                        if isinstance(item, str):
                            extracted_image_urls.append(item)

                if not name:
                    continue

                # HTML 전체에서 이미지 URL도 보조로 수집 (JSON-LD에 갤러리가 부족한 경우 보완)
                # - 너무 과도한 수집을 막기 위해 상한을 둔다.
                for match in IMAGE_URL_PATTERN.finditer(html):
                    if len(extracted_image_urls) >= DEFAULT_MAX_IMAGES_PER_PRODUCT:
                        break
                    url = match.group(0)
                    # Mecca 콘텐츠 허브/이미지 위주로 필터링
                    if "contenthub" not in url and "mecca" not in url:
                        continue
                    extracted_image_urls.append(url)

                # 정리: 중복 제거 + 안정적 순서 유지 + 상한 적용
                deduped: List[str] = []
                seen: set = set()
                for u in extracted_image_urls:
                    if not u:
                        continue
                    if u in seen:
                        continue
                    seen.add(u)
                    deduped.append(u)
                    if len(deduped) >= DEFAULT_MAX_IMAGES_PER_PRODUCT:
                        break

                return {
                    "name": name,
                    "brand": brand_name or "Unknown",
                    "url": product_url,
                    "imageUrls": deduped,
                    "imageUrl": deduped[0] if deduped else None,  # 호환용(첫 장)
                    "description": description,
                    "productCode": sku,
                }

        # JSON-LD(Product)가 없더라도, URL 코드가 있는 경우에 한해 최소 정보로 백업한다.
        # (페이지 구조 변경/부분 로드/블록 등으로 JSON-LD가 비어도 이미지/코드만이라도 확보)
        if extract_product_code_from_url(product_url) is None:
            return None

        for match in IMAGE_URL_PATTERN.finditer(html):
            if len(extracted_image_urls) >= DEFAULT_MAX_IMAGES_PER_PRODUCT:
                break
            url = match.group(0)
            if "contenthub" not in url and "mecca" not in url:
                continue
            extracted_image_urls.append(url)

        deduped: List[str] = []
        seen: set = set()
        for u in extracted_image_urls:
            if not u:
                continue
            if u in seen:
                continue
            seen.add(u)
            deduped.append(u)
            if len(deduped) >= DEFAULT_MAX_IMAGES_PER_PRODUCT:
                break

        inferred = _infer_brand_and_name_from_url(product_url)
        return {
            "name": inferred["name"],
            "brand": inferred["brand"],
            "url": product_url,
            "imageUrls": deduped,
            "imageUrl": deduped[0] if deduped else None,
            "description": None,
            "productCode": extract_product_code_from_url(product_url),
        }

    except Exception as e:
        print(f"  Error fetching JSON-LD: {e}", file=sys.stderr)
        return None


def create_product_document(
    product: Dict,
    category: str
) -> Dict:
    """제품 문서 생성 (스키마 준수)"""
    now = datetime.utcnow().isoformat() + "Z"
    brand_name = product.get("brand", "Unknown")
    product_name = product.get("name", "Unknown Product")
    product_url = product.get("url", "")
    
    # URL에서 ID 추출하여 코드 생성
    product_code = normalize_product_code(product_name, brand_name, product_url)
    
    # 카테고리 매핑
    category_map = {
        "makeup": {"large": "화장품", "medium": "메이크업", "small": "기타"},
        "skincare": {"large": "화장품", "medium": "스킨케어", "small": "기타"},
        "fragrance": {"large": "향수", "medium": "향수", "small": "기타"},
        "haircare": {"large": "헤어케어", "medium": "헤어케어", "small": "기타"},
        "body": {"large": "바디케어", "medium": "바디케어", "small": "기타"},
    }
    cat_info = category_map.get(category, {"large": "기타", "medium": "기타", "small": "기타"})
    
    image_urls: List[str] = product.get("imageUrls") or []
    if not image_urls and product.get("imageUrl"):
        image_urls = [product.get("imageUrl")]

    thumbnail_images: List[Dict] = []
    for idx, img_url in enumerate(image_urls):
        if not img_url:
            continue
        path = urlparse(img_url).path or img_url.split("?")[0]
        thumbnail_images.append(
            {
                "index": idx,
                "path": path,
                "fullUrl": img_url,
                "originalName": None,
                "typeCode": "MAIN" if idx == 0 else "SUB",
                "seq": idx,
            }
        )

    # 상세 이미지(스펙 이미지)로도 동일한 이미지 리스트를 사용한다.
    # - 스키마: type=IMAGE/MIXED에서 images[].seq, images[].url 필요
    tech_spec_images = [{"seq": idx, "url": u} for idx, u in enumerate(image_urls) if u]
    tech_spec_info = (
        {"type": "IMAGE", "images": tech_spec_images}
        if tech_spec_images
        else {"type": "HTML", "htmlContent": "<div></div>", "images": []}
    )

    return {
        "_meta": {
            "schemaVersion": 1,
            "savedAt": now,
            "clientInfo": {
                "userAgent": "Playwright Crawler",
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
            "gdsEngNm": product_name,
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
        "thumbnailImages": thumbnail_images,
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
        "techSpecInfo": tech_spec_info,
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


def crawl_single_product(product_url: str, category: str):
    product_data = fetch_product_details_from_jsonld(product_url)
    if not product_data:
        print(f"Failed to fetch product details: {product_url}", file=sys.stderr)
        return

    init_rawdata_database_and_schema()
    conn = connect_pg()
    ensure_raw_tables(conn)
    cursor = conn.cursor()

    # 카테고리 upsert
    cat_doc = create_category_document(category, depth=1, parent_id=None)
    cursor.execute(
        """
        INSERT INTO raw_category_document (category_id, document)
        VALUES (%s, %s::jsonb)
        ON CONFLICT (category_id) DO UPDATE SET document = EXCLUDED.document
        """,
        (cat_doc["categoryId"], json.dumps(cat_doc)),
    )

    # 브랜드 upsert
    brand_name = product_data.get("brand") or "Unknown"
    if brand_name != "Unknown":
        brand_doc = create_brand_document(brand_name)
        cursor.execute(
            """
            INSERT INTO raw_brand_document (brand_id, document)
            VALUES (%s, %s::jsonb)
            ON CONFLICT (brand_id) DO UPDATE SET document = EXCLUDED.document
            """,
            (brand_doc["brandId"], json.dumps(brand_doc)),
        )

    doc = create_product_document(product_data, category)
    product_id = doc["masterInfo"]["gdsCd"]

    cursor.execute(
        """
        INSERT INTO raw_product_document (product_id, document)
        VALUES (%s, %s::jsonb)
        ON CONFLICT (product_id) DO UPDATE SET document = EXCLUDED.document
        """,
        (product_id, json.dumps(doc)),
    )

    conn.commit()
    conn.close()
    print(f"Upserted product_id={product_id} from {product_url}", file=sys.stderr)


def crawl_mecca(category: str, limit: int, update_existing: bool):
    with sync_playwright() as p:
        # 브라우저 실행 시 User-Agent 설정
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            user_agent=MECCA_USER_AGENT
        )
        page = context.new_page()
        
        listing_url = f"{BASE_URL}/{category}/"
        print(f"Navigating to {listing_url}", file=sys.stderr)
        
        try:
            # 타임아웃 증가 및 대기 조건 완화
            page.set_default_timeout(DEFAULT_PAGE_TIMEOUT_MS)
            page.goto(listing_url, timeout=DEFAULT_NAVIGATION_TIMEOUT_MS)
            page.wait_for_load_state("domcontentloaded")
        except Exception as e:
            print(f"Error loading page: {e}", file=sys.stderr)
            # 계속 진행 시도 (부분 로드되었을 수도 있음)

        # 카테고리 루트 페이지는 “섹션/서브카테고리” 중심이라 제품이 제한적으로만 노출되는 경우가 많다.
        # 따라서 (1) 루트 페이지에서 서브카테고리 URL을 모으고, (2) 각 서브카테고리 페이지를 순회하며 제품 URL을 수집한다.
        print("Collecting subcategory pages and product links...", file=sys.stderr)

        product_links: List[str] = []
        seen_product_urls: set = set()
        seen_page_urls: set = set()

        exclude_segments = {
            "new", "brands", "categories",
            "gifts", "services-events", "mecca-memo", "bag", "wishlist",
            "account", "help", "stores", "terms", "privacy", "search",
        }
        exclude_category_pages = {
            "foundation-finder",
        }

        def _extract_hrefs() -> List[str]:
            try:
                return page.eval_on_selector_all("a[href]", "els => els.map(e => e.getAttribute('href'))")
            except Exception:
                return []

        def _to_full_url(href: str) -> str:
            # href 형태가 '/en-au/..' 또는 'en-au/..' 둘 다 존재하므로 site root 기준으로 조인한다.
            return urljoin(f"{BASE_SITE_URL}/", href)

        def _is_same_category_page(full_url: str) -> bool:
            path_parts = [p for p in urlparse(full_url).path.strip("/").split("/") if p]
            if path_parts and path_parts[0] == "en-au":
                path_parts = path_parts[1:]
            if not path_parts or path_parts[0] != category:
                return False
            if any(part in exclude_segments for part in path_parts):
                return False
            if any(part in exclude_category_pages for part in path_parts):
                return False
            return True

        def _is_product_url(full_url: str) -> bool:
            if not full_url.startswith(f"{BASE_SITE_URL}/en-au/"):
                return False
            if extract_product_code_from_url(full_url) is None:
                return False
            return True

        pages_to_scan: List[str] = [listing_url]
        seen_page_urls.add(listing_url)

        # 1) 루트 페이지에서 서브카테고리 URL 수집
        hrefs = _extract_hrefs()
        for href in hrefs:
            if not href:
                continue
            full_url = _to_full_url(href)
            if full_url in seen_page_urls:
                continue
            if not _is_same_category_page(full_url):
                continue
            if _is_product_url(full_url):
                continue
            pages_to_scan.append(full_url)
            seen_page_urls.add(full_url)
            if len(pages_to_scan) >= DEFAULT_MAX_CATEGORY_PAGES_TO_SCAN:
                break

        # 2) 각 페이지를 순회하며 제품 URL 수집
        for idx, page_url in enumerate(pages_to_scan):
            if len(product_links) >= limit * 3:
                break

            if idx > 0:
                try:
                    page.goto(page_url, timeout=DEFAULT_NAVIGATION_TIMEOUT_MS)
                    page.wait_for_load_state("domcontentloaded")
                except Exception as e:
                    print(f"  Error loading subpage: {page_url} ({e})", file=sys.stderr)
                    continue

            # 일부 페이지는 스크롤 후에만 제품 카드가 렌더링되기도 함
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
            time.sleep(DEFAULT_SCROLL_WAIT_SECONDS)

            sub_hrefs = _extract_hrefs()
            for href in sub_hrefs:
                if not href:
                    continue
                full_url = _to_full_url(href)
                if full_url in seen_product_urls:
                    continue
                if not _is_product_url(full_url):
                    continue
                seen_product_urls.add(full_url)
                product_links.append(full_url)

        print(f"Found {len(product_links)} potential product links across {len(pages_to_scan)} pages", file=sys.stderr)
        
        # DB 연결
        try:
            init_rawdata_database_and_schema()
            conn = connect_pg()
            ensure_raw_tables(conn)
            cursor = conn.cursor()
        except Exception as e:
            print(f"DB Connection failed: {e}", file=sys.stderr)
            browser.close()
            return

        # 카테고리 저장
        print(f"Saving category: {category}", file=sys.stderr)
        cat_doc = create_category_document(category, depth=1, parent_id=None)
        cursor.execute(
            """
            INSERT INTO raw_category_document (category_id, document)
            VALUES (%s, %s::jsonb)
            ON CONFLICT (category_id) DO UPDATE SET document = EXCLUDED.document
            """,
            (cat_doc["categoryId"], json.dumps(cat_doc))
        )
        conn.commit()

        # 브랜드 추적 (중복 저장 방지)
        saved_brands: set = set()

        cursor.execute("SELECT product_id FROM raw_product_document")
        existing_product_ids = {row[0] for row in cursor.fetchall()}

        urls_to_fetch: List[str] = []
        for url in product_links:
            code = extract_product_code_from_url(url)
            if not code:
                continue
            if not update_existing and code in existing_product_ids:
                continue
            urls_to_fetch.append(url)

        print(
            f"Need to fetch up to {limit} products. Candidate URLs: {len(urls_to_fetch)} (update_existing={update_existing})",
            file=sys.stderr,
        )

        inserted = 0
        fetched = 0
        failed = 0

        def _fetch(url: str) -> Optional[Dict]:
            return fetch_product_details_from_jsonld(url)

        with concurrent.futures.ThreadPoolExecutor(max_workers=DEFAULT_CONCURRENCY) as executor:
            futures: List[concurrent.futures.Future] = []
            for url in urls_to_fetch:
                if inserted + len(futures) >= limit:
                    break
                futures.append(executor.submit(_fetch, url))

            for future in concurrent.futures.as_completed(futures):
                if inserted >= limit:
                    break

                product_data = future.result()
                fetched += 1

                if not product_data:
                    failed += 1
                    continue

                brand_name = product_data.get("brand") or "Unknown"
                product_name = product_data.get("name") or "Unknown Product"
                product_url = product_data.get("url") or ""

                # JSON-LD sku가 있으면 우선 사용
                product_code = (product_data.get("productCode") or extract_product_code_from_url(product_url) or "").upper()
                if product_code:
                    product_data["url"] = product_url
                else:
                    product_code = normalize_product_code(product_name, brand_name, product_url)

                # 브랜드 저장 (최초 1회)
                if brand_name and brand_name != "Unknown" and brand_name not in saved_brands:
                    brand_doc = create_brand_document(brand_name)
                    cursor.execute(
                        """
                        INSERT INTO raw_brand_document (brand_id, document)
                        VALUES (%s, %s::jsonb)
                        ON CONFLICT (brand_id) DO UPDATE SET document = EXCLUDED.document
                        """,
                        (brand_doc["brandId"], json.dumps(brand_doc))
                    )
                    saved_brands.add(brand_name)

                # 제품 DB 저장
                doc = create_product_document(product_data, category)
                product_id = doc["masterInfo"]["gdsCd"]

                if update_existing:
                    cursor.execute(
                        """
                        INSERT INTO raw_product_document (product_id, document)
                        VALUES (%s, %s::jsonb)
                        ON CONFLICT (product_id) DO UPDATE SET document = EXCLUDED.document
                        """,
                        (product_id, json.dumps(doc))
                    )
                    inserted += 1
                    existing_product_ids.add(product_id)
                else:
                    cursor.execute(
                        """
                        INSERT INTO raw_product_document (product_id, document)
                        VALUES (%s, %s::jsonb)
                        ON CONFLICT (product_id) DO NOTHING
                        """,
                        (product_id, json.dumps(doc))
                    )

                    if cursor.rowcount == 1:
                        inserted += 1
                        existing_product_ids.add(product_id)

                if inserted % DEFAULT_INSERT_BATCH_SIZE == 0:
                    conn.commit()
                    print(f"  Committed {inserted} new products...", file=sys.stderr)

        conn.commit()
        print(f"Done. Inserted {inserted}. Fetched {fetched}. Failed {failed}.", file=sys.stderr)
        conn.close()
        browser.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MECCA Playwright Crawler")
    parser.add_argument("--category", type=str, default="makeup")
    parser.add_argument("--limit", type=int, default=10, help="추가로 삽입할 신규 제품 수")
    parser.add_argument("--product-url", type=str, default=None, help="단일 상품 URL만 크롤링/업서트")
    parser.add_argument(
        "--update-existing",
        action="store_true",
        help="기존 product_id가 있어도 문서를 갱신한다 (이미지/설명 백필 용도)",
    )
    
    args = parser.parse_args()
    if args.product_url:
        crawl_single_product(args.product_url, args.category)
    else:
        crawl_mecca(args.category, args.limit, args.update_existing)
