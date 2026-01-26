RFC-IMPL-002 — Contract Registry v1 (Loading/Validation) + ContractRef Rules

Status: Accepted
Created: 2026-01-25
Scope: contract-registry 로딩/검증을 "구현 가능한 수준"으로 고정
Depends on: RFC-V4-002, RFC-V4-003, RFC-IMPL-001
Audience: Platform / Runtime Developers / Tooling Developers
Non-Goals: codegen, simulate, diff, replay (tooling 확장), DynamoDB 어댑터 (RFC-IMPL-007)

0. Executive Summary

본 RFC는 **v1 contracts를 런타임에서 로드하는 방식**과 "fail-closed 검증 규칙"을 구현 가능한 수준으로 고정한다.

**v1은 LocalYamlContractRegistryAdapter** (개발/테스트/부트스트랩)를 사용하며,
**운영 환경은 RFC-IMPL-007의 DynamoDBContractRegistryAdapter**로 전환한다.

---

1. Inputs / Outputs

Inputs:
- `src/main/resources/contracts/v1/*.yaml`

Outputs:
- `ContractRegistryPort` 구현이 다음 계약을 로드 가능해야 함:
  - ChangeSetContract
  - JoinSpecContract
  - InvertedIndexContract
  - IndexValueCanonicalizerContract

---

2. Required Rules (Must)

2-1. Fail-Closed
- kind/id/version/status 누락 시 즉시 오류
- enum 값 불일치 시 즉시 오류

2-2. Determinism (RFC-V4-002)
- YAML 로딩은 “해석 차이”가 없도록 **지원 타입/동작을 명시적으로 제한**한다.
  - 허용 타입: string, int/long, boolean, list, map(중첩 map 허용)
  - 금지: timestamp 자동 파싱, custom tag(`!!java` 등), anchor/alias 기반 참조(운영 위험)
  - 숫자는 문자열로 받아서 명시적으로 변환(toLong/toInt)한다 (파서 차이 방지)
- Unknown field 정책: **fail-closed**
  - 계약의 최상위/주요 섹션에서 “정의되지 않은 키” 발견 시 오류로 처리한다.
  - (v1 최소) 다만 호환성 확보를 위해 “허용되는 unknown 영역”이 필요하면 RFC로 별도 정의한다.

2-3. ContractRef
- 런타임 내부 표준 참조 형태는 `ContractRef(id, version)` 유지
- (향후) 문자열 ref 파싱은 tooling에서 책임

---

3. API Shape (Implementation-level)

3-1. ContractRegistryPort 최소 API
- `loadChangeSetContract(ref)`
- `loadJoinSpecContract(ref)`
- `loadInvertedIndexContract(ref)`
- (필요 시) `loadIndexValueCanonicalizerContract(ref)` 추가

---

4. Acceptance Criteria

- `LocalYamlContractRegistryAdapter`는 기본 루트 `/contracts/v1`에서 로드 ✅
- 계약 파일명은 `*.v1.yaml`로 고정 ✅
- 파일명은 힌트일 뿐이며, **SSOT는 본문(meta: kind/id/version/status)** 이다 ✅
- 잘못된 계약(YAML syntax/필수키 누락) 입력 시 fail-closed ✅

---

5. Adapter Roadmap

| Phase | Adapter | 용도 | SSOT |
|-------|---------|------|------|
| v1 | `LocalYamlContractRegistryAdapter` | 개발/테스트/부트스트랩 | `resources/contracts/v1/*.yaml` |
| v2 | `DynamoDBContractRegistryAdapter` | 운영 | DynamoDB Schema Registry |

**전환 원칙**:
- `ContractRegistryPort` 인터페이스는 변경 없음
- DI 설정만 바꾸면 어댑터 교체 완료
- 로컬 YAML은 "초기 seed 데이터"로 DynamoDB에 import하는 용도로 전환

**DynamoDB 구현은 RFC-IMPL-007 참조**
