#!/bin/bash
# MSK 클러스터 Bootstrap Servers 조회 스크립트

set -euo pipefail

# MSK Cluster ARN
CLUSTER_ARN="${KAFKA_CLUSTER_ARN:-arn:aws:kafka:ap-northeast-2:058264332540:cluster/ivm-lite-kafka/88c798fa-e11f-447c-b139-794ad9f05140-s3}"
REGION="${AWS_REGION:-ap-northeast-2}"

echo "MSK 클러스터 Bootstrap Servers 조회 중..."
echo "Cluster ARN: $CLUSTER_ARN"
echo "Region: $REGION"
echo ""

# Bootstrap brokers 조회
BOOTSTRAP_OUTPUT=$(aws kafka get-bootstrap-brokers \
    --cluster-arn "$CLUSTER_ARN" \
    --region "$REGION" \
    2>&1)

if [ $? -eq 0 ]; then
    echo "✅ Bootstrap Servers 조회 성공:"
    echo ""
    
    # Plaintext 또는 TLS 엔드포인트 추출
    echo "$BOOTSTRAP_OUTPUT" | jq -r '.BootstrapBrokerStringTls // .BootstrapBrokerString // empty' | tr ',' '\n' | sed 's/^/  /'
    
    echo ""
    echo "환경 변수 설정 예시:"
    echo "export KAFKA_BOOTSTRAP_SERVERS=\"$(echo "$BOOTSTRAP_OUTPUT" | jq -r '.BootstrapBrokerStringTls // .BootstrapBrokerString' | tr '\n' ',' | sed 's/,$//')\""
    echo "export KAFKA_SECURITY_PROTOCOL=SASL_SSL"
    echo "export KAFKA_SASL_MECHANISM=AWS_MSK_IAM"
    echo "export AWS_REGION=$REGION"
else
    echo "❌ Bootstrap Servers 조회 실패:"
    echo "$BOOTSTRAP_OUTPUT"
    echo ""
    echo "AWS 자격 증명을 확인하세요:"
    echo "  aws sts get-caller-identity"
    exit 1
fi
