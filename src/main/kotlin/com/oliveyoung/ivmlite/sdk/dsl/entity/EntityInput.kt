package com.oliveyoung.ivmlite.sdk.dsl.entity

/**
 * Entity 입력 공통 인터페이스
 * RFC-IMPL-011 Wave 2-D
 */
sealed interface EntityInput {
    val tenantId: String
    val entityType: String
}

/**
 * Generic Entity Input (코드젠 엔티티용)
 * 
 * 코드젠으로 생성된 EntityBuilder의 결과를 담는 범용 입력 타입
 */
data class GenericEntityInput(
    override val tenantId: String,
    override val entityType: String,
    val data: Map<String, Any?>
) : EntityInput
