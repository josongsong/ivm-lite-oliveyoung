# USE Inventory API 연동 가이드

> USE(United States E-commerce) 상품 재고 조회 API 연동 문서

## 개요

| 항목 | 내용 |
|------|------|
| **목적** | USE 상품 재고를 실시간 조회 |
| **Use Case** | 센터코드 및 GTIN 코드로 재고 조회 |
| **Produce Rule** | 실시간 — 재고 정보 필요 시 API 호출 즉시 제공 |
| **사용처** | 재고 정보가 필요한 모든 서비스 |

---

## 기본 정보

| 항목 | 내용 |
|------|------|
| **Method** | POST |
| **Content-Type** | application/json |
| **Dev Endpoint** | `http://dev-inventory.private.oliveyoung.com/api/v1/online/inventory/query` |
| **Prd Endpoint** | (정의 예정) |

---

## Request

### 요청 본문 (JSON)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `gtins` | String[] | O | 조회할 GTIN 목록 |
| `center_codes` | String[] | - | 조회할 센터 목록. 빈 배열 또는 null 시 **전체 센터** 조회 |

### GTIN 형식

- **숫자형**: `1000123425895`, `3232323232`, `8888888888` 등
- **접두사형**: `GTIN000000010`, `GTIN041434210` 등

> 상세 Request/Response 샘플은 [샘플 데이터](#샘플-데이터) 섹션 참조

---

## Response

### 응답 구조

| 경로 | 필드 | 타입 | 설명 |
|------|------|------|------|
| `data` | | Object | 인벤토리 데이터 |
| `data.inventories` | | Array | 인벤토리 목록 |
| `data.inventories[].gtin` | | String | 상품코드(GTIN) |
| `data.inventories[].total_sellable_quantity` | | Int | 판매 가능 수량 (전체 합계) |
| `data.inventories[].centers` | | Array | 센터별 재고 |
| `data.inventories[].centers[].center_code` | | String | 센터 코드 |
| `data.inventories[].centers[].sellable_quantity` | | Int | 센터별 판매 가능 수량 |
| `errors` | | Array | 에러 목록 (정상 시 `[]`) |

### Error Response (에러 발생 시)

| 필드 | 타입 | 설명 |
|------|------|------|
| `error_code` | String | 에러코드 |
| `message` | String | 에러 메시지 |
| `properties` | Map<String, String> | 요청 내역 |
| `exceptional_category` | String | 에러 유형 |

> Error Codes는 별도 정의 예정

---

## 샘플 데이터

### Request 샘플

#### 1. 센터 지정 조회

```json
{
  "gtins": ["GTIN000000010", "GTIN041434210", "8888888888"],
  "center_codes": ["US01"]
}
```

#### 2. 전체 센터 조회

```json
{
  "gtins": ["1000123425895", "GTIN033531588"],
  "center_codes": []
}
```

#### 3. 숫자형 GTIN

```json
{
  "gtins": ["1000123425895", "3232323232", "8899089086218"],
  "center_codes": null
}
```

#### 4. GTIN 접두사형

```json
{
  "gtins": ["GTIN000000010", "GTIN024261923", "GTIN063775033"],
  "center_codes": ["US01", "US02"]
}
```

---

### Response 샘플

#### 1. 단일 상품 (재고 있음)

```json
{
  "data": {
    "inventories": [
      {
        "gtin": "GTIN041434210",
        "total_sellable_quantity": 200880,
        "centers": [
          {
            "center_code": "US01",
            "sellable_quantity": 200880
          }
        ]
      }
    ]
  },
  "errors": []
}
```

#### 2. 단일 상품 (재고 없음)

```json
{
  "data": {
    "inventories": [
      {
        "gtin": "1000123425895",
        "total_sellable_quantity": 0,
        "centers": [
          {
            "center_code": "US01",
            "sellable_quantity": 0
          }
        ]
      }
    ]
  },
  "errors": []
}
```

#### 3. 다중 상품 (혼합 형식)

```json
{
  "data": {
    "inventories": [
      {
        "gtin": "1440769533583",
        "total_sellable_quantity": 23,
        "centers": [
          {
            "center_code": "US01",
            "sellable_quantity": 23
          }
        ]
      },
      {
        "gtin": "8888888888",
        "total_sellable_quantity": 960,
        "centers": [
          {
            "center_code": "US01",
            "sellable_quantity": 960
          }
        ]
      },
      {
        "gtin": "GTIN000554057",
        "total_sellable_quantity": 16315,
        "centers": [
          {
            "center_code": "US01",
            "sellable_quantity": 16315
          }
        ]
      }
    ]
  },
  "errors": []
}
```

#### 4. 에러 응답

```json
{
  "data": null,
  "errors": [
    {
      "error_code": "INVALID_GTIN",
      "message": "잘못된 GTIN 형식입니다",
      "properties": {
        "gtin": "INVALID"
      },
      "exceptional_category": "VALIDATION"
    }
  ]
}
```

---

### 요청-응답 매핑 (검증용)

| Request | Response |
|---------|----------|
| `gtins: ["1000123425895"]` | `total_sellable_quantity: 0` |
| `gtins: ["GTIN041434210"]` | `total_sellable_quantity: 200880` |
| `gtins: ["GTIN033531588"]` | `total_sellable_quantity: 59996` |
| `gtins: ["8888888888"]` | `total_sellable_quantity: 960` |
| `gtins: ["GTIN076078420"]` | `total_sellable_quantity: 9` |

> 위 GTIN은 Dev 환경에서 실제 데이터가 존재하는 샘플입니다.

---

## 연동 예시

### cURL

```bash
curl -X POST "http://dev-inventory.private.oliveyoung.com/api/v1/online/inventory/query" \
  -H "Content-Type: application/json" \
  -d '{
    "gtins": ["1000123425895", "GTIN041434210"],
    "center_codes": []
  }'
```

### Kotlin (Ktor HttpClient)

```kotlin
data class InventoryRequest(
    val gtins: List<String>,
    val center_codes: List<String>? = null
)

data class CenterInventory(
    val center_code: String,
    val sellable_quantity: Int
)

data class InventoryItem(
    val gtin: String,
    val total_sellable_quantity: Int,
    val centers: List<CenterInventory>
)

data class InventoryResponse(
    val data: InventoryData,
    val errors: List<ApiError>
)

data class InventoryData(
    val inventories: List<InventoryItem>
)

suspend fun queryInventory(
    client: HttpClient,
    gtins: List<String>,
    centerCodes: List<String>? = null
): InventoryResponse {
    return client.post("http://dev-inventory.private.oliveyoung.com/api/v1/online/inventory/query") {
        contentType(ContentType.Application.Json)
        setBody(InventoryRequest(gtins = gtins, center_codes = centerCodes))
    }.body()
}
```

### JavaScript/TypeScript (fetch)

```typescript
interface InventoryRequest {
  gtins: string[];
  center_codes?: string[] | null;
}

interface InventoryResponse {
  data: {
    inventories: Array<{
      gtin: string;
      total_sellable_quantity: number;
      centers: Array<{
        center_code: string;
        sellable_quantity: number;
      }>;
    }>;
  };
  errors: unknown[];
}

async function queryInventory(
  gtins: string[],
  centerCodes?: string[] | null
): Promise<InventoryResponse> {
  const res = await fetch(
    "http://dev-inventory.private.oliveyoung.com/api/v1/online/inventory/query",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ gtins, center_codes: centerCodes ?? [] }),
    }
  );
  return res.json();
}
```

---

## 검증 결과 (2026-02)

| 테스트 | 결과 |
|--------|------|
| 기본 호출 (gtins + center_codes) | ✅ 200 OK |
| 전체 센터 조회 (center_codes: []) | ✅ 200 OK |
| center_codes: null | ✅ 200 OK |
| 숫자형 GTIN | ✅ 정상 반환 |
| GTIN 접두사 형식 | ✅ 정상 반환 |
| 79개 GTIN 일괄 조회 | ✅ 전체 매칭, ~760ms |

---

## 주의사항

1. **GTIN 형식**: 숫자형·접두사형 모두 지원. 내부 시스템과 일치하는 형식 사용 권장.
2. **센터 조회**: `center_codes`를 비우거나 null로 보내면 전체 센터 재고가 반환됨.
3. **재고 없음**: 해당 GTIN에 재고가 없어도 `inventories` 배열에 `sellable_quantity: 0`으로 포함됨.
4. **네트워크**: `*.private.oliveyoung.com` 도메인은 내부망/VPN 환경에서 접근 가능.

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-22 | 초안 작성, Dev 환경 검증 완료 |
