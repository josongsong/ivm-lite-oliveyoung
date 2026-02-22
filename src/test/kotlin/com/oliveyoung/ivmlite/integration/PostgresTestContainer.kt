package com.oliveyoung.ivmlite.integration

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer

/**
 * PostgreSQL Testcontainer 공통 설정 (RFC-IMPL Phase B-4)
 *
 * Flyway 마이그레이션 포함. 테스트 간 트랜잭션 롤백으로 격리.
 */
object PostgresTestContainer {

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("ivmlite_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
    }

    private var initialized = false

    /**
     * Docker 사용 가능 여부 확인
     */
    val isDockerAvailable: Boolean by lazy {
        try {
            DockerClientFactory.instance().isDockerAvailable
        } catch (e: Exception) {
            false
        }
    }

    fun start(): Database {
        require(isDockerAvailable) { "Docker is not available. Skipping integration tests." }

        if (!container.isRunning) {
            container.start()
        }

        if (!initialized) {
            runMigrations()
            initialized = true
        }

        return connectDatabase()
    }

    private fun runMigrations() {
        Flyway.configure()
            .dataSource(container.jdbcUrl, container.username, container.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun connectDatabase(): Database {
        return Database.connect(
            url = container.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = container.username,
            password = container.password
        )
    }

    fun jdbcUrl(): String = container.jdbcUrl
    fun username(): String = container.username
    fun password(): String = container.password
}
