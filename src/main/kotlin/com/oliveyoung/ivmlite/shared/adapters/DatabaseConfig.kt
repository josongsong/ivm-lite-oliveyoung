package com.oliveyoung.ivmlite.shared.adapters

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

/**
 * Database Configuration
 *
 * Exposed Database를 제공하며, HikariCP 커넥션 풀 사용.
 * 환경 변수로 설정 주입 가능.
 */
object DatabaseConfig {

    data class DbProperties(
        val url: String = System.getenv("DB_URL") ?: error("DB_URL is required"),
        val user: String = System.getenv("DB_USER") ?: error("DB_USER is required"),
        val password: String = System.getenv("DB_PASSWORD") ?: error("DB_PASSWORD is required"),
        val maxPoolSize: Int = System.getenv("DB_MAX_POOL_SIZE")?.toIntOrNull() ?: 10,
        val minIdle: Int = System.getenv("DB_MIN_IDLE")?.toIntOrNull() ?: 2,
    )

    /**
     * HikariCP DataSource 생성
     */
    fun createDataSource(props: DbProperties = DbProperties()): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = props.url
            username = props.user
            password = props.password
            maximumPoolSize = props.maxPoolSize
            minimumIdle = props.minIdle

            // PostgreSQL 최적화
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

            // 커넥션 테스트
            connectionTestQuery = "SELECT 1"

            // 풀 이름 (로깅/모니터링용)
            poolName = "ivm-lite-pool"
        }
        return HikariDataSource(config)
    }

    /**
     * Exposed Database 연결
     */
    fun connectDatabase(dataSource: DataSource): Database {
        return Database.connect(dataSource)
    }

    /**
     * 기본 설정으로 Database 연결 (편의 메서드)
     */
    fun defaultDatabase(): Database {
        return connectDatabase(createDataSource())
    }
}
