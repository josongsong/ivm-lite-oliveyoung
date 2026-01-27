package com.oliveyoung.ivmlite.shared.config

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.slf4j.LoggerFactory

/**
 * SOTA급 설정 검증기
 * 
 * 애플리케이션 시작 시 설정의 유효성을 검증합니다.
 * - 필수 환경 변수 확인
 * - AWS 자격 증명 형식 검증
 * - 보안 모범 사례 검사
 */
object ConfigValidator {
    private val logger = LoggerFactory.getLogger(ConfigValidator::class.java)
    
    /**
     * 설정 검증 실행
     * 
     * @throws DomainError.ConfigError 검증 실패 시
     */
    fun validate(config: AppConfig) {
        logger.info("설정 검증 시작...")
        
        val errors = mutableListOf<String>()
        
        // DynamoDB 설정 검증
        validateDynamoDbConfig(config.dynamodb, errors)
        
        // 데이터베이스 설정 검증
        validateDatabaseConfig(config.database, errors)
        
        // Kafka 설정 검증
        validateKafkaConfig(config.kafka, errors)
        
        if (errors.isNotEmpty()) {
            val errorMessage = "설정 검증 실패:\n" + errors.joinToString("\n")
            logger.error(errorMessage)
            throw DomainError.ConfigError(errorMessage)
        }
        
        logger.info("설정 검증 완료")
    }
    
    private fun validateDynamoDbConfig(config: DynamoDbConfig, errors: MutableList<String>) {
        // Region 검증
        if (config.region.isBlank()) {
            errors.add("dynamodb.region이 비어있습니다")
        }
        
        // TableName 검증
        if (config.tableName.isBlank()) {
            errors.add("dynamodb.tableName이 비어있습니다")
        }
        
        // AWS 자격 증명 검증 (endpoint가 없으면 AWS 사용)
        if (config.endpoint.isNullOrBlank()) {
            // AWS 사용 시 자격 증명 필요
            val hasAccessKey = config.accessKeyId?.isNotBlank() == true
            val hasSecretKey = config.secretAccessKey?.isNotBlank() == true
            
            // 환경 변수에서도 확인 (환경 변수가 우선이므로)
            val envAccessKey = System.getenv("AWS_ACCESS_KEY_ID")
            val envSecretKey = System.getenv("AWS_SECRET_ACCESS_KEY")
            
            val hasCredentials = hasAccessKey && hasSecretKey || 
                                envAccessKey != null && envSecretKey != null
            
            if (!hasCredentials) {
                logger.warn(
                    "AWS 자격 증명이 설정되지 않았습니다. " +
                    "환경 변수(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY) 또는 " +
                    "IAM 역할을 사용하세요."
                )
                // 경고만 (IAM 역할 사용 가능하므로)
            } else {
                // 자격 증명 형식 검증
                val accessKeyId = envAccessKey ?: config.accessKeyId
                val secretAccessKey = envSecretKey ?: config.secretAccessKey
                
                if (accessKeyId != null && secretAccessKey != null) {
                    validateAwsCredentials(accessKeyId, secretAccessKey, errors)
                }
            }
        } else {
            // 로컬 DynamoDB 사용 시 자격 증명 불필요
            logger.info("로컬 DynamoDB 사용: ${config.endpoint}")
        }
    }
    
    private fun validateAwsCredentials(
        accessKeyId: String,
        secretAccessKey: String,
        errors: MutableList<String>
    ) {
        // Access Key ID 형식 검증 (AKIA로 시작, 20자)
        if (!accessKeyId.matches(Regex("^AKIA[0-9A-Z]{16}$"))) {
            errors.add(
                "AWS_ACCESS_KEY_ID 형식이 올바르지 않습니다. " +
                "AKIA로 시작하는 20자 문자열이어야 합니다."
            )
        }
        
        // Secret Access Key 길이 검증 (최소 40자)
        if (secretAccessKey.length < 40) {
            errors.add(
                "AWS_SECRET_ACCESS_KEY 길이가 너무 짧습니다. " +
                "보안 위험이 있을 수 있습니다."
            )
        }
        
        // 보안 경고: 설정 파일에 평문 저장 시
        if (accessKeyId.contains("AKIA") && secretAccessKey.length >= 40) {
            logger.info("AWS 자격 증명 형식 검증 통과")
        }
    }
    
    private fun validateDatabaseConfig(config: DatabaseConfig, errors: MutableList<String>) {
        if (config.url.isBlank()) {
            errors.add("database.url이 비어있습니다")
        }
        
        if (config.user.isBlank()) {
            errors.add("database.user이 비어있습니다")
        }
        
        if (config.password.isBlank()) {
            errors.add("database.password이 비어있습니다")
        }
        
        // 개발 환경 경고 (기본 비밀번호 사용 시)
        if (config.password == "ivm_local_dev") {
            logger.warn("기본 데이터베이스 비밀번호를 사용하고 있습니다. 프로덕션 환경에서는 변경하세요.")
        }
    }
    
    private fun validateKafkaConfig(config: KafkaConfig, errors: MutableList<String>) {
        if (config.bootstrapServers.isBlank()) {
            errors.add("kafka.bootstrapServers가 비어있습니다")
        }
        
        if (config.consumerGroup.isBlank()) {
            errors.add("kafka.consumerGroup이 비어있습니다")
        }
    }
}

