package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.TargetRef
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompileTargetsBuilderTest {

    @Test
    fun `searchDoc() 호출 시 search-doc 타겟 추가`() {
        val builder = CompileTargetsBuilder()
        builder.targets {
            searchDoc()
        }

        val targets = builder.build()
        assertEquals(1, targets.size)
        assertEquals(TargetRef("search-doc", "v1"), targets[0])
    }

    @Test
    fun `recoFeed() 호출 시 reco-feed 타겟 추가`() {
        val builder = CompileTargetsBuilder()
        builder.targets {
            recoFeed()
        }

        val targets = builder.build()
        assertEquals(1, targets.size)
        assertEquals(TargetRef("reco-feed", "v1"), targets[0])
    }

    @Test
    fun `custom() 호출 시 커스텀 타겟 추가`() {
        val builder = CompileTargetsBuilder()
        builder.targets {
            custom("my-target", "v2")
        }

        val targets = builder.build()
        assertEquals(1, targets.size)
        assertEquals(TargetRef("my-target", "v2"), targets[0])
    }

    @Test
    fun `여러 타겟 동시 추가`() {
        val builder = CompileTargetsBuilder()
        builder.targets {
            searchDoc()
            recoFeed()
            custom("my-target", "v3")
        }

        val targets = builder.build()
        assertEquals(3, targets.size)
        assertEquals(TargetRef("search-doc", "v1"), targets[0])
        assertEquals(TargetRef("reco-feed", "v1"), targets[1])
        assertEquals(TargetRef("my-target", "v3"), targets[2])
    }

    @Test
    fun `버전 커스터마이징`() {
        val builder = CompileTargetsBuilder()
        builder.targets {
            searchDoc("v2")
            recoFeed("v3")
        }

        val targets = builder.build()
        assertEquals(2, targets.size)
        assertEquals(TargetRef("search-doc", "v2"), targets[0])
        assertEquals(TargetRef("reco-feed", "v3"), targets[1])
    }

    @Test
    fun `빈 빌더는 빈 리스트 반환`() {
        val builder = CompileTargetsBuilder()
        builder.targets {
            // 빈 설정
        }

        val targets = builder.build()
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `build()는 불변 리스트 반환`() {
        val builder = CompileTargetsBuilder()
        builder.targets {
            searchDoc()
        }

        val targets1 = builder.build()
        val targets2 = builder.build()

        assertEquals(targets1.size, targets2.size)
        assertEquals(1, targets1.size)
    }

    @Test
    fun `targets 블록 없이 build() 호출 시 빈 리스트`() {
        val builder = CompileTargetsBuilder()
        val targets = builder.build()

        assertTrue(targets.isEmpty())
    }
}
