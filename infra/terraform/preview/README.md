# Preview Environment - Terraform

Preview 환경 AWS 인프라 프로비저닝

## 사전 요구사항

1. **AWS CLI 설정**
   ```bash
   aws configure
   # 또는 환경변수
   export AWS_ACCESS_KEY_ID=xxx
   export AWS_SECRET_ACCESS_KEY=xxx
   export AWS_REGION=ap-northeast-2
   ```

2. **Lambda JAR 빌드**
   ```bash
   ./gradlew :plugins:sink-s3:shadowJar
   # 결과: plugins/sink-s3/build/libs/s3-sink-lambda.jar (18MB)
   ```

## 프로비저닝

### 1. 초기화
```bash
cd infra/terraform/preview
terraform init
```

### 2. Plan 확인
```bash
terraform plan
```

### 3. 적용
```bash
terraform apply

# 확인 후 "yes" 입력
```

### 4. Output 확인
```bash
terraform output

# 출력 예시:
# s3_sink_queue_url = "https://sqs.ap-northeast-2.amazonaws.com/058264332540/preview-s3-sink-queue"
# s3_bucket_name = "preview-ivm-lite-sink-data"
# lambda_function_name = "preview-s3-sink"
```

## 생성되는 리소스

| 리소스 | 이름 | 설명 |
|--------|------|------|
| SQS Queue | preview-s3-sink-queue | Sink 메시지 큐 |
| S3 Bucket | preview-ivm-lite-sink-data | View 저장소 |
| Lambda Function | preview-s3-sink | S3 저장 함수 (18MB) |
| IAM Role | preview-lambda-s3-sink-role | Lambda 실행 권한 |
| CloudWatch Log Group | /aws/lambda/preview-s3-sink | Lambda 로그 (7일 보관) |

## 엔진 설정

### 환경변수
```bash
# SQS Queue URL (terraform output에서 복사)
export S3_SINK_QUEUE_URL="https://sqs.ap-northeast-2.amazonaws.com/058264332540/preview-s3-sink-queue"

# 엔진 실행
./gradlew run
```

### application.yaml
```yaml
# src/main/resources/application.yaml
dynamodb:
  region: "ap-northeast-2"
  # endpoint 설정 안 함 (Real AWS 사용)
```

## 테스트

### 1. SQS 메시지 발송 테스트
```bash
aws sqs send-message \
  --queue-url https://sqs.ap-northeast-2.amazonaws.com/058264332540/preview-s3-sink-queue \
  --message-body '{
    "envelopeVersion": 1,
    "target": "s3",
    "producedAtEpochMs": 1707728000000,
    "payloadVersion": 1,
    "entityType": "product",
    "sliceType": "CORE",
    "viewName": "PRODUCT_DETAIL",
    "viewData": {"id": "P001", "name": "Test Product"},
    "metadata": {}
  }'
```

### 2. Lambda 로그 확인
```bash
aws logs tail /aws/lambda/preview-s3-sink --follow
```

### 3. S3 파일 확인
```bash
aws s3 ls s3://preview-ivm-lite-sink-data/views/ --recursive
```

## Lambda 업데이트

JAR 재빌드 후 Lambda 업데이트:

```bash
# 1. JAR 빌드
./gradlew :plugins:sink-s3:shadowJar

# 2. Terraform 재적용
cd infra/terraform/preview
terraform apply

# 또는 AWS CLI로 직접 업데이트
aws lambda update-function-code \
  --function-name preview-s3-sink \
  --zip-file fileb://../../../plugins/sink-s3/build/libs/s3-sink-lambda.jar
```

## 비용 추정

**월간 예상 비용 (트래픽 가정: 100만 View/월):**

| 서비스 | 사용량 | 비용 |
|--------|--------|------|
| SQS | 100만 요청 | $0.40 |
| Lambda | 100만 실행 (512MB, 2초) | $20 |
| S3 | 100GB 저장 + 100만 PUT | $2.50 |
| CloudWatch Logs | 10GB/월 | $0.50 |
| **합계** | | **~$23/월** |

## 삭제

```bash
cd infra/terraform/preview
terraform destroy

# 확인 후 "yes" 입력
```

**주의**: S3 버킷에 데이터가 있으면 삭제 실패. 수동 삭제 필요:
```bash
aws s3 rm s3://preview-ivm-lite-sink-data --recursive
terraform destroy
```
