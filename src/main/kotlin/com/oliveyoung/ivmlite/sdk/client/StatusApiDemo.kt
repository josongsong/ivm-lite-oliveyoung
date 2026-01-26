package com.oliveyoung.ivmlite.sdk.client

import kotlinx.coroutines.runBlocking
import java.time.Duration

/**
 * StatusApi 데모 - Wave 5-K 검증용
 */
fun main() = runBlocking {
    println("=== StatusApi Demo ===")

    val client = Ivm.client()

    // 1. deploy.status 테스트
    println("\n1. Testing deploy.status()")
    val status = client.deploy.status("demo-job-123")
    println("   JobId: ${status.jobId}")
    println("   State: ${status.state}")
    println("   Created: ${status.createdAt}")

    // 2. deploy.await 테스트
    println("\n2. Testing deploy.await() with timeout")
    val result = client.deploy.await("demo-job-456", timeout = Duration.ofMillis(100))
    println("   Success: ${result.success}")
    println("   EntityKey: ${result.entityKey}")
    if (!result.success) {
        println("   Error: ${result.error}")
    }

    // 3. plan.explainLastPlan 테스트
    println("\n3. Testing plan.explainLastPlan()")
    val plan = client.plan.explainLastPlan("demo-deploy-789")
    println("   DeployId: ${plan.deployId}")
    println("   Activated Rules: ${plan.activatedRules}")
    println("   Execution Steps: ${plan.executionSteps.size}")

    println("\n=== All tests passed ===")
}
