package com.oliveyoung.ivmlite.pkg.sinks.projection

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * Product Static Projection (opensearch-index-plan v2)
 *
 * PRODUCT_SEARCH View → Static 인덱스 문서 변환.
 * - flatten facet 필드 (category_display, attr_codes, attr_kv)
 * - attrCode별 facet 필드 (Formulation, Skin Type, Main Functions, Ingredients 등)
 * - 검색 텍스트 (title_ko, brand_ko, search_keywords)
 * - 가격/재고는 Dyn로 이동하므로 Static에서 제외
 */
object ProductStaticProjection {

    private const val SCHEMA_VERSION = "v1"

    /** Olive Young attrCode → UI Facet 매핑 (Refine 패널용) */
    private val ATTR_CODE_FACET_MAP = mapOf(
        "2" to "attr_formulation",   // 제형타입 (Formulation)
        "6" to "attr_skin_type",     // 추천피부타입 (Skin Type)
        "42" to "attr_main_functions", // 주요기능 (Main Functions / Skin Concern)
        "81" to "attr_ingredients",  // 주요성분 (Featured Ingredients)
    )

    /**
     * View JSON (Slice 병합 구조) → Static 문서 변환
     *
     * View 구조: { "CORE": {...}, "PRICE": {...}, "CATEGORY": {...}, "INDEX": {...}, "MEDIA": {...} }
     */
    fun project(
        viewData: JsonObject,
        tenantId: String,
        entityKey: String,
        updatedAt: Instant = Instant.now(),
    ): JsonObject {
        val core = viewData["CORE"]?.jsonObject
        val price = viewData["PRICE"]?.jsonObject
        val category = viewData["CATEGORY"]?.jsonObject
        val index = viewData["INDEX"]?.jsonObject
        val media = viewData["MEDIA"]?.jsonObject

        val uaCode = core?.get("uaCode")?.jsonPrimitive?.content
            ?: (viewData["uaCode"]?.jsonPrimitive?.content)

        return buildJsonObject {
            put("tenantId", JsonPrimitive(tenantId))
            put("entityKey", JsonPrimitive(entityKey))
            put("uaCode", JsonPrimitive(uaCode ?: ""))
            put("productId", JsonPrimitive(uaCode ?: entityKey))
            put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
            put("updatedAt", JsonPrimitive(updatedAt.toString()))
            // E2E/테스트용: viewData에 있으면 pass-through (검색 필터용)
            viewData["testRunId"]?.let { put("testRunId", it) }

            // 검색 텍스트
            put("title_ko", JsonPrimitive(resolveTitleKo(core)))
            resolveTitleEn(core)?.let { put("title_en", JsonPrimitive(it)) }
            resolveBrandKo(core)?.let { put("brand_ko", JsonPrimitive(it)) }
            resolveBrandEn(core)?.let { put("brand_en", JsonPrimitive(it)) }
            put("search_keywords", JsonPrimitive(resolveSearchKeywords(index)))

            // 필터/집계 (flatten)
            put("brand_code", JsonPrimitive(resolveBrandCode(core)))
            put("category_display", resolveCategoryDisplay(category, core))
            put("category_std", resolveCategoryStd(category, core))
            put("attr_codes", resolveAttrCodes(index))
            put("attr_kv", resolveAttrKv(index))
            // attrCode별 facet 필드 (UI Refine 패널용)
            resolveAttrFacets(index).forEach { (field, arr) ->
                if (arr.isNotEmpty()) put(field, arr)
            }
            put("badge_vegan", JsonPrimitive(resolveBadge(index, "veganYn")))
            put("badge_clean", JsonPrimitive(resolveBadge(index, "cleanBeautyYn")))
            put("badge_cruelty_free", JsonPrimitive(resolveBadge(index, "crueltyFreeYn")))

            // 미디어
            put("thumb_url", JsonPrimitive(resolveThumbUrl(media, core)))

            // 옵션 (sku, name만)
            put("options", resolveOptionsMinimal(price, core))
        }
    }

    private fun resolveTitleKo(core: JsonObject?): String {
        val online = core?.get("onlineInfo")?.jsonObject
        val prdtName = online?.get("prdtName")?.jsonPrimitive?.content
        if (!prdtName.isNullOrBlank()) return prdtName
        val master = core?.get("masterInfo")?.jsonObject
        return master?.get("gdsNm")?.jsonPrimitive?.content?.trim() ?: ""
    }

