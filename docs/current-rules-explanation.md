# 현재 등록된 규칙 설명서

**작성일**: 2026-01-29  
**목적**: IVM-Lite 시스템에 등록된 모든 규칙을 이해하기 쉽게 설명

---

## 📋 목차

1. [Product 슬라이싱 규칙 (RuleSet)](#1-product-슬라이싱-규칙-ruleset)
2. [Brand 슬라이싱 규칙](#2-brand-슬라이싱-규칙)
3. [Product 상세 뷰 규칙](#3-product-상세-뷰-규칙)
4. [OpenSearch 전송 규칙](#4-opensearch-전송-규칙)
5. [데이터 구조 정의 (Entity Schema)](#5-데이터-구조-정의-entity-schema)

---

## 1. Product 슬라이싱 규칙 (RuleSet)

### 📄 파일: `ruleset.v1.yaml` (기본 규칙)

**목적**: Product 원본 데이터를 용도별로 나누어 저장하는 규칙

#### 🎯 핵심 개념

**"Product 데이터가 변경되면, 어떤 슬라이스를 다시 만들어야 할까?"**

이 규칙은 **어떤 필드가 변경되었을 때 어떤 슬라이스를 재생성해야 하는지** 정의합니다.

#### 📦 생성되는 슬라이스 종류

1. **CORE 슬라이스** (핵심 정보)
   - **포함 내용**: 제품의 기본 정보 (제목, 브랜드, 가격)
   - **변경 감지**: `/title`, `/brand`, `/price` 필드가 변경되면 재생성
   - **특별 기능**: Brand 정보를 JOIN하여 `brandName`, `brandLogoUrl`을 추가
     - Brand가 없어도 슬라이싱은 진행됨 (required: false)
     - Brand의 모든 정보가 아닌, 필요한 필드만 선택적으로 가져옴 (projection)

2. **PRICE 슬라이스** (가격 정보)
   - **포함 내용**: 가격 관련 정보만 (`price`, `salePrice`, `discount`)
   - **변경 감지**: 가격 관련 필드가 변경되면 재생성
   - **용도**: 가격 조회 API에서 빠르게 응답

3. **INVENTORY 슬라이스** (재고 정보)
   - **포함 내용**: 재고 관련 정보 (`stock`, `availability`)
   - **변경 감지**: 재고 관련 필드가 변경되면 재생성
   - **용도**: 재고 확인 API

4. **MEDIA 슬라이스** (미디어 정보)
   - **포함 내용**: 이미지, 동영상 정보 (`images`, `videos`)
   - **변경 감지**: 미디어 관련 필드가 변경되면 재생성

5. **CATEGORY 슬라이스** (카테고리 정보)
   - **포함 내용**: 카테고리 ID와 경로 (`categoryId`, `categoryPath`)
   - **변경 감지**: 카테고리 관련 필드가 변경되면 재생성

#### 🔗 자동 재슬라이싱 (Fanout)

**"Brand가 변경되면, 관련된 모든 Product를 자동으로 다시 슬라이싱한다"**

- **Brand 인덱스**: Brand ID가 변경되면, 해당 Brand를 사용하는 모든 Product의 CORE 슬라이스를 자동 재생성
  - 최대 10,000개까지 자동 처리 (circuit breaker)
- **Category 인덱스**: Category가 변경되면, 해당 Category에 속한 모든 Product를 자동 재생성
  - 최대 50,000개까지 자동 처리 (카테고리는 연관 상품이 많을 수 있음)

---

### 📄 파일: `ruleset-product-doc001.v1.yaml` (실제 운영 규칙)

**목적**: 실제 올리브영 시스템의 복잡한 Product 데이터 구조를 처리하는 규칙

#### 🎯 주요 차이점

기본 규칙보다 **훨씬 더 세밀하게** 정의되어 있습니다:

1. **더 많은 필드 추적**
   - `/_meta/schemaVersion`, `/_meta/savedAt` (메타데이터)
   - `/masterInfo/*` (마스터 정보 전체)
   - `/onlineInfo/*` (온라인 정보 전체)

2. **ENRICHED 슬라이스 추가**
   - Brand 정보를 JOIN하여 `brandName`, `brandLogoUrl`을 추가한 특별한 슬라이스
   - Brand 코드가 변경되면 이 슬라이스만 재생성 (CORE는 유지)

3. **INDEX 슬라이스**
   - 검색용 인덱스 데이터를 별도로 저장
   - 검색 키워드, 플래그, 옵션 정보 포함

---

## 2. Brand 슬라이싱 규칙

### 📄 파일: `ruleset-brand.v1.yaml`

**목적**: Brand 원본 데이터를 슬라이싱하는 규칙

#### 📦 생성되는 슬라이스

1. **SUMMARY 슬라이스** (요약 정보)
   - Brand의 핵심 정보만 포함 (이름, 로고 URL 등)
   - 다른 엔티티에서 Brand 정보를 참조할 때 사용
   - **용도**: Product에서 Brand 정보를 JOIN할 때 이 슬라이스를 사용

#### 🔗 자동 재슬라이싱

- Brand가 변경되면, 이 Brand를 참조하는 모든 Product가 자동으로 재슬라이싱됨

---

## 3. Product 상세 뷰 규칙

### 📄 파일: `view-product-detail.v1.yaml`

**목적**: 여러 슬라이스를 조합하여 Product 상세 페이지에 필요한 데이터를 만드는 규칙

#### 🎯 핵심 개념

**"상세 페이지를 보여주려면, CORE + PRICE + MEDIA + REVIEW 슬라이스를 합쳐야 해"**

#### 📋 필요한 슬라이스

- **필수 슬라이스**: CORE, PRICE, MEDIA, REVIEW
- **선택적 슬라이스**: PROMOTION (프로모션이 있으면 포함)

#### 🔄 동작 방식

1. Product ID로 각 슬라이스를 조회
2. 모든 슬라이스를 하나의 JSON으로 합침
3. 클라이언트에 반환

#### ⚠️ 슬라이스가 없는 경우

- **일부 슬라이스가 없어도**: 상세 페이지를 보여줄 수 있음 (PARTIAL_ALLOWED)
- **어떤 슬라이스가 없는지**: 응답 메타데이터에 포함하여 클라이언트가 알 수 있음

---

## 4. OpenSearch 전송 규칙

### 📄 파일: `sinkrule-opensearch-product.v1.yaml`

**목적**: Product 데이터를 OpenSearch (검색 엔진)에 자동으로 전송하는 규칙

#### 🎯 핵심 개념

**"Product가 변경되면, OpenSearch에도 자동으로 업데이트해줘"**

#### 📋 전송 조건

- **어떤 슬라이스가 변경되면**: CORE, PRICE, MEDIA, CATEGORY
- **어떤 뷰를 사용**: `view-product-search.v1` (검색용 뷰)
- **전송 대상**: OpenSearch 인덱스

#### 🔄 동작 방식

1. Product의 CORE, PRICE, MEDIA, CATEGORY 슬라이스 중 하나가 변경됨
2. `view-product-search.v1` 뷰를 사용하여 데이터 조합
3. OpenSearch에 자동으로 인덱싱

---

## 5. 데이터 구조 정의 (Entity Schema)

### 📄 파일: `entity-product.v1.yaml`, `entity-brand.v1.yaml`, `entity-category.v1.yaml`

**목적**: 각 엔티티의 데이터 구조를 정의

#### 🎯 핵심 개념

**"Product 데이터는 이런 구조로 들어와야 해"**

#### 📋 정의 내용

- **필수 필드**: 반드시 있어야 하는 필드
- **선택 필드**: 없어도 되는 필드
- **필드 타입**: 문자열, 숫자, 객체, 배열 등
- **필드 제약**: 최대 길이, 최소값, 최대값 등

#### 🔄 용도

1. **데이터 검증**: 들어오는 데이터가 올바른 구조인지 확인
2. **스키마 버전 관리**: 스키마가 변경되면 버전을 올려서 호환성 관리
3. **문서화**: 개발자가 데이터 구조를 쉽게 이해할 수 있음

---

## 🔄 전체 데이터 흐름 요약

```
1. RawData 입력
   ↓
2. RuleSet에 따라 슬라이싱 (CORE, PRICE, MEDIA 등)
   ↓
3. ViewDefinition에 따라 슬라이스 조합 (상세 페이지용 데이터 생성)
   ↓
4. SinkRule에 따라 외부 시스템 전송 (OpenSearch 인덱싱)
```

---

## 💡 실전 예시

### 시나리오: "이니스프리 브랜드 이름이 변경되었어요"

1. **Brand RawData 업데이트**
   - Brand 원본 데이터에서 `brandName`이 "이니스프리" → "이니스프리 (NEW)"로 변경

2. **Brand SUMMARY 슬라이스 재생성**
   - `ruleset-brand.v1.yaml`에 따라 SUMMARY 슬라이스 재생성

3. **자동 Fanout 트리거**
   - `ruleset.v1.yaml`의 `indexes.brand.references: BRAND`에 의해
   - 이니스프리 브랜드를 사용하는 모든 Product의 CORE 슬라이스가 자동 재생성

4. **Projection 적용**
   - CORE 슬라이스 재생성 시, Brand의 `brandName`만 선택적으로 가져옴
   - Product CORE 슬라이스에 `brandName: "이니스프리 (NEW)"` 자동 반영

5. **View 자동 업데이트**
   - Product 상세 페이지를 조회하면 새로운 브랜드 이름이 표시됨

6. **OpenSearch 자동 인덱싱**
   - CORE 슬라이스가 변경되었으므로 OpenSearch도 자동 업데이트

**결과**: 브랜드 이름 하나만 변경했는데, 관련된 모든 Product가 자동으로 업데이트됨! 🎉

---

## 📊 규칙 통계

- **RuleSet**: 3개 (Product 기본, Product 실제, Brand)
- **ViewDefinition**: 6개 (상세, 검색, 장바구니, 브랜드 상세 등)
- **SinkRule**: 1개 (OpenSearch)
- **EntitySchema**: 3개 (Product, Brand, Category)

**총 13개 규칙 파일**이 시스템을 제어하고 있습니다.

---

## 🎓 핵심 개념 정리

1. **RuleSet**: "어떻게 데이터를 나눌까?"
2. **ViewDefinition**: "어떻게 데이터를 합칠까?"
3. **SinkRule**: "어디로 데이터를 보낼까?"
4. **EntitySchema**: "데이터 구조는 어떻게 생겼을까?"
5. **ImpactMap**: "어떤 필드가 변경되면 어떤 슬라이스를 재생성할까?"
6. **Fanout**: "관련된 다른 데이터도 자동으로 업데이트할까?"
7. **Projection**: "JOIN할 때 필요한 필드만 선택적으로 가져올까?"

---

이 규칙들이 모두 함께 작동하여, **데이터 변경 시 자동으로 관련된 모든 데이터를 일관되게 업데이트**하는 시스템을 만듭니다! 🚀
