package com.oliveyoung.ivmlite.pkg.sinks.projection

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test

/**
 * ProductStaticProjection 단위 테스트
 */
class ProductStaticProjectionTest {

    @Test
    @Suppress("LongMethod")
    fun `View CORE+PRICE+CATEGORY+INDEX+MEDIA → Static 문서 변환`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA11279226")
                putJsonObject("masterInfo") {
                    put("gdsNm", "제나벨 PDRN 크림")
                    put("gdsEngNm", "Genabelle PDRN Cream")
                    putJsonObject("brand") {
                        put("code", "GENABELLE")
                        put("krName", "제나벨")
                        put("enName", "Genabelle")
                    }
                    putJsonObject("standardCategory") {
                        putJsonObject("large") { put("code", "10") }
                        putJsonObject("medium") { put("code", "101") }
                        putJsonObject("small") { put("code", "1011") }
                    }
                }
                putJsonObject("onlineInfo") {
                    put("prdtName", "제나벨 PDRN 리쥬비네이팅 크림 70ml")
                }
            }
            putJsonObject("PRICE") {
                putJsonArray("options") {
                    add(buildJsonObject {
                        put("gdsCd", "8809690390048")
                        put("gdsNm", "제나벨 PDRN 리쥬비네이팅 크림 70ml (온)")
                    })
                }
            }
            putJsonObject("CATEGORY") {
                putJsonArray("displayCategories") {
                    add(buildJsonObject { put("sclsCtgrNo", "1000000160") })
                    add(buildJsonObject { put("sclsCtgrNo", "1000000158") })
                }
            }
            putJsonObject("INDEX") {
                putJsonObject("additionalInfo") {
                    put("srchKeyWordText", "시카,보습,수분")
                }
                putJsonObject("emblemInfo") {
                    put("veganYn", "false")
                    put("cleanBeautyYn", "true")
                    put("crueltyFreeYn", "false")
                }
                putJsonArray("attributes") {
                    add(buildJsonObject {
                        put("attrCode", "2")
                        put("attrValue", "수분크림 제형")
                    })
                    add(buildJsonObject {
                        put("attrCode", "6")
                        put("attrValue", "모든피부타입")
                    })
                }
            }
            putJsonObject("MEDIA") {
                putJsonArray("thumbnailImages") {
                    add(buildJsonObject { put("url", "https://cdn.example.com/thumb.jpg") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA11279226",
        )

        result["tenantId"]!!.toString() shouldBe "\"oliveyoung\""
        result["entityKey"]!!.toString() shouldBe "\"PRODUCT:oliveyoung:UA11279226\""
        result["uaCode"]!!.toString() shouldBe "\"UA11279226\""
        result["productId"]!!.toString() shouldBe "\"UA11279226\""
        result["schemaVersion"]!!.toString() shouldBe "\"v1\""

        // title_ko: onlineInfo.prdtName 우선
        result["title_ko"]!!.toString() shouldBe "\"제나벨 PDRN 리쥬비네이팅 크림 70ml\""
        result["brand_code"]!!.toString() shouldBe "\"GENABELLE\""
        result["brand_ko"]!!.toString() shouldBe "\"제나벨\""
        result["search_keywords"]!!.toString() shouldBe "\"시카 보습 수분\""

        result["badge_vegan"]!!.toString() shouldBe "false"
        result["badge_clean"]!!.toString() shouldBe "true"
        result["thumb_url"]!!.toString() shouldBe "\"https://cdn.example.com/thumb.jpg\""

        result["category_display"] shouldNotBe null
        result["attr_codes"] shouldNotBe null
        result["attr_kv"] shouldNotBe null
        result["options"] shouldNotBe null
        // attrCode별 facet 필드 (UI Refine 패널용)
        result["attr_formulation"]!!.jsonArray shouldHaveSize 1
        result["attr_formulation"]!!.jsonArray[0].jsonPrimitive.content shouldBe "수분크림 제형"
        result["attr_skin_type"]!!.jsonArray shouldHaveSize 1
        result["attr_skin_type"]!!.jsonArray[0].jsonPrimitive.content shouldBe "모든피부타입"
    }

    @Test
    fun `title_ko fallback - onlineInfo 없을 때 masterInfo gdsNm 사용`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA99999")
                putJsonObject("masterInfo") {
                    put("gdsNm", "마스터 상품명")
                    put("gdsEngNm", "Master Product Name")
                }
                // onlineInfo 없음 → gdsNm fallback
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA99999",
        )

        result["title_ko"]!!.jsonPrimitive.content shouldBe "마스터 상품명"
    }

    @Test
    fun `빈 슬라이스 - CORE만 있을 때`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA11111")
                putJsonObject("masterInfo") {
                    put("gdsNm", "최소 상품")
                    putJsonObject("brand") { put("code", "MIN") }
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA11111",
        )

        result["title_ko"]!!.jsonPrimitive.content shouldBe "최소 상품"
        result["brand_code"]!!.jsonPrimitive.content shouldBe "MIN"
        result["search_keywords"]!!.jsonPrimitive.content shouldBe ""
        result["category_display"]!!.jsonArray shouldHaveSize 0
        result["attr_codes"]!!.jsonArray shouldHaveSize 0
        result["options"]!!.jsonArray shouldHaveSize 0
        result["badge_vegan"]!!.jsonPrimitive.content shouldBe "false"
        result["thumb_url"]!!.jsonPrimitive.content shouldBe ""
    }

    @Test
    fun `badge veganYn Y와 true 모두 true로 처리`() {
        val viewDataY = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-Y") }
            putJsonObject("INDEX") {
                putJsonObject("emblemInfo") {
                    put("veganYn", "Y")
                    put("cleanBeautyYn", "N")
                    put("crueltyFreeYn", "false")
                }
            }
        }
        val viewDataTrue = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-T") }
            putJsonObject("INDEX") {
                putJsonObject("emblemInfo") {
                    put("veganYn", "true")
                    put("cleanBeautyYn", "false")
                    put("crueltyFreeYn", "N")
                }
            }
        }

        val resultY = ProductStaticProjection.project(viewDataY, "oliveyoung", "PRODUCT:oliveyoung:UA-Y")
        val resultT = ProductStaticProjection.project(viewDataTrue, "oliveyoung", "PRODUCT:oliveyoung:UA-T")

        resultY["badge_vegan"]!!.jsonPrimitive.content shouldBe "true"
        resultT["badge_vegan"]!!.jsonPrimitive.content shouldBe "true"
    }

    @Test
    fun `attr_kv normalizeValue - 공백 정리`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-ATTR") }
            putJsonObject("INDEX") {
                putJsonArray("attributes") {
                    add(buildJsonObject {
                        put("attrCode", "1")
                        put("attrValue", "  수분  크림  ")
                    })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-ATTR",
        )

        val attrKv = result["attr_kv"]!!.jsonArray
        attrKv shouldHaveSize 1
        attrKv[0].jsonPrimitive.content shouldBe "1=수분 크림"
    }

    @Test
    fun `options - PRICE 없고 CORE에 options 있을 때 fallback`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-OPT")
                putJsonArray("options") {
                    add(buildJsonObject {
                        put("gdsCd", "SKU001")
                        put("gdsNm", "옵션A")
                    })
                }
            }
            // PRICE 슬라이스 없음
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-OPT",
        )

        val opts = result["options"]!!.jsonArray
        opts shouldHaveSize 1
        opts[0].jsonObject["sku"]!!.jsonPrimitive.content shouldBe "SKU001"
        opts[0].jsonObject["name"]!!.jsonPrimitive.content shouldBe "옵션A"
    }

    @Test
    fun `testRunId pass-through - E2E 필터용`() {
        val viewData = buildJsonObject {
            put("testRunId", "e2e-abc123")
            putJsonObject("CORE") {
                put("uaCode", "UA-E2E")
                putJsonObject("masterInfo") { put("gdsNm", "E2E 상품") }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-E2E",
        )

        result["testRunId"]!!.jsonPrimitive.content shouldBe "e2e-abc123"
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 엣지 케이스
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    fun `빈 viewData - 최소 필드만 출력`() {
        val viewData = buildJsonObject { }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-EMPTY",
        )

        result["tenantId"]!!.jsonPrimitive.content shouldBe "oliveyoung"
        result["entityKey"]!!.jsonPrimitive.content shouldBe "PRODUCT:oliveyoung:UA-EMPTY"
        result["uaCode"]!!.jsonPrimitive.content shouldBe ""
        result["productId"]!!.jsonPrimitive.content shouldBe "PRODUCT:oliveyoung:UA-EMPTY"
        result["title_ko"]!!.jsonPrimitive.content shouldBe ""
        result["brand_code"]!!.jsonPrimitive.content shouldBe ""
        result["search_keywords"]!!.jsonPrimitive.content shouldBe ""
        result["category_display"]!!.jsonArray shouldHaveSize 0
        result["category_std"]!!.jsonArray shouldHaveSize 0
        result["attr_codes"]!!.jsonArray shouldHaveSize 0
        result["attr_kv"]!!.jsonArray shouldHaveSize 0
        result["options"]!!.jsonArray shouldHaveSize 0
        result["thumb_url"]!!.jsonPrimitive.content shouldBe ""
        result.containsKey("testRunId") shouldBe false
    }

    @Test
    fun `uaCode top-level fallback - CORE 없을 때`() {
        val viewData = buildJsonObject {
            put("uaCode", "UA-TOPLEVEL")
            putJsonObject("INDEX") { }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-TOPLEVEL",
        )

        result["uaCode"]!!.jsonPrimitive.content shouldBe "UA-TOPLEVEL"
        result["productId"]!!.jsonPrimitive.content shouldBe "UA-TOPLEVEL"
    }

    @Test
    fun `prdtName blank일 때 gdsNm fallback`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-BLANK")
                putJsonObject("masterInfo") {
                    put("gdsNm", "실제 상품명")
                    put("gdsEngNm", "Real Name")
                }
                putJsonObject("onlineInfo") {
                    put("prdtName", "   ")
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-BLANK",
        )

        result["title_ko"]!!.jsonPrimitive.content shouldBe "실제 상품명"
    }

    @Test
    fun `category_display CORE fallback - CATEGORY 없을 때`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-CAT")
                putJsonArray("displayCategories") {
                    add(buildJsonObject { put("sclsCtgrNo", "2000000001") })
                    add(buildJsonObject { put("sclsCtgrNo", "2000000002") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-CAT",
        )

        val cat = result["category_display"]!!.jsonArray
        cat shouldHaveSize 2
        cat[0].jsonPrimitive.content shouldBe "2000000001"
        cat[1].jsonPrimitive.content shouldBe "2000000002"
    }

    @Test
    fun `category_std 부분 존재 - large만 있을 때`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-STD")
                putJsonObject("masterInfo") {
                    put("gdsNm", "x")
                    putJsonObject("standardCategory") {
                        putJsonObject("large") { put("code", "20") }
                    }
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-STD",
        )

        val std = result["category_std"]!!.jsonArray
        std shouldHaveSize 1
        std[0].jsonPrimitive.content shouldBe "L:20"
    }

    @Test
    fun `attrCode별 facet - main_functions, ingredients`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-FACET") }
            putJsonObject("INDEX") {
                putJsonArray("attributes") {
                    add(buildJsonObject { put("attrCode", "42"); put("attrValue", "보습") })
                    add(buildJsonObject { put("attrCode", "42"); put("attrValue", "미백") })
                    add(buildJsonObject { put("attrCode", "81"); put("attrValue", "히알루론산") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-FACET",
        )

        val mainFns = result["attr_main_functions"]!!.jsonArray.map { it.jsonPrimitive.content }
        mainFns shouldHaveSize 2
        mainFns.toSet() shouldBe setOf("보습", "미백")
        result["attr_ingredients"]!!.jsonArray shouldHaveSize 1
        result["attr_ingredients"]!!.jsonArray[0].jsonPrimitive.content shouldBe "히알루론산"
    }

    @Test
    fun `attr facets - 매핑 없는 attrCode는 facet 필드에 없음`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-OTHER") }
            putJsonObject("INDEX") {
                putJsonArray("attributes") {
                    add(buildJsonObject { put("attrCode", "99"); put("attrValue", "SPF50") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-OTHER",
        )

        result["attr_kv"]!!.jsonArray[0].jsonPrimitive.content shouldBe "99=SPF50"
        result["attr_formulation"] shouldBe null
        result["attr_skin_type"] shouldBe null
        result["attr_main_functions"] shouldBe null
        result["attr_ingredients"] shouldBe null
    }

    @Test
    fun `attributes attrValue 없으면 attr_kv에서 제외`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-NOVAL") }
            putJsonObject("INDEX") {
                putJsonArray("attributes") {
                    add(buildJsonObject { put("attrCode", "1"); put("attrValue", "있음") })
                    add(buildJsonObject { put("attrCode", "2") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-NOVAL",
        )

        result["attr_codes"]!!.jsonArray shouldHaveSize 2
        result["attr_kv"]!!.jsonArray shouldHaveSize 1
        result["attr_kv"]!!.jsonArray[0].jsonPrimitive.content shouldBe "1=있음"
    }

    @Test
    fun `attributes 중복 제거 및 정렬`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-DUP") }
            putJsonObject("INDEX") {
                putJsonArray("attributes") {
                    add(buildJsonObject { put("attrCode", "9"); put("attrValue", "A") })
                    add(buildJsonObject { put("attrCode", "2"); put("attrValue", "B") })
                    add(buildJsonObject { put("attrCode", "2"); put("attrValue", "B") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-DUP",
        )

        val codes = result["attr_codes"]!!.jsonArray
        codes shouldHaveSize 2
        codes[0].jsonPrimitive.content shouldBe "2"
        codes[1].jsonPrimitive.content shouldBe "9"

        val kv = result["attr_kv"]!!.jsonArray
        kv shouldHaveSize 2
        kv[0].jsonPrimitive.content shouldBe "2=B"
        kv[1].jsonPrimitive.content shouldBe "9=A"
    }

    @Test
    fun `options gdsCd gdsNm 없으면 빈 문자열`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-OPTOPT")
                putJsonArray("options") {
                    add(buildJsonObject { })
                    add(buildJsonObject { put("gdsCd", "SKU1") })
                    add(buildJsonObject { put("gdsNm", "이름만") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-OPTOPT",
        )

        val opts = result["options"]!!.jsonArray
        opts shouldHaveSize 3
        opts[0].jsonObject["sku"]!!.jsonPrimitive.content shouldBe ""
        opts[0].jsonObject["name"]!!.jsonPrimitive.content shouldBe ""
        opts[1].jsonObject["sku"]!!.jsonPrimitive.content shouldBe "SKU1"
        opts[1].jsonObject["name"]!!.jsonPrimitive.content shouldBe ""
        opts[2].jsonObject["sku"]!!.jsonPrimitive.content shouldBe ""
        opts[2].jsonObject["name"]!!.jsonPrimitive.content shouldBe "이름만"
    }

    @Test
    fun `search_keywords 빈 문자열 및 구분자만`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-KW") }
            putJsonObject("INDEX") {
                putJsonObject("additionalInfo") {
                    put("srchKeyWordText", "  ,  ,  \n  ")
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-KW",
        )

        result["search_keywords"]!!.jsonPrimitive.content shouldBe ""
    }

    @Test
    fun `emblemInfo 없으면 badge 모두 false`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-NOEMBLEM") }
            putJsonObject("INDEX") { }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-NOEMBLEM",
        )

        result["badge_vegan"]!!.jsonPrimitive.content shouldBe "false"
        result["badge_clean"]!!.jsonPrimitive.content shouldBe "false"
        result["badge_cruelty_free"]!!.jsonPrimitive.content shouldBe "false"
    }

    @Test
    fun `thumbnailImages MEDIA 없고 CORE에 있을 때 fallback`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-THUMB")
                putJsonArray("thumbnailImages") {
                    add(buildJsonObject { put("url", "https://core-fallback.jpg") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-THUMB",
        )

        result["thumb_url"]!!.jsonPrimitive.content shouldBe "https://core-fallback.jpg"
    }

    @Test
    fun `thumbnailImages 빈 배열`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-EMPTYTHUMB") }
            putJsonObject("MEDIA") {
                putJsonArray("thumbnailImages") { }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-EMPTYTHUMB",
        )

        result["thumb_url"]!!.jsonPrimitive.content shouldBe ""
    }

    @Test
    fun `displayCategories 중복 sclsCtgrNo 제거`() {
        val viewData = buildJsonObject {
            putJsonObject("CORE") { put("uaCode", "UA-CATDUP") }
            putJsonObject("CATEGORY") {
                putJsonArray("displayCategories") {
                    add(buildJsonObject { put("sclsCtgrNo", "3000000001") })
                    add(buildJsonObject { put("sclsCtgrNo", "3000000001") })
                    add(buildJsonObject { put("sclsCtgrNo", "3000000002") })
                }
            }
        }

        val result = ProductStaticProjection.project(
            viewData = viewData,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA-CATDUP",
        )

        val cat = result["category_display"]!!.jsonArray
        cat shouldHaveSize 2
        cat[0].jsonPrimitive.content shouldBe "3000000001"
        cat[1].jsonPrimitive.content shouldBe "3000000002"
    }
}
