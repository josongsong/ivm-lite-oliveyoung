# 문서 규칙 — Documentation Rules

이 문서는 IVM-Lite 프로젝트의 모든 Markdown 문서에 적용되는 규칙을 정의합니다.  
**lint-docs** 명령으로 강제 검사됩니다.

---

## 1. 디렉토리 구조

```
docs/
├── adr/              # ADR만 (NNNN-슬러그.md)
├── guides/           # 개발 가이드
├── proposals/       # 제안서
├── archive/         # 아카이브 (YYYY-MM/ 하위 허용)
└── rfc_archive/      # RFC 원본 (YYYY-MM/ 하위 허용)
```

- **docs 루트**: README.md, RULES.md, engineering-gates.md 등 최소한만
- **신규 문서**: 적절한 하위 디렉토리에 배치

---

## 2. ADR 형식 (docs/adr/)

### 필수 프론트매터

```markdown
# ADR-NNNN: 제목

**Status**: Accepted | Proposed | Deprecated | Superseded
**Date**: YYYY-MM-DD
**Deciders**: 결정 주체
**RFC**: 관련 RFC (선택)

---

## Context
...
## Decision
...
## Consequences
...
```

### 파일명

- `NNNN-슬러그.md` (4자리 숫자 + 하이픈 + kebab-case)
- 예: `0015-sink-plugin-architecture.md`

### 섹션 순서

1. Context
2. Decision
3. Consequences (Positive / Negative / Neutral)

---

## 3. 링크 규칙

### 금지

- `../rfc/` — rfc 디렉토리 제거됨
- `docs/rfc/` — 상대 경로 사용

### 사용

- RFC: `../rfc_archive/2026-02/RFC-017-*.md`
- 기존 RFC: `../rfc_archive/rfc001.md`
- ADR: `./0015-sink-plugin-architecture.md`
- 가이드: `../guides/lambda-sqs-setup-guide.md`

### 상대 경로

- 문서 간 참조는 **상대 경로**만 사용
- 절대 경로(`/docs/...`) 금지

---

## 4. Markdown 스타일

### 제목 (H1)

- **문서당 1개** 필수
- **형식**: `# [주제] — [설명]` (em dash `—` U+2014)
  - Guides: `# Lambda & SQS — 환경 설정 가이드`
  - 단일 개념: `# [주제]` (예: `# 개발 가이드`)
- **ADR**: `# ADR-NNNN: [제목]` (고정)
- **RFC**: `# RFC-NNN: [제목]` (고정)
- H2~H6: `##`, `###` 순차 사용 (단계 건너뛰기 금지)

### 목록

- 순서 목록: `1.`, `2.`, `3.`
- 비순서 목록: `-` 사용 (일관성)

### 코드 블록

- 언어 지정: ` ```kotlin`, ` ```bash` 등
- 인라인 코드: `` `변수명` ``

### 표

- 파이프(`|`) 정렬
- 헤더 구분선 필수: `| --- | --- |`

---

## 5. 언어

- **본문**: 한국어
- **코드/식별자**: 영어
- **주석**: 한국어 (CLAUDE.md, .cursorrules 준수)

---

## 6. 금지 패턴

| 패턴 | 이유 |
|------|------|
| `](../rfc/` | rfc 디렉토리 없음 |
| `](docs/` | 상대 경로 사용 |
| H1 여러 개 | 문서당 1개 |
| H2 → H4 건너뛰기 | 계층 유지 |
| 200자 초과 한 줄 | 가독성 |

---

## 7. Lint 실행

```bash
# 문서 lint (RULES 금지 패턴 + markdownlint)
just lint-docs

# 또는
./gradlew lintDocs
./scripts/lint-docs.sh
```

**checkAll**에 포함됨: `./gradlew checkAll` 시 문서 lint 자동 실행

---

## 8. 예외

- `archive/` 하위: 형식 완화 (참고용)
- `rfc_archive/` 하위: 원본 보존, 수정 최소화
