package com.oliveyoung.ivmlite.integration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.testcontainers.DockerClientFactory
import java.io.File

/**
 * Docker 가용성 확인용 테스트 (디버깅용)
 */
class DockerCheckTest : StringSpec({

    "Docker is available" {
        // Check socket files
        val socket1 = File("/var/run/docker.sock")
        val socket2 = File(System.getProperty("user.home") + "/.docker/run/docker.sock")
        println("Socket /var/run/docker.sock exists: ${socket1.exists()}, readable: ${socket1.canRead()}")
        println("Socket ~/.docker/run/docker.sock exists: ${socket2.exists()}, readable: ${socket2.canRead()}")
        println("DOCKER_HOST env: ${System.getenv("DOCKER_HOST")}")
        println("testcontainers.docker.host prop: ${System.getProperty("testcontainers.docker.host")}")

        val available = try {
            DockerClientFactory.instance().isDockerAvailable
        } catch (e: Exception) {
            println("Docker check exception: ${e::class.java.name}: ${e.message}")
            e.printStackTrace()
            false
        }
        println("Docker available: $available")
        available shouldBe true
    }

    "PostgresTestContainer.isDockerAvailable returns true" {
        println("PostgresTestContainer.isDockerAvailable: ${PostgresTestContainer.isDockerAvailable}")
        PostgresTestContainer.isDockerAvailable shouldBe true
    }
})
