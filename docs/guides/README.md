# 개발 가이드 — 목차

이 디렉토리는 개발자가 참고할 수 있는 실용적인 가이드 문서들을 포함합니다.

## 문서 목록

### 아키텍처
- [Architecture Onboarding](./architecture-onboarding.md) - IVM-Lite 아키텍처 개요 및 온보딩
- [Engine Architecture](./engine-architecture.md) - SDK & API 엔진 아키텍처

### 데이터 흐름
- [Raw → Slice → View → Sink](./raw-to-slicing-to-view-to-sink.md) - 데이터 파이프라인 흐름
- [VIEW_TO_SINK Backfill 구현 계획](./view-to-sink-backfill-plan.md) - Slice → Sink 벌크 인덱싱 (OpenSearch reindex)
- [Sink 버퍼링 전략](./sink-buffering-strategy.md) - 실시간 Sink 시간/개수 기반 버퍼링 (SQS Batch Window)
  - **Slice 실행 순서 (RFC-018)**: 의존성 자동 추론, TopoSort, Wave별 병렬 실행

### 인프라 & 배포
- [Lambda & SQS 환경 설정](./lambda-sqs-setup-guide.md) - Lambda/SQS 세팅, 환경변수, IAM, Terraform, Ingest API 옵션(skipSink/inProcessSink)

### 외부 API 연동
- [USE Inventory API 연동](./use-inventory-api-integration.md) - USE 상품 재고 조회 API

### SDK Embed
- [SDK Embed — CredentialsProvider 주입](./sdk-embed-credentials.md) - 외부 앱 embed 시 AWS 자격 증명 주입

## 관련 문서

- [ADR 목록](../adr/README.md) - 아키텍처 결정사항
- [RFC 아카이브](../rfc_archive/) - 원본 RFC 문서
- [RFC-018 Slice 실행 순서](../rfc_archive/2026-02/RFC-018-slice-execution-order-enforcement.md) - 의존성 런타임 강제
