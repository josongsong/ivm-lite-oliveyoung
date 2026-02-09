package com.oliveyoung.ivmlite.apps.admin.adapters

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.ScanResponse
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * DynamoDbExplorerAdapter 단위 테스트
 *
 * P0 버그 수정 검증:
 * - Cursor 경계 조건 (< vs <=)
 * - getVersionHistory 정렬 중복 제거
 * - schemaId 빈 문자열 처리
 */
class DynamoDbExplorerAdapterTest {

    private lateinit var dynamoClient: DynamoDbAsyncClient
    private lateinit var adapter: DynamoDbExplorerAdapter

    private val tableName = "test-table"
    private val tenantId = TenantId("oliveyoung")

    @BeforeEach
    fun setup() {
        dynamoClient = mockk()
        adapter = DynamoDbExplorerAdapter(dynamoClient, tableName)
    }

    @Nested
    inner class ListRawDataCursorTests {

        @Test
        fun `cursor가 null이면 전체 결과 반환`() = runTest {
            // Given
            val items = listOf(
                createRawDataItem("entity-A", 1L, "schema1", "1.0.0"),
                createRawDataItem("entity-B", 2L, "schema1", "1.0.0"),
                createRawDataItem("entity-C", 3L, "schema1", "1.0.0")
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, null)

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals(3, data.items.size)
                assertEquals("entity-A", data.items[0].entityKey)
            }
        }

