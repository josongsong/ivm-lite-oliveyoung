package com.oliveyoung.ivmlite.sdk.schema

import com.oliveyoung.ivmlite.sdk.client.Ivm
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * EntityRef & EntityBuilder 테스트
 * 
 * 코드젠으로 생성되는 엔티티 빌더 패턴 검증
 */
class EntityRefTest {

    @BeforeEach
    fun setup() {
        Ivm.configure {
            baseUrl("http://localhost:8080")
            tenantId("test-tenant")
        }
    }

    // ===== EntityBuilder 인터페이스 테스트 =====

    @Test
    fun `EntityBuilder 구현체 생성 및 빌드`() {
        val builder = TestProductBuilder()
        builder.sku = "SKU-001"
        builder.name = "테스트 상품"
        builder.price = 15000L

        val data = builder.build()

        assertEquals("PRODUCT", builder.entityType)
        assertEquals("SKU-001", data["sku"])
        assertEquals("테스트 상품", data["name"])
        assertEquals(15000L, data["price"])
    }

    @Test
    fun `EntityBuilder 선택 필드 처리`() {
        val builder = TestProductBuilder()
        builder.sku = "SKU-002"
        builder.name = "선택 필드 테스트"
        builder.price = 20000L
        builder.category = "건강식품"
        builder.brand = "테스트브랜드"

        val data = builder.build()

        assertEquals("건강식품", data["category"])
        assertEquals("테스트브랜드", data["brand"])
    }

    @Test
    fun `EntityBuilder 커스텀 속성`() {
        val builder = TestProductBuilder()
        builder.sku = "SKU-003"
        builder.name = "커스텀 속성 테스트"
        builder.price = 25000L
        builder.attribute("origin", "국내")
        builder.attribute("weight", "500g")

        val data = builder.build()

        assertEquals("국내", data["origin"])
        assertEquals("500g", data["weight"])
    }

    // ===== EntityRef 테스트 =====

    @Test
    fun `EntityRef 생성 및 빌더 팩토리 호출`() {
        val entityRef = EntityRef<TestProductBuilder>(
            entityType = "PRODUCT",
            builderFactory = { TestProductBuilder() }
        )

        assertEquals("PRODUCT", entityRef.entityType)

        val builder = entityRef.builderFactory()
        assertNotNull(builder)
        assertTrue(builder is TestProductBuilder)
    }

    @Test
    fun `EntityRef toString은 entityType 반환`() {
        val entityRef = EntityRef<TestProductBuilder>(
            entityType = "PRODUCT",
            builderFactory = { TestProductBuilder() }
        )

        assertEquals("PRODUCT", entityRef.toString())
    }

    // ===== Ivm.client().ingest(EntityRef) 테스트 =====

    @Test
    fun `IvmClient ingest(EntityRef) 체이닝`() {
        val entityRef = EntityRef<TestProductBuilder>(
            entityType = "PRODUCT",
            builderFactory = { TestProductBuilder() }
        )

        val deployable = Ivm.client().ingest(entityRef) {
            sku = "SKU-TEST"
            name = "테스트"
            price = 10000L
        }

        assertNotNull(deployable)
    }

    @Test
    fun `IngestContext entity() 호출`() {
        val entityRef = EntityRef<TestProductBuilder>(
            entityType = "PRODUCT",
            builderFactory = { TestProductBuilder() }
        )

        val deployable = Ivm.client().ingest().entity(entityRef) {
            sku = "SKU-TEST"
            name = "테스트"
            price = 10000L
        }

        assertNotNull(deployable)
    }
}

// ===== 테스트용 EntityBuilder 구현 =====

class TestProductBuilder : EntityBuilder {
    override val entityType: String = "PRODUCT"

    // 필수 필드
    var sku: String = ""
    var name: String = ""
    var price: Long = 0L

    // 선택 필드
    var category: String? = null
    var brand: String? = null

    // 커스텀 속성
    private val _attributes = mutableMapOf<String, Any>()

    fun attribute(key: String, value: Any) {
        _attributes[key] = value
    }

    override fun build(): Map<String, Any?> {
        require(sku.isNotBlank()) { "sku is required" }
        require(name.isNotBlank()) { "name is required" }

        return mapOf(
            "sku" to sku,
            "name" to name,
            "price" to price,
            "category" to category,
            "brand" to brand,
        ) + _attributes
    }
}
