# Lambda 배포 가이드

**목표**: Ktor 서버 대신 AWS Lambda로 Ingest API 처리

---

## 🏗️ 아키텍처 비교

### 현재 (Ktor 서버)
```
Client → API Gateway → Ktor Server (EC2/ECS)
                          ↓
                    IngestionOrchestrator
                          ↓
                    Response 200 OK
```

### Lambda 전환 후
```
Client → API Gateway → Lambda (IngestLambdaHandler)
                          ↓
                    IngestionOrchestrator
                          ↓
                    Response 200 OK
```

---

## 📦 1. Lambda JAR 빌드

### Gradle 설정 (build.gradle.kts)

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks.shadowJar {
    archiveBaseName.set("ivm-ingest-lambda")
    archiveClassifier.set("")
    archiveVersion.set("1.0.0")

    // Lambda Runtime 포함
    mergeServiceFiles()

    // 불필요한 파일 제외
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}
```

### 빌드 실행
```bash
./gradlew shadowJar

# 결과: build/libs/ivm-ingest-lambda-1.0.0.jar (약 50MB)
```

---

## 🚀 2. Lambda 함수 생성

### AWS Console 방법

1. **Lambda 생성**
   - Function name: `ivm-ingest-api`
   - Runtime: `Java 17 (Corretto)`
   - Architecture: `x86_64` (또는 `arm64` - 더 저렴)

2. **JAR 업로드**
   - Upload from: `.zip or .jar file`
   - File: `ivm-ingest-lambda-1.0.0.jar`

3. **Handler 설정**
   - Handler: `com.oliveyoung.ivmlite.apps.lambda.IngestLambdaHandler`

4. **메모리/타임아웃**
   - Memory: `1024 MB` (권장, View 생성 고려)
   - Timeout: `30 seconds` (동기 처리 1~2초 + 여유)

5. **환경 변수**
   ```
   DB_URL=jdbc:postgresql://...
   DB_USER=postgres
   DB_PASSWORD=***
   AWS_REGION=ap-northeast-2
   ```

---

## 🔗 3. API Gateway 연동

### REST API 생성

1. **API Gateway 생성**
   - Type: `REST API`
   - Name: `ivm-ingest-api`

2. **Resource 생성**
   - Path: `/v1/ingest`
   - Method: `POST`

3. **Integration**
   - Type: `Lambda Function`
   - Lambda: `ivm-ingest-api`
   - Use Lambda Proxy integration: ✅ **체크**

4. **배포**
   - Stage: `prod`
   - URL: `https://abc123.execute-api.ap-northeast-2.amazonaws.com/prod/v1/ingest`

---

## 📝 4. 요청 예시

### cURL
```bash
curl -X POST https://abc123.execute-api.ap-northeast-2.amazonaws.com/prod/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-1",
    "entityKey": "PRODUCT:sku123",
    "payload": {
      "name": "상품명",
      "price": 10000
    },
    "jobId": "job-001"
  }'
```

### 응답
```json
{
  "success": true,
  "jobId": "job-001",
  "tenantId": "tenant-1",
  "entityKey": "PRODUCT:sku123",
  "version": 1,
  "sliceCount": 3,
  "viewCount": 2,
  "sinkPending": true,
  "durationMs": 1234
}
```

---

## ⚡ 5. 성능 최적화

### Cold Start 개선

**문제**: 첫 요청 시 5~10초 소요 (JVM 초기화)

**해결책**:
1. **Provisioned Concurrency**
   - 사전 웜업된 인스턴스 유지
   - 비용 증가하지만 Cold Start 제거

2. **SnapStart (Java 전용)**
   - Lambda 초기화 스냅샷
   - Cold Start 90% 감소

3. **GraalVM Native Image**
   - AOT 컴파일
   - 시작 시간 < 100ms
   - 빌드 복잡도 증가

### 동시성 설정

```bash
# Reserved Concurrency: 동시 실행 제한
aws lambda put-function-concurrency \
  --function-name ivm-ingest-api \
  --reserved-concurrent-executions 100

# Provisioned Concurrency: 사전 준비된 인스턴스
aws lambda put-provisioned-concurrency-config \
  --function-name ivm-ingest-api \
  --provisioned-concurrent-executions 5 \
  --qualifier prod
```