        @Test
        fun `cursor 값과 정확히 같은 항목은 제외 (경계 조건)`() = runTest {
            // Given - cursor="entity-B"일 때, entity-B는 제외되어야 함
            val items = listOf(
                createRawDataItem("entity-A", 1L, "schema1", "1.0.0"),
                createRawDataItem("entity-B", 2L, "schema1", "1.0.0"),
                createRawDataItem("entity-C", 3L, "schema1", "1.0.0")
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, "entity-B")

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals(1, data.items.size)
                assertEquals("entity-C", data.items[0].entityKey)
                // entity-B가 포함되지 않아야 함 (< 연산자가 아닌 > 연산자 사용)
            }
        }

        @Test
        fun `cursor 이후 항목만 반환 (정상 케이스)`() = runTest {
            // Given
            val items = listOf(
                createRawDataItem("entity-A", 1L, "schema1", "1.0.0"),
                createRawDataItem("entity-B", 2L, "schema1", "1.0.0"),
                createRawDataItem("entity-C", 3L, "schema1", "1.0.0"),
                createRawDataItem("entity-D", 4L, "schema1", "1.0.0")
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, "entity-A")

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals(3, data.items.size)
                assertEquals("entity-B", data.items[0].entityKey)
                assertEquals("entity-C", data.items[1].entityKey)
                assertEquals("entity-D", data.items[2].entityKey)
            }
        }

        @Test
        fun `cursor가 마지막 항목이면 빈 결과 반환`() = runTest {
            // Given
            val items = listOf(
                createRawDataItem("entity-A", 1L, "schema1", "1.0.0"),
                createRawDataItem("entity-B", 2L, "schema1", "1.0.0")
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, "entity-B")

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals(0, data.items.size)
                assertNull(data.nextCursor)
            }
        }

        @Test
        fun `hasMore 플래그 정확성 (limit보다 많은 항목)`() = runTest {
            // Given
            val items = listOf(
                createRawDataItem("entity-A", 1L, "schema1", "1.0.0"),
                createRawDataItem("entity-B", 2L, "schema1", "1.0.0"),
                createRawDataItem("entity-C", 3L, "schema1", "1.0.0")
            )
            mockScanResponse(items)

            // When - limit=2
            val result = adapter.listRawData(tenantId, null, 2, null)

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals(2, data.items.size)
                assertEquals("entity-B", data.nextCursor) // 마지막 항목의 entityKey
            }
        }
    }

    @Nested
    inner class SchemaIdEmptyStringTests {

        @Test
        fun `schemaId와 schemaVersion 모두 있으면 조합`() = runTest {
            // Given
            val items = listOf(
                createRawDataItem("entity-A", 1L, "product-schema", "2.0.0")
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, null)

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals("product-schema@2.0.0", data.items[0].schemaId)
            }
        }

        @Test
        fun `schemaId만 있고 schemaVersion이 빈 문자열이면 schemaId만 반환`() = runTest {
            // Given
            val items = listOf(
                createRawDataItem("entity-A", 1L, "product-schema", "")
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, null)

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals("product-schema", data.items[0].schemaId)
            }
        }

        @Test
        fun `schemaId와 schemaVersion 모두 빈 문자열이면 빈 문자열 반환`() = runTest {
            // Given
            val items = listOf(
                createRawDataItem("entity-A", 1L, "", "")
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, null)

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals("", data.items[0].schemaId)
            }
        }

        @Test
        fun `schemaId가 null이고 schemaVersion만 있으면 빈 문자열 반환`() = runTest {
            // Given - schemaId가 null인 경우
            val items = listOf(
                createRawDataItemWithNullSchema("entity-A", 1L)
            )
            mockScanResponse(items)

            // When
            val result = adapter.listRawData(tenantId, null, 10, null)

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals("", data.items[0].schemaId)
            }
        }
    }

    @Nested
    inner class GetVersionHistoryTests {

        @Test
        fun `DynamoDB Query 결과가 이미 정렬되어 있으므로 추가 정렬 없음`() = runTest {
            // Given - DynamoDB에서 scanIndexForward(false)로 내림차순 반환
            val querySlot = slot<QueryRequest>()
            val items = listOf(
                createVersionHistoryItem(3L, "2024-01-03T00:00:00Z", "hash3"),
                createVersionHistoryItem(2L, "2024-01-02T00:00:00Z", "hash2"),
                createVersionHistoryItem(1L, "2024-01-01T00:00:00Z", "hash1")
            )

            coEvery { dynamoClient.query(capture(querySlot)) } returns CompletableFuture.completedFuture(
                QueryResponse.builder().items(items).build()
            )

            // When
            val result = adapter.getVersionHistory(tenantId, EntityKey("test-entity"), 100)

            // Then
            assertTrue(result.isRight())
            result.onRight { history ->
                assertEquals(3, history.size)
                // DynamoDB에서 받은 순서 그대로 유지 (이미 내림차순)
                assertEquals(3L, history[0].version)
                assertEquals(2L, history[1].version)
                assertEquals(1L, history[2].version)
            }

            // Query에 scanIndexForward(false) 설정 확인
            assertEquals(false, querySlot.captured.scanIndexForward())
        }
    }

    @Nested
    inner class ListSlicesByTypeCursorTests {

        @Test
        fun `slices cursor도 경계 조건 정확히 처리`() = runTest {
            // Given
            val items = listOf(
                createSliceItem("entity-A", "core", 1L),
                createSliceItem("entity-B", "core", 2L),
                createSliceItem("entity-C", "core", 3L)
            )
            mockScanResponse(items)

            // When - cursor="entity-B"일 때
            val result = adapter.listSlicesByType(tenantId, SliceType.CORE, 10, "entity-B")

            // Then
            assertTrue(result.isRight())
            result.onRight { data ->
                assertEquals(1, data.items.size)
                assertEquals("entity-C", data.items[0].sourceKey)
            }
        }
    }

    // ==================== Helper Methods ====================

    private fun mockScanResponse(items: List<Map<String, AttributeValue>>) {
        coEvery { dynamoClient.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
            ScanResponse.builder().items(items).build()
        )
    }

    private fun createRawDataItem(
        entityKey: String,
        version: Long,
        schemaId: String,
        schemaVersion: String
    ): Map<String, AttributeValue> = mapOf(
        "PK" to attr("TENANT#${tenantId.value}#$entityKey"),
        "SK" to attr("RAWDATA#v$version"),
        "entity_key" to attr(entityKey),
        "version" to numAttr(version),
        "schema_id" to attr(schemaId),
        "schema_version" to attr(schemaVersion),
        "created_at" to attr("2024-01-01T00:00:00Z")
    )

    private fun createRawDataItemWithNullSchema(
        entityKey: String,
        version: Long
    ): Map<String, AttributeValue> = mapOf(
        "PK" to attr("TENANT#${tenantId.value}#$entityKey"),
        "SK" to attr("RAWDATA#v$version"),
        "entity_key" to attr(entityKey),
        "version" to numAttr(version),
        "created_at" to attr("2024-01-01T00:00:00Z")
    )

    private fun createSliceItem(
        entityKey: String,
        sliceType: String,
        version: Long
    ): Map<String, AttributeValue> = mapOf(
        "PK" to attr("TENANT#${tenantId.value}#$entityKey"),
        "SK" to attr("SLICE#$sliceType#v$version"),
        "entity_key" to attr(entityKey),
        "slice_type" to attr(sliceType),
        "slice_version" to numAttr(version),
        "created_at" to attr("2024-01-01T00:00:00Z")
    )

    private fun createVersionHistoryItem(
        version: Long,
        createdAt: String,
        payloadHash: String
    ): Map<String, AttributeValue> = mapOf(
        "version" to numAttr(version),
        "created_at" to attr(createdAt),
        "payload_hash" to attr(payloadHash)
    )

    private fun attr(value: String): AttributeValue =
        AttributeValue.builder().s(value).build()

    private fun numAttr(value: Long): AttributeValue =
        AttributeValue.builder().n(value.toString()).build()
}