    private fun resolveTitleEn(core: JsonObject?): String? {
        val master = core?.get("masterInfo")?.jsonObject
        return master?.get("gdsEngNm")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    private fun resolveBrandKo(core: JsonObject?): String? {
        val brand = core?.get("masterInfo")?.jsonObject?.get("brand")?.jsonObject
        return brand?.get("krName")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    private fun resolveBrandEn(core: JsonObject?): String? {
        val brand = core?.get("masterInfo")?.jsonObject?.get("brand")?.jsonObject
        return brand?.get("enName")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    private fun resolveSearchKeywords(index: JsonObject?): String {
        val additional = index?.get("additionalInfo")?.jsonObject
        val text = additional?.get("srchKeyWordText")?.jsonPrimitive?.content ?: return ""
        return text.split(",", " ", "\n").map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun resolveBrandCode(core: JsonObject?): String {
        val brand = core?.get("masterInfo")?.jsonObject?.get("brand")?.jsonObject
        return brand?.get("code")?.jsonPrimitive?.content ?: ""
    }

    private fun resolveCategoryDisplay(category: JsonObject?, core: JsonObject?): JsonArray {
        val list = category?.get("displayCategories")?.jsonArray
            ?: core?.get("displayCategories")?.jsonArray
            ?: return buildJsonArray { }
        val codes = list.mapNotNull { it.jsonObject["sclsCtgrNo"]?.jsonPrimitive?.content }
            .distinct().sorted()
        return buildJsonArray { codes.forEach { add(JsonPrimitive(it)) } }
    }

    private fun resolveCategoryStd(category: JsonObject?, core: JsonObject?): JsonArray {
        val std = category?.get("masterInfo")?.jsonObject?.get("standardCategory")?.jsonObject
            ?: core?.get("masterInfo")?.jsonObject?.get("standardCategory")?.jsonObject
            ?: return buildJsonArray { }
        val result = mutableListOf<String>()
        std["large"]?.jsonObject?.get("code")?.jsonPrimitive?.content?.let { result.add("L:$it") }
        std["medium"]?.jsonObject?.get("code")?.jsonPrimitive?.content?.let { result.add("M:$it") }
        std["small"]?.jsonObject?.get("code")?.jsonPrimitive?.content?.let { result.add("S:$it") }
        return buildJsonArray { result.forEach { add(JsonPrimitive(it)) } }
    }

    private fun resolveAttrCodes(index: JsonObject?): JsonArray {
        val attrs = index?.get("attributes")?.jsonArray ?: return buildJsonArray { }
        val codes = attrs.mapNotNull { it.jsonObject["attrCode"]?.jsonPrimitive?.content }
            .distinct().sorted()
        return buildJsonArray { codes.forEach { add(JsonPrimitive(it)) } }
    }

    private fun resolveAttrKv(index: JsonObject?): JsonArray {
        val attrs = index?.get("attributes")?.jsonArray ?: return buildJsonArray { }
        val kv = attrs.mapNotNull { attr ->
            val code = attr.jsonObject["attrCode"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val value = attr.jsonObject["attrValue"]?.jsonPrimitive?.content?.let { normalizeValue(it) } ?: return@mapNotNull null
            "$code=$value"
        }.distinct().sorted()
        return buildJsonArray { kv.forEach { add(JsonPrimitive(it)) } }
    }

    /** attrCode별 facet 필드 반환 (Formulation, Skin Type 등 UI Refine용) */
    private fun resolveAttrFacets(index: JsonObject?): Map<String, JsonArray> {
        val attrs = index?.get("attributes")?.jsonArray ?: return emptyMap()
        val byCode = attrs.mapNotNull { attr ->
            val code = attr.jsonObject["attrCode"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val value = attr.jsonObject["attrValue"]?.jsonPrimitive?.content?.let { normalizeValue(it) } ?: return@mapNotNull null
            code to value
        }.groupBy({ it.first }, { it.second })
        return ATTR_CODE_FACET_MAP.mapNotNull { (code, field) ->
            val values = (byCode[code] ?: emptyList()).distinct().sorted()
            if (values.isEmpty()) null else field to buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
        }.toMap()
    }

    private fun normalizeValue(v: String): String =
        v.trim().replace(Regex("\\s+"), " ")

    private fun resolveBadge(index: JsonObject?, field: String): Boolean {
        val emblem = index?.get("emblemInfo")?.jsonObject ?: return false
        val v = emblem[field] ?: return false
        return when (v) {
            is kotlinx.serialization.json.JsonPrimitive -> v.content == "true" || v.content == "Y"
            else -> false
        }
    }

    private fun resolveThumbUrl(media: JsonObject?, core: JsonObject?): String {
        val thumb = media?.get("thumbnailImages")?.jsonArray
            ?: core?.get("thumbnailImages")?.jsonArray
        val first = thumb?.firstOrNull()?.jsonObject
        return first?.get("url")?.jsonPrimitive?.content ?: ""
    }

    private fun resolveOptionsMinimal(price: JsonObject?, core: JsonObject?): JsonArray {
        val opts = price?.get("options")?.jsonArray
            ?: core?.get("options")?.jsonArray
            ?: return buildJsonArray { }
        return buildJsonArray {
            opts.forEach { opt ->
                val obj = opt.jsonObject
                val sku = obj["gdsCd"]?.jsonPrimitive?.content ?: ""
                val name = obj["gdsNm"]?.jsonPrimitive?.content ?: ""
                add(buildJsonObject {
                    put("sku", JsonPrimitive(sku))
                    put("name", JsonPrimitive(name))
                })
            }
        }
    }
}
