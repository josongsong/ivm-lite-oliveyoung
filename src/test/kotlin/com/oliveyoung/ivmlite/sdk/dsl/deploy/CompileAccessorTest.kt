package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.TargetRef
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompileAccessorTest {

    @Test
    fun `sync() 호출 시 CompileMode_Sync 설정`() {
        var capturedMode: CompileMode? = null
        val accessor = CompileAccessor { capturedMode = it }

        accessor.sync()

        assertEquals(CompileMode.Sync, capturedMode)
    }

    @Test
    fun `async() 호출 시 CompileMode_Async 설정`() {
        var capturedMode: CompileMode? = null
        val accessor = CompileAccessor { capturedMode = it }

        accessor.async()

        assertEquals(CompileMode.Async, capturedMode)
    }

    @Test
    fun `invoke로 targets 설정 시 SyncWithTargets 반환`() {
        var capturedMode: CompileMode? = null
        val accessor = CompileAccessor { capturedMode = it }

        accessor {
            targets {
                searchDoc()
                recoFeed()
            }
        }

        assertTrue(capturedMode is CompileMode.SyncWithTargets)
        val syncWithTargets = capturedMode as CompileMode.SyncWithTargets
        assertEquals(2, syncWithTargets.targets.size)
        assertEquals(TargetRef("search-doc", "v1"), syncWithTargets.targets[0])
        assertEquals(TargetRef("reco-feed", "v1"), syncWithTargets.targets[1])
    }

    @Test
    fun `invoke로 custom target 설정`() {
        var capturedMode: CompileMode? = null
        val accessor = CompileAccessor { capturedMode = it }

        accessor {
            targets {
                custom("my-target", "v2")
            }
        }

        assertTrue(capturedMode is CompileMode.SyncWithTargets)
        val syncWithTargets = capturedMode as CompileMode.SyncWithTargets
        assertEquals(1, syncWithTargets.targets.size)
        assertEquals(TargetRef("my-target", "v2"), syncWithTargets.targets[0])
    }

    @Test
    fun `invoke로 빈 targets 설정`() {
        var capturedMode: CompileMode? = null
        val accessor = CompileAccessor { capturedMode = it }

        accessor {
            targets {
                // 빈 설정
            }
        }

        assertTrue(capturedMode is CompileMode.SyncWithTargets)
        val syncWithTargets = capturedMode as CompileMode.SyncWithTargets
        assertTrue(syncWithTargets.targets.isEmpty())
    }
}
