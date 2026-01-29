# OpenTelemetry 백엔드 시스템 비교

> **목적**: 트레이싱 데이터를 처리하는 백엔드 시스템 선택 가이드

---

## 📊 일반적인 선택지

### 1. 로컬 개발 환경

#### Jaeger All-in-One (가장 일반적) ✅
```
애플리케이션 → Jaeger All-in-One (포트 4317)
```

**특징**:
- ✅ 가장 널리 사용됨
- ✅ UI 포함 (포트 16686)
- ✅ OTLP 직접 수신 지원
- ✅ 메모리 기반 (재시작 시 데이터 소실)
- ✅ docker-compose에 이미 추가됨

**사용법**:
```bash
docker-compose up -d jaeger
open http://localhost:16686
```

---

### 2. 프로덕션 환경

#### Option A: Grafana Tempo (오픈소스, 추천) ⭐

```
애플리케이션 → OTLP Collector → Tempo → Grafana
```

**특징**:
- ✅ 오픈소스 (무료)
- ✅ Grafana와 네이티브 통합
- ✅ 스케일 가능 (수평 확장)
- ✅ 객체 스토리지 백엔드 (S3, GCS 등)
- ✅ 장기 저장 가능

**설정**:
```yaml
# OTLP Collector 설정
exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true
```

**비용**: Self-hosted면 무료, Grafana Cloud는 사용량 기반

---

#### Option B: Grafana Cloud (관리형, 추천) ⭐

```
애플리케이션 → Grafana Cloud Tempo
```

**특징**:
- ✅ 완전 관리형 (운영 불필요)
- ✅ Grafana UI 포함
- ✅ 메트릭/로그와 통합 가능
- ✅ 빠른 설정

**설정**:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://tempo-us-central1.grafana.net:443
export OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic <key>"
```

**비용**: 무료 티어 있음, 이후 사용량 기반

---

#### Option C: Datadog (상용)

```
애플리케이션 → Datadog OTLP Endpoint
```

**특징**:
- ✅ APM + 인프라 모니터링 통합
- ✅ AI 기반 이상 탐지
- ✅ 완전 관리형
- ✅ 강력한 분석 도구

**설정**:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://trace-intake.datadoghq.com:443
export OTEL_EXPORTER_OTLP_HEADERS="DD-API-KEY=<api-key>"
```

**비용**: 유료 (월 $31/호스트부터)

---

#### Option D: AWS X-Ray (AWS 환경)

```
애플리케이션 → OTLP Collector → X-Ray
```

**특징**:
- ✅ AWS 네이티브 통합
- ✅ CloudWatch와 통합
- ✅ AWS 서비스 자동 추적
- ✅ IAM 기반 인증

**설정**:
```yaml
# OTLP Collector 설정
exporters:
  xray:
    region: ap-northeast-2
```

**비용**: AWS 사용량 기반 (GB당 $5)

---

#### Option E: New Relic (상용)

```
애플리케이션 → New Relic OTLP Endpoint
```

**특징**:
- ✅ 완전 관리형
- ✅ 강력한 분석 및 알림
- ✅ AI 기반 인사이트
- ✅ 모바일 앱 지원

**설정**:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
export OTEL_EXPORTER_OTLP_HEADERS="api-key=<api-key>"
```

**비용**: 유료 (월 $99부터)

---

## 🎯 선택 가이드

### 시나리오별 추천

| 환경 | 추천 | 이유 |
|------|------|------|
| **로컬 개발** | Jaeger All-in-One | 가장 간단하고 빠름 |
| **스타트업/소규모** | Grafana Cloud | 무료 티어, 관리형 |
| **중대형 (Self-hosted)** | Grafana Tempo | 오픈소스, 스케일 가능 |
| **AWS 환경** | AWS X-Ray | 네이티브 통합 |
| **엔터프라이즈** | Datadog/New Relic | 강력한 분석 도구 |

---

## 📈 비교표

| 항목 | Jaeger | Tempo | Grafana Cloud | Datadog | AWS X-Ray |
|------|--------|-------|---------------|---------|-----------|
| **비용** | 무료 | 무료 | 무료 티어 | 유료 | 사용량 기반 |
| **관리** | Self-hosted | Self-hosted | 관리형 | 관리형 | 관리형 |
| **UI** | ✅ | Grafana | ✅ | ✅ | CloudWatch |
| **스케일** | 제한적 | 높음 | 높음 | 매우 높음 | 높음 |
| **장기 저장** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **설정 난이도** | 쉬움 | 보통 | 매우 쉬움 | 매우 쉬움 | 쉬움 |

---

## 🚀 현재 프로젝트 권장 설정

### 로컬 개발
```bash
# docker-compose에 이미 추가됨
docker-compose up -d jaeger
```

### 프로덕션 (권장)
```bash
# Grafana Cloud 사용 (가장 간단)
export OTEL_EXPORTER_OTLP_ENDPOINT=https://tempo-us-central1.grafana.net:443
export OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic <key>"
```

또는

```bash
# Self-hosted Tempo 사용
export OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo.production:4317
```

---

## 📚 참고 자료

- [Jaeger 공식 문서](https://www.jaegertracing.io/docs/)
- [Grafana Tempo 문서](https://grafana.com/docs/tempo/)
- [Datadog APM](https://docs.datadoghq.com/tracing/)
- [AWS X-Ray](https://aws.amazon.com/xray/)
