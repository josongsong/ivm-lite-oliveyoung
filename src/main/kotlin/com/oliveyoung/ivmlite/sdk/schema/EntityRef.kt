package com.oliveyoung.ivmlite.sdk.schema

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker

/**
 * 타입 세이프한 Entity 참조
 * 
 * Contract에서 정의된 Entity를 타입 세이프하게 참조할 수 있게 합니다.
 * 
 * @example
 * ```kotlin
 * // 코드젠으로 생성된 Entities 사용
 * Ivm.client().ingest(Entities.Product) {
 *     sku = "SKU-001"
 *     name = "비타민C"
 *     price = 15000
 * }.deploy()
 * ```
 * 
 * @param T 이 Entity의 빌더 타입
 */
@IvmDslMarker
data class EntityRef<T : EntityBuilder>(
    /** 엔티티 타입 (예: "PRODUCT", "BRAND") */
    val entityType: String,
    
    /** 빌더 팩토리 */
    val builderFactory: () -> T
) {
    override fun toString(): String = entityType
}

/**
 * Entity Builder 인터페이스
 * 
 * 모든 코드젠 엔티티 빌더가 구현해야 하는 인터페이스
 */
interface EntityBuilder {
    /** 엔티티 타입 */
    val entityType: String
    
    /** 빌드된 데이터 반환 */
    fun build(): Map<String, Any?>
}
