# ADR-0014: DOC-001 상품 마스터(Flat Catalog) 온보딩

**Status**: Accepted  
**Date**: 2026-01-26  
**Deciders**: Architecture Team  
**RFC**: RFC-IR-031 (RFC-011)

---

## Context

DOC-001 상품마스터데이터(Flat Catalog) Raw Document를 ivm-lite 파이프라인에 온보딩하기 위한 계약(Contract)과 룰(RuleSet), 조인(JoinSpec), 뷰(ViewDefinition), 싱크(Ship to OpenSearch/Personalize) 설계가 필요했습니다.

DOC-001의 특성:
- Authoring Form(A0)의 원본 입력 상태를 나타내는 Raw Document
- 구조가 깊고(섹션 다수), 일부 필드가 optional
- 화면/운영 변화로 스키마가 자주 진화

## Decision

**DOC-001 온보딩 전략**을 채택합니다.

### 핵심 원칙

1. **Raw(DOC-001)은 Read 경로에서 직접 사용 금지**: IR Builder 입력으로만 사용. 결정성/증분을 위해 Raw는 Canonical JSON으로 저장
2. **Slice는 소수의 안정적 SliceType만 물리화**: 조회/조인/증분에 최적화된 SliceType만 저장, 필요 시 Ship 단계에서 타겟 문서 생성
3. **Join은 단일 hop LOOKUP만 허용**: JoinSpecContract 제약 준수, fanout은 InvertedIndexContract로 관리
4. **View는 필수/선택 Slice + MissingPolicy 명시**: ViewDefinitionContract로 정책 명시
5. **Sink는 Ship 단계에서 타겟별 변환**: 멱등 업서트/삭제로 반영

### 식별자/버전 규칙

- **Schema ID**: `raw.product.doc001.v1`
- **Schema Version**: SemVer `1.0.0`
- **EntityKey**: `PRODUCT#{tenantId}#{onlineInfo.prdtNo}`
- **Version**: source-of-truth에서 monotonic increasing

### Slice 설계

물리 SliceType (6개로 제한):
- **CORE**: 식별/명칭/브랜드/상태/플래그
- **PRICE**: 가격/할인/마진
- **INVENTORY**: 판매 가능/주문 제한/재고 관련
- **MEDIA**: 썸네일/비디오 요약
- **CATEGORY**: 노출 카테고리/표준 카테고리 요약
- **DERIVED**: 파생 필드(버킷/정규화 상태/검색 필터용)

### Join 설계

- **Brand Join**: `/masterInfo/brand/code` → `BRAND#{tenantId}#{value}`
- **Category Join**: `/displayCategories/0/sclsCtgrNo` → `CATEGORY#{tenantId}#{value}`
- 모두 required: false (브랜드/카테고리 미정 상품 허용)

### View 설계

- **PDP View**: CORE + PRICE + INVENTORY + MEDIA + CATEGORY (필수)
- **Search View**: CORE + DERIVED + CATEGORY (필수)
- **Cart View**: CORE + PRICE + INVENTORY (필수)

### Sink 설계

- **OpenSearch**: Ship 단계에서 검색 문서 생성 (멱등 업서트)
- **Personalize**: Ship 단계에서 추천 피드 문서 생성 (멱등 업서트)

## Consequences

### Positive

- ✅ 결정성/증분 처리 보장
- ✅ 조인/팬아웃 제어 가능
- ✅ 멀티 타겟 지원 (OpenSearch/Personalize)
- ✅ 확장 가능한 설계

### Negative

- ⚠️ 초기 설계 비용 증가
- ⚠️ Ship 단계 변환 로직 필요
- ⚠️ 버전 관리 복잡도

### Neutral

- 계약 작성 및 관리 오버헤드
- 마이그레이션 전략 수립 필요

---

## 참고

- [RFC-IR-031](../rfc/rfc011-doc001-flat-catalog.md) - 원본 RFC 문서
- [ADR-0001](./0001-contract-first-architecture.md) - Contract-First 아키텍처
- [ADR-0006](./0006-slicing-join-scope.md) - Slicing Join 허용 범위
