import org.jooq.meta.jaxb.Logging

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    application
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    id("org.flywaydb.flyway") version "10.10.0"
    id("org.jooq.jooq-codegen-gradle") version "3.19.6"
}

repositories { mavenCentral() }

// ============================================
// Version Catalog (RFC-IMPL-009 SSOT)
// ============================================
val ktorVersion = "2.3.9"
val koinVersion = "3.5.3"
val hopliteVersion = "2.7.5"
val otelVersion = "1.36.0"
val micrometerVersion = "1.12.4"
val resilience4jVersion = "2.2.0"
val kotestVersion = "5.9.1"
val testcontainersVersion = "1.21.3"

// ============================================
// Database Configuration (로컬 개발용)
// ============================================
val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/ivmlite"
val dbUser = System.getenv("DB_USER") ?: "ivm"
val dbPassword = System.getenv("DB_PASSWORD") ?: "ivm_local_dev"

dependencies {
    // ============================================
    // Kotlin Core
    // ============================================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Arrow (Functional Programming - for Either, Option)
    implementation("io.arrow-kt:arrow-core:1.2.1")
    
    // Jackson (for CanonicalJson - RFC8785 compatibility)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // ============================================
    // HTTP Server (Ktor) - RFC-IMPL-009: Netty 고정
    // ============================================
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // ============================================
    // HTTP Client (Ktor) - RFC-IMPL-009: CIO 고정
    // ============================================
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // ============================================
    // DI (Koin) - RFC-IMPL-009
    // ============================================
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // ============================================
    // Config (Hoplite) - RFC-IMPL-009
    // ============================================
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")

    // ============================================
    // Observability - RFC-IMPL-009
    // ============================================
    // OpenTelemetry (Tracing SSOT)
    implementation("io.opentelemetry:opentelemetry-api:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$otelVersion")
    
    // Ktor OTel instrumentation (하이브리드용)
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-2.0:2.23.0-alpha")
    
    // Micrometer (Metrics SSOT)
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    
    // Logging (JSON structured)
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // ============================================
    // Resilience - RFC-IMPL-009 (adapters에서만 사용)
    // ============================================
    implementation("io.github.resilience4j:resilience4j-kotlin:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-micrometer:$resilience4jVersion")

    // ============================================
    // Database (PostgreSQL + jOOQ + HikariCP)
    // ============================================
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jooq:jooq:3.19.6")
    implementation("org.jooq:jooq-kotlin:3.19.6")
    implementation("org.jooq:jooq-kotlin-coroutines:3.19.6")

    // jOOQ codegen (빌드 시에만 사용)
    jooqCodegen("org.postgresql:postgresql:42.7.3")

    // ============================================
    // AWS SDK v2 (DynamoDB - Schema Registry)
    // ============================================
    implementation(platform("software.amazon.awssdk:bom:2.25.67"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:netty-nio-client")

    // ============================================
    // YAML for contract registry (v1 local mode)
    // ============================================
    implementation("org.yaml:snakeyaml:2.2")

    // ============================================
    // CLI
    // ============================================
    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    // ============================================
    // Test Dependencies
    // ============================================
    testImplementation(kotlin("test"))
    
    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    
    // MockK
    testImplementation("io.mockk:mockk:1.13.10")
    
    // ArchUnit (RFC-V4-010, RFC-IMPL-009)
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    
    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    
    // Flyway for test migrations
    testImplementation("org.flywaydb:flyway-core:10.10.0")
    testImplementation("org.flywaydb:flyway-database-postgresql:10.10.0")
    
    // Ktor Test
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    
    // Koin Test (exclude conflicting kotlin-test-junit)
    testImplementation("io.insert-koin:koin-test:$koinVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
    }
}

// ============================================
// Flyway Configuration
// ============================================
flyway {
    url = dbUrl
    user = dbUser
    password = dbPassword
    locations = arrayOf("classpath:db/migration")
    cleanDisabled = false  // 로컬 개발용 (운영에서는 true)
}

// ============================================
// jOOQ Code Generation
// ============================================
jooq {
    configuration {
        logging = Logging.WARN
        jdbc {
            driver = "org.postgresql.Driver"
            url = dbUrl
            user = dbUser
            password = dbPassword
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
            }
            generate {
                isDeprecated = false
                isRecords = true
                isPojos = true
                isPojosAsKotlinDataClasses = true
                isKotlinNotNullPojoAttributes = true
                isKotlinNotNullRecordAttributes = true
                isKotlinNotNullInterfaceAttributes = true
            }
            target {
                packageName = "com.oliveyoung.ivmlite.generated.jooq"
                directory = "build/generated-src/jooq/main"
            }
        }
    }
}

// Generated jOOQ 코드를 소스셋에 추가
sourceSets {
    main {
        kotlin {
            srcDir("build/generated-src/jooq/main")
            srcDir("build/generated/kotlin")  // ViewCodeGen 출력
        }
    }
}

// ============================================
// Contract CodeGen - YAML → Kotlin 코드 생성
// ============================================

// View 코드젠 (읽기용)
tasks.register<JavaExec>("generateViews") {
    group = "codegen"
    description = "Generate ViewRef classes from VIEW_DEFINITION contracts"
    
    mainClass.set("com.oliveyoung.ivmlite.tooling.codegen.ViewCodeGenKt")
    classpath = sourceSets.main.get().runtimeClasspath
    
    args = listOf(
        "--contracts", "src/main/resources/contracts",
        "--output", "build/generated/kotlin",
        "--package", "com.oliveyoung.ivmlite.sdk.schema.generated"
    )
    
    inputs.dir("src/main/resources/contracts")
    outputs.dir("build/generated/kotlin")
}

// Entity 코드젠 (쓰기용)
tasks.register<JavaExec>("generateEntities") {
    group = "codegen"
    description = "Generate EntityBuilder classes from ENTITY_SCHEMA contracts"
    
    mainClass.set("com.oliveyoung.ivmlite.tooling.codegen.EntityCodeGenKt")
    classpath = sourceSets.main.get().runtimeClasspath
    
    args = listOf(
        "--contracts", "src/main/resources/contracts",
        "--output", "build/generated/kotlin",
        "--package", "com.oliveyoung.ivmlite.sdk.schema.generated"
    )
    
    inputs.dir("src/main/resources/contracts")
    outputs.dir("build/generated/kotlin")
}

// 전체 스키마 코드젠 (Views + Entities)
tasks.register("generateSchema") {
    group = "codegen"
    description = "Generate all schema classes (Views + Entities) from contracts"
    dependsOn("generateViews", "generateEntities")
}

// ============================================
// Task Dependencies
// jOOQ codegen은 DB 연결이 필요하므로 수동 실행
// ============================================
tasks.named("jooqCodegen") {
    // 이 태스크는 수동 실행 (DB 연결 필요)
    // ./gradlew jooqCodegen
    inputs.files(fileTree("src/main/resources/db/migration"))
    outputs.dir("build/generated-src/jooq/main")
}

// compileKotlin은 jooqCodegen 없이도 가능 (InMemory 어댑터 사용 시)
// jOOQ 코드가 필요할 때만 수동으로 jooqCodegen 실행

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    // 기본적으로 통합 테스트 제외 (Docker 필요)
    // 포함하려면: ./gradlew test -Dkotest.tags.include=IntegrationTag
    systemProperty("kotest.tags.exclude", System.getProperty("kotest.tags.exclude") ?: "IntegrationTag")
}

// 통합 테스트 전용 태스크 (Docker/Testcontainers 필요)
tasks.register<Test>("integrationTest") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    systemProperty("kotest.tags.include", "IntegrationTag")
    description = "Run integration tests (requires Docker)"
    group = "verification"
}

// CI 체크 태스크
tasks.register("checkAll") {
    dependsOn("test", "detekt")
    description = "Run all checks (tests + lint)"
}

// Semgrep 정적 분석 (보안/버그 패턴). 사전: pip install semgrep / brew install semgrep
tasks.register<Exec>("semgrep") {
    group = "verification"
    description = "Run Semgrep static analysis (security/bug patterns)"
    commandLine("bash", "$projectDir/scripts/semgrep.sh", "src/")
}

application {
    mainClass.set("com.oliveyoung.ivmlite.apps.runtimeapi.ApplicationKt")
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

// JVM Toolchain (일관된 JVM 버전)
kotlin {
    jvmToolchain(17)
}

// Kotlin 컴파일 옵션
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

// Java 컴파일 옵션
tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
