package com.oliveyoung.ivmlite.shared.adapters

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

/**
 * Database Configuration
 * 
 * jOOQ DSLContext를 제공하며, HikariCP 커넥션 풀 사용.
 * 환경 변수로 설정 주입 가능.
 */
object DatabaseConfig {

    data class DbProperties(
        // Remote-only: 로컬 기본값 제거 (환경 변수로만 주입)
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
     * jOOQ DSLContext 생성
     * 
     * 이 Context를 통해 타입 안전한 SQL 실행 가능.
     * jOOQ 코드 생성기가 만든 테이블/컬럼 클래스를 사용.
     */
    fun createDSLContext(dataSource: DataSource): DSLContext {
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }

    /**
     * 기본 설정으로 DSLContext 생성 (편의 메서드)
     */
    fun defaultDSLContext(): DSLContext {
        return createDSLContext(createDataSource())
    }
}
