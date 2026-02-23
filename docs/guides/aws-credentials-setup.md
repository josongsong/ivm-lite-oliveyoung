# AWS DynamoDB — 자격 증명 설정 가이드

## 개요

IVM Lite는 AWS DynamoDB를 사용하여 Contract Registry를 저장합니다.  
AWS 자격 증명을 설정하는 방법은 다음과 같습니다.

---

## 방법 1: 환경 변수 사용 (권장) ⭐

가장 안전하고 권장되는 방법입니다.

### macOS / Linux

```bash
# .env 파일 생성 (프로젝트 루트)
cat > .env << EOF
export AWS_ACCESS_KEY_ID=YOUR_AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY=YOUR_AWS_SECRET_ACCESS_KEY
export AWS_REGION=ap-northeast-2
EOF

# 환경 변수 로드
source .env

# 애플리케이션 실행
./gradlew run
```

### Windows (PowerShell)

```powershell
# 환경 변수 설정
$env:AWS_ACCESS_KEY_ID="YOUR_AWS_ACCESS_KEY_ID"
$env:AWS_SECRET_ACCESS_KEY="YOUR_AWS_SECRET_ACCESS_KEY"
$env:AWS_REGION="ap-northeast-2"

# 애플리케이션 실행
.\gradlew.bat run
```

### IntelliJ IDEA에서 실행 시

1. **Run Configuration** → **Environment variables** 추가:
   ```
   AWS_ACCESS_KEY_ID=YOUR_AWS_ACCESS_KEY_ID
   AWS_SECRET_ACCESS_KEY=YOUR_AWS_SECRET_ACCESS_KEY
   AWS_REGION=ap-northeast-2
   ```

2. 또는 **Edit Configurations** → **Environment** → **Environment variables**에서 추가

---

## 방법 2: application.yaml 설정

> ⚠️ **주의**: 이 방법은 보안상 권장되지 않습니다.  
> 설정 파일에 자격 증명을 저장하면 Git에 커밋될 위험이 있습니다.

`src/main/resources/application.yaml`:

```yaml
dynamodb:
  endpoint: ${DYNAMODB_ENDPOINT:-}
  region: ${AWS_REGION:-ap-northeast-2}
  tableName: ${DYNAMODB_TABLE}
  accessKeyId: ${AWS_ACCESS_KEY_ID:-}
  secretAccessKey: ${AWS_SECRET_ACCESS_KEY:-}
```

> 💡 **권장**: 환경 변수를 사용하세요. (remote-only: 로컬 기본값 없음)

---

## 방법 3: AWS Credentials 파일

`~/.aws/credentials` 파일에 추가:

```ini
[default]
aws_access_key_id = YOUR_AWS_ACCESS_KEY_ID
aws_secret_access_key = YOUR_AWS_SECRET_ACCESS_KEY
region = ap-northeast-2
```

---

## 자격 증명 우선순위

애플리케이션은 다음 순서로 자격 증명을 찾습니다:

1. **환경 변수** (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. **설정 파일** (`application.yaml`의 `dynamodb.accessKeyId`, `dynamodb.secretAccessKey`)
3. **AWS Credentials 파일** (`~/.aws/credentials`)
4. **IAM 역할** (EC2/ECS/Lambda에서 실행 시)

---

## 설정 확인

### 1. 환경 변수 확인

```bash
# macOS / Linux
echo $AWS_ACCESS_KEY_ID
echo $AWS_SECRET_ACCESS_KEY

# Windows (PowerShell)
echo $env:AWS_ACCESS_KEY_ID
echo $env:AWS_SECRET_ACCESS_KEY
```

### 2. DynamoDB 연결 테스트

```bash
# DynamoDB 테이블 목록 조회
aws dynamodb list-tables \
  --region ap-northeast-2 \
  # endpoint override(DYNAMODB_ENDPOINT)는 기본 비움 (=AWS 기본 엔드포인트)
```

---

## 보안 권장사항

### ✅ DO

- 환경 변수 사용 (`.env` 파일, `.gitignore`에 포함됨)
- IAM 역할 사용 (EC2/ECS/Lambda)
- 최소 권한 원칙 (DynamoDB 접근만 허용)

### ❌ DON'T

- Git에 자격 증명 커밋
- `application.yaml`에 평문 자격 증명 저장 (운영 환경)
- 공개 저장소에 자격 증명 노출

---

## 문제 해결

### 자격 증명을 찾을 수 없음

```
Unable to load credentials from any provider in the chain
```

**해결책:**
1. 환경 변수 설정 확인
2. `~/.aws/credentials` 파일 확인
3. `application.yaml`에 명시적 자격 증명 추가

### 권한 오류

```
AccessDeniedException: User is not authorized to perform: dynamodb:Query
```

**해결책:**
1. IAM 정책 확인 (DynamoDB 접근 권한 필요)
2. 테이블 이름 확인 (`dynamodb.tableName` 설정)

---

## 참고

- [AWS SDK for Java v2 - 자격 증명](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)