---

## 💰 6. 비용 비교

### Ktor 서버 (EC2 t3.medium)
- 인스턴스: $0.0416/hour × 730h = **$30/월**
- 항상 실행 (트래픽 없어도 비용 발생)

### Lambda
- 요청: $0.20 / 1M requests
- 실행 시간: $0.0000166667 / GB-second
- 예시 (1M requests, 1GB, 2초):
  - 요청 비용: $0.20
  - 실행 비용: 1M × 1GB × 2s × $0.0000166667 = $33.33
  - **총 $33.53/월**

**결론**: 트래픽 적으면 Lambda 유리, 많으면 EC2 유리

---

## 🔐 7. 보안 설정

### IAM Role (Lambda 실행 역할)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:GetItem",
        "dynamodb:Query"
      ],
      "Resource": "arn:aws:dynamodb:*:*:table/ivm-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage"
      ],
      "Resource": "arn:aws:sqs:*:*:ivm-sink-*"
    }
  ]
}
```

### VPC 설정 (RDS 연결 시)

```bash
# Lambda를 VPC 내부에 배치
aws lambda update-function-configuration \
  --function-name ivm-ingest-api \
  --vpc-config SubnetIds=subnet-xxx,subnet-yyy,SecurityGroupIds=sg-zzz
```

**주의**: VPC Lambda는 NAT Gateway 필요 (인터넷 액세스)

---

## 📊 8. 모니터링

### CloudWatch Logs
```bash
# 로그 확인
aws logs tail /aws/lambda/ivm-ingest-api --follow
```

### CloudWatch Metrics
- **Invocations**: 호출 횟수
- **Duration**: 실행 시간 (평균 1~2초)
- **Errors**: 에러 발생 횟수
- **Throttles**: 동시성 제한 도달

### OpenTelemetry 트레이싱 (RFC-IMPL-009)

Lambda Handler에 OpenTelemetry 트레이싱이 적용되어 있습니다.

**SinkStreamHandler** (DynamoDB Streams):
- `lambdaTracingModule` 사용 (환경변수 기반)
- Span: `SinkStreamHandler.processBatch`
- 속성: `aws.lambda.function`, `sink.batch.size`, `sink.processed`, `sink.duration_ms` 등

**IngestLambdaHandler** (API Gateway):
- `allModules` (tracingModule 포함)
- Span: `IngestLambdaHandler.handleRequest`
- 속성: `ingest.tenant`, `ingest.entity_key`, `ingest.version`, `ingest.duration_ms` 등

**환경변수 (SinkStream Lambda)**:
- `TRACING_ENABLED`: true/false (기본: true)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OTLP Collector 주소 (예: `http://otel-collector:4317`)

**배포 시**: OTEL Collector를 Lambda와 같은 VPC에 두고, `OTEL_EXPORTER_OTLP_ENDPOINT`로 설정.

---

## 🔄 9. CI/CD 파이프라인

### GitHub Actions 예시

```yaml
name: Deploy Lambda

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'

      - name: Build JAR
        run: ./gradlew shadowJar

      - name: Deploy to Lambda
        run: |
          aws lambda update-function-code \
            --function-name ivm-ingest-api \
            --zip-file fileb://build/libs/ivm-ingest-lambda-1.0.0.jar
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          AWS_REGION: ap-northeast-2
```

---

## ✅ 최종 체크리스트

- [ ] `shadowJar` 빌드 성공
- [ ] Lambda 함수 생성 (Java 17 Runtime)
- [ ] Handler 설정: `IngestLambdaHandler`
- [ ] 환경 변수 설정 (DB, AWS)
- [ ] API Gateway 연동 (Lambda Proxy)
- [ ] VPC 설정 (RDS 연결 시)
- [ ] IAM Role 권한 (DynamoDB, SQS, Logs)
- [ ] Cold Start 최적화 (Provisioned Concurrency)
- [ ] CloudWatch 모니터링 설정
- [ ] 부하 테스트 (1000 requests/sec)

---

**작성자**: Claude Sonnet 4.5
**작성일**: 2026-02-12
**상태**: Lambda 배포 가이드 완성
