# IVM-Lite (ivm-lite-oliveyoung-full) 프로젝트 개요

## 목적
IVM-Lite는 **Incremental View Maintenance** 데이터 파이프라인 시스템으로, RawData → Slice → View → Sink 흐름을 처리함.

## 기술 스택
- **Backend**: Kotlin 1.9, Ktor 2.3
- **Frontend**: React 19, TypeScript 5.7, Vite 7
- **빌드**: Gradle 8.5, pnpm (admin-ui)
- **아키텍처**: Hexagonal + Domain-Sliced Design

## 주요 구조
```
src/main/kotlin/com/oliveyoung/ivmlite/
├── apps/          # Admin(:8081), RuntimeAPI(:8080), opscli
├── pkg/           # contracts, rawdata, slices, views, sinks, orchestration
├── shared/        # 공통 유틸
└── sdk/           # SDK 모듈

admin-ui/          # React Admin UI (FSD 구조)
src/main/resources/contracts/v1/  # YAML 계약 정의 (SSOT)
```

## 핵심 원칙
- **Contract is Law**: YAML 계약이 SSOT
- **Arrow Either**: try-catch 대신 `Either<DomainError, T>` 사용
- 환경변수: `.env` 로드 필수 (`source .env`)
