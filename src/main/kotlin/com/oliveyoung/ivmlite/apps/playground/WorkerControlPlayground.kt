package com.oliveyoung.ivmlite.apps.playground

import com.oliveyoung.ivmlite.sdk.Ivm
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import kotlinx.coroutines.runBlocking

/**
 * OutboxPollingWorker 제어 예제
 * 
 * SDK에서 Worker를 시작/중지하는 방법을 보여줍니다.
 */
fun main() = runBlocking {
    // 1. SDK 설정
    Ivm.configure {
        // tenantId는 내부적으로 설정됨
    }
    
    // 2. Worker 주입 (DI 컨테이너에서 가져온 경우)
    // 실제로는 Koin 등에서 주입받아야 합니다
    // val worker = getKoin().get<OutboxPollingWorker>()
    // Ivm.setWorker(worker)
    
    // 3. Worker 시작
    println("Starting OutboxPollingWorker...")
    try {
        val started = Ivm.worker.start()
        if (started) {
            println("✅ Worker started successfully")
        } else {
            println("⚠️  Worker not started (already running or disabled)")
        }
        
        // 4. Worker 상태 확인
        if (Ivm.worker.isRunning()) {
            println("✅ Worker is running")
        } else {
            println("❌ Worker is not running")
        }
        
        // 5. 잠시 대기 (실제 처리 확인)
        kotlinx.coroutines.delay(5000)
        
        // 6. Worker 중지
        println("Stopping OutboxPollingWorker...")
        val stopped = Ivm.worker.stop()
        if (stopped) {
            println("✅ Worker stopped successfully")
        } else {
            println("⚠️  Worker not stopped (not running)")
        }
    } catch (e: IllegalStateException) {
        println("❌ ${e.message}")
        println("💡 Worker를 주입하려면: Ivm.setWorker(worker)")
    }
}
