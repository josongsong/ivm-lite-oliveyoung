# IVM Lite Admin UI

React + TypeScript + Framer Motion 기반의 관리자 콘솔입니다.

## 🚀 주요 기능

### 1. Dashboard
- Worker 상태 실시간 모니터링
- Outbox 통계 (Pending, Processing, Failed, Processed)
- 데이터 파이프라인 개요
- Slice 타입별 통계

### 2. Contracts 관리
- Entity Schema 조회
- RuleSet 조회
- ViewDefinition 조회
- SinkRule 조회
- YAML 원문 및 파싱된 데이터 확인

### 3. Pipeline 시각화
- 데이터 흐름 시각화 (Raw → Slice → View → Sink)
- Entity별 흐름 추적
- 단계별 통계 확인

### 4. Outbox 관리
- 최근 처리된 작업 조회
- 실패한 작업 조회
- DLQ (Dead Letter Queue) 관리
  - DLQ 엔트리 조회
  - Replay 기능
- Stale 엔트리 관리
  - Visibility Timeout 초과 엔트리 조회
  - Release 기능

## 📦 설치

```bash
cd admin-ui
npm install
```

## 🛠 개발 모드

```bash
# React 개발 서버 (포트 3000)
npm run dev

# Kotlin Admin API 서버 (포트 8081)
./gradlew runAdmin
```

개발 모드에서는 `/api/*` 요청이 자동으로 `http://localhost:8081`로 프록시됩니다.

## 🏗 프로덕션 빌드

```bash
npm run build
```

빌드 결과물은 `../src/main/resources/static/admin/`에 생성됩니다.
Kotlin 서버에서 직접 서빙됩니다.

## 🎨 기술 스택

- **React 19** - UI 프레임워크
- **TypeScript** - 타입 안전성
- **Vite** - 빌드 도구
- **Framer Motion** - 애니메이션
- **TanStack Query** - 서버 상태 관리
- **React Router** - 라우팅
- **Lucide React** - 아이콘

## 📁 프로젝트 구조

```
admin-ui/
├── src/
│   ├── api/
│   │   └── client.ts        # API 클라이언트 및 타입
│   ├── components/
│   │   ├── Layout.tsx       # 레이아웃 (사이드바)
│   │   └── Layout.css
│   ├── pages/
│   │   ├── Dashboard.tsx    # 대시보드
│   │   ├── Contracts.tsx    # Contract 목록
│   │   ├── ContractDetail.tsx # Contract 상세
│   │   ├── Pipeline.tsx     # 파이프라인
│   │   └── Outbox.tsx       # Outbox 관리
│   ├── App.tsx
│   ├── App.css
│   ├── index.css            # 글로벌 스타일 (테마)
│   └── main.tsx
├── public/
│   └── favicon.svg
├── index.html
├── vite.config.ts
└── package.json
```

## 🎨 디자인 시스템

### 색상 (Dark Theme - Cyberpunk)
- **Primary**: `#0a0a0f` (배경)
- **Accent Cyan**: `#00d4ff`
- **Accent Magenta**: `#ff00aa`
- **Accent Green**: `#00ff88`
- **Accent Purple**: `#8855ff`

### 폰트
- **Sans**: Outfit
- **Mono**: JetBrains Mono

### 애니메이션
- Framer Motion 기반 페이지 전환
- Staggered reveal 효과
- Micro-interactions

## 🔗 API 엔드포인트

### Dashboard
- `GET /api/dashboard` - 전체 대시보드

### Contracts
- `GET /api/contracts` - 전체 Contract 목록
- `GET /api/contracts/stats` - Contract 통계
- `GET /api/contracts/schemas` - Schema 목록
- `GET /api/contracts/rulesets` - RuleSet 목록
- `GET /api/contracts/views` - ViewDefinition 목록

### Pipeline
- `GET /api/pipeline/overview` - 파이프라인 개요
- `GET /api/pipeline/rawdata` - RawData 통계
- `GET /api/pipeline/slices` - Slice 통계
- `GET /api/pipeline/flow/{entityKey}` - Entity 흐름 추적

### Outbox
- `GET /api/outbox/recent` - 최근 처리된 엔트리
- `GET /api/outbox/failed` - 실패한 엔트리
- `GET /api/outbox/dlq` - DLQ 엔트리
- `POST /api/outbox/dlq/{id}/replay` - DLQ Replay
- `GET /api/outbox/stale` - Stale 엔트리
- `POST /api/outbox/stale/release` - Stale 해제
