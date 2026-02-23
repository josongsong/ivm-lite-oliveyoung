# OpenTelemetry — 트레이싱 설정 가이드

> **목적**: OpenTelemetry 트레이싱 데이터를 어디로 보내고 어떻게 확인하는지 정리

---

## 📊 현재 설정

### 애플리케이션 설정
- **OTLP Exporter**: `http://localhost:4317` (gRPC)
- **설정 위치**: `application.yaml` 또는 `OTEL_EXPORTER_OTLP_ENDPOINT` 환경변수
- **Span 생성**: 모든 Workflow, Repository, HTTP 요청에 자동 생성

---

## 🚀 로컬 개발 환경

### Option 1: Jaeger All-in-One (권장 - 가장 간단)

**docker-compose에 이미 추가되어 있습니다!**

```bash
# Jaeger 시작
docker-compose up -d jaeger

# Jaeger UI 접속
open http://localhost:16686
```

**특징**:
- ✅ OTLP 수신 지원 (`COLLECTOR_OTLP_ENABLED=true`)
- ✅ UI 포함 (포트 16686)
- ✅ 메모리 기반 저장 (재시작 시 데이터 소실)
- ✅ 개발/테스트용으로 완벽

### Option 2: OTLP Collector + Jaeger (고급)

```bash
# OTLP Collector 실행
docker run -d --name otel-collector \
  -p 4317:4317 \
  -p 4318:4318 \
  -v $(pwd)/otel-collector-config.yaml:/etc/otelcol/config.yaml \
  otel/opentelemetry-collector:latest

# Jaeger 실행
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 14250:14250 \
  jaegertracing/all-in-one:1.55
```

**otel-collector-config.yaml**:
```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [jaeger]
```

---

## 🏭 프로덕션 환경

### 일반적인 패턴

```
애플리케이션 → OTLP Collector → 백엔드 시스템
```

### Option 1: Grafana Cloud (관리형)

```yaml
# application.yaml
observability:
  otlpEndpoint: https://tempo-us-central1.grafana.net:443
```

**환경변수**:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://tempo-us-central1.grafana.net:443
export OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic <base64-encoded-key>"
```

### Option 2: AWS X-Ray

```yaml
# OTLP Collector 설정
exporters:
  xray:
    region: ap-northeast-2
    # AWS 자격 증명은 환경변수 또는 IAM Role 사용
```

### Option 3: Datadog

```yaml
# application.yaml
observability:
  otlpEndpoint: https://trace-intake.datadoghq.com:443
```

**환경변수**:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://trace-intake.datadoghq.com:443
export OTEL_EXPORTER_OTLP_HEADERS="DD-API-KEY=<your-api-key>"
```

### Option 4: Self-hosted Tempo (Grafana Stack)

```yaml
# OTLP Collector 설정
exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [otlp/tempo]
```

---

## 🔧 환경별 설정

### 로컬 개발
```bash
# .env 또는 환경변수
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
ENVIRONMENT=development
```

### 스테이징
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.staging:4317
ENVIRONMENT=staging
```

### 프로덕션
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://tempo.production:4317
ENVIRONMENT=production
```

---

## 📈 트레이스 확인 방법

### Jaeger UI (로컬)
1. `docker-compose up -d jaeger`
2. 브라우저에서 `http://localhost:16686` 접속
3. Service: `ivm-lite` 선택
4. "Find Traces" 클릭

### Grafana (Tempo 연동)
1. Grafana에서 Tempo 데이터소스 추가
2. Explore → Tempo 선택
3. Service: `ivm-lite` 검색

---

## ✅ 검증 방법

### 1. Span이 생성되는지 확인
```bash
# 애플리케이션 로그에서 traceId 확인
grep "traceId" logs/application.log
```

### 2. OTLP Collector/Jaeger가 수신하는지 확인
```bash
# Jaeger 헬스체크
curl http://localhost:16686/

# OTLP gRPC 포트 확인
telnet localhost 4317
```

### 3. 실제 트레이스 확인
```bash
# API 호출 후 Jaeger UI에서 확인
curl http://localhost:8080/api/v1/products
# → Jaeger UI에서 "ivm-lite" 서비스의 트레이스 확인
```

---

## 🎯 권장 설정

### 로컬 개발
- ✅ **Jaeger All-in-One** (docker-compose에 포함)
- 간단하고 빠름
- UI 포함

### 프로덕션
- ✅ **OTLP Collector → Grafana Cloud** (관리형, 추천)
- 또는 **OTLP Collector → Self-hosted Tempo**
- 또는 **AWS X-Ray** (AWS 환경)

---

## 📚 참고 자료

- [OpenTelemetry 공식 문서](https://opentelemetry.io/docs/)
- [Jaeger 문서](https://www.jaegertracing.io/docs/)
- [Grafana Tempo 문서](https://grafana.com/docs/tempo/)
- [RFC-IMPL-009](../rfc_archive/rfcimpl009.md) - Observability SSOT
