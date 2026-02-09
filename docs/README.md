# IVM-Lite 문서

이 디렉토리는 IVM-Lite 프로젝트의 모든 문서를 포함합니다.

## 디렉토리 구조

```
docs/
├── adr/              # Architecture Decision Records (아키텍처 결정사항)
├── guides/           # 개발 가이드 및 아키텍처 문서
├── proposals/        # 제안서 및 개선 계획
├── archive/          # 아카이브된 문서 (참고용)
└── rfc_archive/      # RFC 문서 아카이브 (원본 보존)
```

## 주요 문서

### 아키텍처 결정사항 (ADR)
- [ADR 목록](./adr/README.md) - 모든 아키텍처 결정사항

### 개발 가이드
- [Architecture Onboarding](./guides/architecture-onboarding.md) - 아키텍처 개요
- [Engine Architecture](./guides/engine-architecture.md) - SDK & API 엔진 아키텍처
- [Data Flow](./guides/raw-to-slicing-to-view-to-sink.md) - Raw → Slice → View → Sink 흐름

### 제안서
- [Design System Management](./proposals/design-system-management-sota.md) - 디자인 시스템 관리
- [Shared UI Quality Assessment](./proposals/shared-ui-quality-assessment.md) - UI 컴포넌트 품질 평가

## 문서 정리 원칙

1. **ADR**: 핵심 아키텍처 결정사항만 기록
2. **Guides**: 개발자가 참고할 실용적인 가이드
3. **Proposals**: 향후 개선 계획 및 제안
4. **Archive**: 더 이상 활발히 사용하지 않는 문서 (참고용)

## 참고

- 원본 RFC 문서는 `rfc_archive/`에서 확인할 수 있습니다.
- 아카이브된 문서는 `archive/`에서 확인할 수 있습니다.
