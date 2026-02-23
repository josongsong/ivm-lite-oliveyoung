# ADR-0022: Product RawData → IVM-Lite Contract DX

**Status**: Accepted  
**Date**: 2026-02  
**Deciders**: Architecture Team  
**RFC**: product-schema-dx-proposal

---

## Context

실제 상품 RawData(UA30953620.json 등)를 IVM-Lite 스키마/룰셋으로 설계할 때 DX와 성능 극대화가 필요했습니다.

## Decision

**Product Schema DX** 설계 원칙을 채택합니다.

### 문서 구조 (3층 분리)

| 구분 | 내용 | 착수 |
|------|------|------|
| **(1) 엔진 확장 없이** | RuleSet, View 4종, productE2E, SinkRule | 즉시 |
| **(2) 작은 확장** | RAW_SCHEMA, 경로 패턴화, View projection | 결정 후 |
| **(3) 엔진 로드맵** | TopoSort 런타임, key-based diff | 나중 |

### 확정 5개

1. **SliceType**: NOTICE, ASSOCIATED 추가
2. **PRODUCT_SEARCH**: requiredSlices = CORE, PRICE, CATEGORY, INDEX
3. **impactMap options**: options 충돌 규칙 계약 패턴화
4. **productE2E**: parse→validate→ingest→view compose→sink dry-run
5. **RAW_SCHEMA**: Raw 검증용 계약 타입 (A안/B안)

### SOTA 잠금 8개

- Determinism, options 충돌, NOTICE 크기, Sink delivery, Sink 개념, RAW_SCHEMA 실패 모드, 네이밍, Sink Payload

## Consequences

### Positive

- ✅ 상품 도메인 설계 명확화
- ✅ 구현 착수 시 논쟁 최소화

### Negative

- ⚠️ 상세 매핑표 유지보수

---

## 참고

- [product-schema-dx-proposal](../rfc_archive/2026-02/product-schema-dx-proposal.md)
