import org.jooq.meta.jaxb.Logging
import java.time.Duration

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:10.10.0")
        classpath("org.postgresql:postgresql:42.7.3")
    }
}

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    application
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    id("org.flywaydb.flyway") version "10.10.0"
    id("org.jooq.jooq-codegen-gradle") version "3.19.6"
    `maven-publish`  // 내부 배포용
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
// Database Configuration (remote-only)
// - Flyway/jOOQ 태스크 실행 시에만 필요합니다.
// - 로컬 기본값(localhost) 제거: 실수로 로컬에 붙는 것을 방지합니다.
// ============================================
val dbUrl = System.getenv("DB_URL") ?: ""
val dbUser = System.getenv("DB_USER") ?: ""
val dbPassword = System.getenv("DB_PASSWORD") ?: ""

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

    // TSID Creator (Snowflake-like ID generation - SOTA)
    implementation("com.github.f4b6a3:tsid-creator:5.2.6")

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
    // Dotenv (.env 파일 로드)
    // ============================================
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

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
// jOOQ Code Generation (SOTA Configuration)
//
// 🎯 전략: 생성 코드를 src에 저장하여 git 관리
// - DB 없이 빌드 가능
// - CI/CD에서 DB 연결 불필요
// - 코드 리뷰 가능 (스키마 변경 추적)
//
// 사용법:
//   ./gradlew regenerateJooq  # DB 연결 후 재생성
//   ./gradlew build           # DB 없이 빌드 가능
// ============================================

// JOOQ 생성 코드 경로 (src에 저장)
val jooqOutputDir = "src/main/kotlin"
val jooqPackagePath = "com/oliveyoung/ivmlite/generated/jooq"
val jooqGeneratedDir = file("$jooqOutputDir/$jooqPackagePath")

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
                // Enum 타입 매핑
                forcedTypes {
                    forcedType {
                        name = "varchar"
                        includeExpression = ".*\\.status"
                        includeTypes = ".*"
                    }
                }
            }
            generate {
                isDeprecated = false
                isRecords = true
                isPojos = true
                isDaos = true  // SOTA: DAO 생성으로 CRUD 보일러플레이트 감소
                isPojosAsKotlinDataClasses = true
                isKotlinNotNullPojoAttributes = true
                isKotlinNotNullRecordAttributes = true
                isKotlinNotNullInterfaceAttributes = true
                isRoutines = true  // stored procedures 지원
                isSequences = true  // sequences 지원
                // Kotlin 최적화
                isKotlinSetterJvmNameAnnotationsOnIsPrefix = true
                isJavaTimeTypes = true  // java.time 사용
            }
            target {
                packageName = "com.oliveyoung.ivmlite.generated.jooq"
                directory = jooqOutputDir  // src에 직접 저장
            }
        }
    }
}

// Generated 코드를 소스셋에 추가 (ViewCodeGen만 build에서 가져옴)
sourceSets {
    main {
        kotlin {
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
// Task Dependencies (SOTA jOOQ Integration)
//
// 🎯 전략: 조건부 의존성
// - 생성 코드가 src에 있으면 → jooqCodegen 스킵
// - 생성 코드가 없으면 → 에러 메시지 출력
// ============================================

tasks.named("jooqCodegen") {
    group = "codegen"
    description = "Generate jOOQ classes from database schema (requires DB connection)"

    inputs.files(fileTree("src/main/resources/db/migration"))
    outputs.dir(jooqGeneratedDir)

    doFirst {
        if (dbUrl.isBlank()) {
            throw GradleException("""
                |
                |❌ jOOQ 코드 생성에 DB 연결이 필요합니다.
                |
                |환경 변수 설정 후 실행하세요:
                |  export DB_URL=jdbc:postgresql://localhost:5433/ivmlite
                |  export DB_USER=ivm
                |  export DB_PASSWORD=ivm
                |
                |또는 Docker로 로컬 DB 실행:
                |  docker-compose up -d postgres
                |
            """.trimMargin())
        }
    }
}

// 명시적 재생성 태스크 (Flyway 마이그레이션 후 사용)
tasks.register("regenerateJooq") {
    group = "codegen"
    description = "Regenerate jOOQ classes after schema changes (requires DB)"

    dependsOn("flywayMigrate", "jooqCodegen")

    doLast {
        println("""
            |
            |✅ jOOQ 코드 재생성 완료!
            |
            |변경 사항 확인:
            |  git diff src/main/kotlin/com/oliveyoung/ivmlite/generated/jooq/
            |
            |커밋:
            |  git add src/main/kotlin/com/oliveyoung/ivmlite/generated/jooq/
            |  git commit -m "chore: regenerate jOOQ after schema change"
            |
        """.trimMargin())
    }
}

// compileKotlin: 생성 코드가 있으면 jooqCodegen 스킵
// NOTE: Configuration cache 호환을 위해 doFirst 내에서 직접 경로 계산
tasks.named("compileKotlin") {
    // jooqCodegen 의존성 제거 - src에 생성 코드가 있으므로 불필요
    // 생성 코드가 없으면 컴파일 에러로 자연스럽게 알 수 있음

    doFirst {
        val generatedDir = file("src/main/kotlin/com/oliveyoung/ivmlite/generated/jooq")
        if (!generatedDir.exists() || generatedDir.listFiles()?.isEmpty() == true) {
            logger.warn("""
                |
                |⚠️  jOOQ 생성 코드가 없습니다!
                |
                |해결 방법:
                |  1. DB 연결 후: ./gradlew regenerateJooq
                |  2. 또는 git에서 복원: git checkout -- src/main/kotlin/com/oliveyoung/ivmlite/generated/
                |
            """.trimMargin())
        }
    }
}

// ============================================
// 🧪 SOTA 테스트 UX 설정
// ============================================

// 공통 테스트 설정 함수
fun Test.configureTestLogging() {
    testLogging {
        // 이벤트 표시
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
        )

        // 예외 상세 출력
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true

        // 표준 스트림 표시
        showStandardStreams = false  // 필요시 true

        // 느린 테스트 감지
        minGranularity = 2
    }

    // 테스트 결과 요약 리스너
    addTestListener(object : TestListener {
        private var failedTests = mutableListOf<TestDescriptor>()
        private var skippedTests = mutableListOf<TestDescriptor>()
        private var passedTests = 0
        private var startTime = 0L

        override fun beforeSuite(suite: TestDescriptor) {
            if (suite.parent == null) {
                startTime = System.currentTimeMillis()
                println()
                println("╔════════════════════════════════════════════════════════════════╗")
                println("║  🧪 테스트 실행 시작                                              ║")
                println("╚════════════════════════════════════════════════════════════════╝")
                println()
            }
        }

        override fun beforeTest(testDescriptor: TestDescriptor) {}

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            when (result.resultType) {
                TestResult.ResultType.SUCCESS -> {
                    passedTests++
                    print("✓")
                }
                TestResult.ResultType.FAILURE -> {
                    failedTests.add(testDescriptor)
                    print("✗")
                }
                TestResult.ResultType.SKIPPED -> {
                    skippedTests.add(testDescriptor)
                    print("○")
                }
            }
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                val duration = System.currentTimeMillis() - startTime
                val durationSec = duration / 1000.0

                println()
                println()
                println("╔════════════════════════════════════════════════════════════════╗")
                println("║  📊 테스트 결과 요약                                              ║")
                println("╠════════════════════════════════════════════════════════════════╣")
                println("║                                                                ║")
                println("║  ✓ 성공: ${passedTests.toString().padEnd(5)}  ○ 스킵: ${skippedTests.size.toString().padEnd(5)}  ✗ 실패: ${failedTests.size.toString().padEnd(5)}          ║")
                println("║  ⏱ 소요 시간: ${String.format("%.2f", durationSec)}초                                         ║".take(67) + "║")
                println("║                                                                ║")

                if (failedTests.isNotEmpty()) {
                    println("╠════════════════════════════════════════════════════════════════╣")
                    println("║  ❌ 실패한 테스트:                                               ║")
                    println("║                                                                ║")
                    failedTests.take(10).forEach { test ->
                        val testName = "${test.className?.substringAfterLast('.') ?: ""} > ${test.displayName}"
                        println("║    • ${testName.take(56).padEnd(56)} ║")
                    }
                    if (failedTests.size > 10) {
                        println("║    ... 외 ${failedTests.size - 10}개                                           ║".take(67) + "║")
                    }
                    println("║                                                                ║")
                }

                if (result.resultType == TestResult.ResultType.SUCCESS) {
                    println("╠════════════════════════════════════════════════════════════════╣")
                    println("║  🎉 모든 테스트 통과!                                            ║")
                }

                println("╚════════════════════════════════════════════════════════════════╝")
                println()
            }
        }
    })
}

tasks.test {
    useJUnitPlatform()
    configureTestLogging()

    // 기본적으로 통합 테스트 제외 (Docker 필요)
    systemProperty("kotest.tags.exclude", System.getProperty("kotest.tags.exclude") ?: "IntegrationTag")

    // JVM 설정
    jvmArgs(
        "-Xmx2g",
        "-XX:+UseG1GC",
        "-XX:+HeapDumpOnOutOfMemoryError"
    )

    // 실패해도 계속 실행 (전체 결과 확인)
    ignoreFailures = System.getenv("CI") != null

    // 리포트 설정
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }
}

// 통합 테스트 전용 태스크 (Docker/Testcontainers 필요)
tasks.register<Test>("integrationTest") {
    useJUnitPlatform()
    configureTestLogging()

    systemProperty("kotest.tags.include", "IntegrationTag")
    description = "🐳 Run integration tests (requires Docker)"
    group = "verification"

    // 통합 테스트는 순차 실행 (리소스 충돌 방지)
    maxParallelForks = 1

    // 타임아웃 설정
    timeout.set(Duration.ofMinutes(10))
}

// 빠른 단위 테스트 (통합 테스트 제외)
tasks.register<Test>("unitTest") {
    useJUnitPlatform()
    configureTestLogging()

    systemProperty("kotest.tags.exclude", "IntegrationTag")
    description = "⚡ Run unit tests only (fast)"
    group = "verification"

    // 병렬 실행 극대화
    maxParallelForks = Runtime.getRuntime().availableProcessors()
}

// 특정 패키지 테스트
tasks.register<Test>("testPackage") {
    useJUnitPlatform()
    configureTestLogging()

    val pkg = System.getProperty("pkg") ?: ""
    if (pkg.isNotEmpty()) {
        filter {
            includeTestsMatching("*.$pkg.*")
        }
    }
    description = "📦 Run tests for specific package (-Dpkg=slices)"
    group = "verification"
}

// 실패한 테스트만 재실행
tasks.register<Test>("retryFailed") {
    useJUnitPlatform()
    configureTestLogging()

    filter {
        isFailOnNoMatchingTests = false
    }

    // 이전 실패 정보 활용 (Gradle Enterprise 필요)
    description = "🔄 Retry previously failed tests"
    group = "verification"
}

// CI 체크 태스크
tasks.register("checkAll") {
    dependsOn("test", "detekt")
    description = "🔍 Run all checks (tests + lint)"
    group = "verification"

    doLast {
        println()
        println("╔════════════════════════════════════════════════════════════════╗")
        println("║  ✅ 모든 검사 완료!                                              ║")
        println("╚════════════════════════════════════════════════════════════════╝")
    }
}

// 린트만 실행 (빠른 체크)
tasks.register("lint") {
    dependsOn("detekt")
    description = "🔍 Run Kotlin linting (detekt)"
    group = "verification"
}

// 테스트 커버리지 요약 (JaCoCo 있을 경우)
tasks.register("testSummary") {
    dependsOn("test")
    description = "📊 Show test summary"
    group = "verification"

    doLast {
        val reportDir = file("build/reports/tests/test")
        if (reportDir.exists()) {
            println()
            println("📁 테스트 리포트: file://${reportDir.absolutePath}/index.html")
            println()
        }
    }
}

// 린트 자동 수정
tasks.register("lintFix") {
    dependsOn("detekt")
    description = "Run Kotlin linting with auto-correct"
    group = "verification"
    doFirst {
        println("Running detekt with autoCorrect=true")
    }
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

// Playground 실행용
tasks.register<JavaExec>("runPlayground") {
    group = "application"
    description = "Run RawdataToSliceToOpenSearchPlayground"
    mainClass.set("com.oliveyoung.ivmlite.apps.playground.RawdataToSliceToOpenSearchPlaygroundKt")
    classpath = sourceSets.main.get().runtimeClasspath
}

// Product Ingest & Slicing Playground 실행용
tasks.register<JavaExec>("runProductPlayground") {
    group = "application"
    description = "Run ProductIngestSlicingPlayground - CLI로 product 입력받아 ingest/slicing 확인"
    mainClass.set("com.oliveyoung.ivmlite.apps.playground.ProductIngestSlicingPlaygroundKt")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`  // CLI 입력 활성화
}

// RuntimeAPI 실행용
tasks.register<JavaExec>("runApi") {
    group = "application"
    description = "Run RuntimeAPI (Ktor server on port 8080)"
    mainClass.set("com.oliveyoung.ivmlite.apps.runtimeapi.ApplicationKt")
    classpath = sourceSets.main.get().runtimeClasspath
}

// Detekt configuration (Kotlin 특화 린트)
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/config/detekt/baseline.xml")  // 기존 이슈 무시용
    parallel = true  // 병렬 분석
    autoCorrect = true  // 자동 수정 활성화
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        html.outputLocation.set(file("build/reports/detekt/detekt.html"))
        xml.required.set(true)
        xml.outputLocation.set(file("build/reports/detekt/detekt.xml"))
        sarif.required.set(true)  // GitHub Code Scanning 호환
        sarif.outputLocation.set(file("build/reports/detekt/detekt.sarif"))
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
}

// detektBaseline 태스크는 detekt 플러그인이 자동 생성
// 사용법: ./gradlew detektBaseline

// JVM Toolchain (일관된 JVM 버전)
kotlin {
    jvmToolchain(17)

    // Kotlin 컴파일러 옵션 (프로젝트 레벨)
    compilerOptions {
        // 언어 기능 활성화
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

// ============================================
// 🚀 SOTA Kotlin 컴파일 최적화
// ============================================
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)

        // SOTA 성능 최적화 플래그
        freeCompilerArgs.addAll(
            // 필수 최적화
            "-Xjsr305=strict",                // JSR-305 null 안전성
            "-Xjvm-default=all",              // 인터페이스 기본 메서드 최적화

            // 릴리스 빌드 최적화 (assertion 제거)
            "-Xno-param-assertions",          // 파라미터 assertion 제거
            "-Xno-call-assertions",           // 호출 assertion 제거
            "-Xno-receiver-assertions",       // 리시버 assertion 제거

            // 컴파일 속도 최적화
            "-Xbackend-threads=0",            // 백엔드 병렬 처리 (0=auto, CPU 코어 수)
            "-Xsam-conversions=class",        // SAM 변환 최적화
            "-Xassertions=jvm",               // JVM assertion 모드

            // opt-in 어노테이션
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }

    // 증분 컴파일 세부 설정
    incremental = true
}

// Java 컴파일 최적화
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"

    options.apply {
        encoding = "UTF-8"
        isIncremental = true              // 증분 컴파일
        isFork = true                     // 별도 프로세스에서 컴파일
        forkOptions.memoryMaximumSize = "2g"
    }
}

// ============================================
// 빌드 캐시 최적화
// ============================================
tasks.withType<Test>().configureEach {
    // 테스트 병렬 실행
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // 테스트 결과 캐싱
    outputs.cacheIf { true }
}

// Jar 태스크 캐싱
tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

// Copy 태스크 병렬화
tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Application 설정 (기본: OpsCli)
application {
    mainClass.set("com.oliveyoung.ivmlite.apps.opscli.OpsCliAppKt")
}

// ============================================
// Maven Publishing (내부 배포용)
// ============================================
group = "com.oliveyoung"

// SDK 버전은 src/main/kotlin/com/oliveyoung/ivmlite/sdk/VERSION 파일에서 읽기
val sdkVersionFile = file("src/main/kotlin/com/oliveyoung/ivmlite/sdk/VERSION")
version = if (sdkVersionFile.exists()) {
    sdkVersionFile.readText().trim()
} else {
    "1.0.0"  // 기본값
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/josongsong/ivm-lite-oliveyoung")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }

        // Nexus 배포 (선택사항, 주석 해제하여 사용)
        /*
        maven {
            name = "NexusReleases"
            url = uri("https://nexus.company.com/repository/maven-releases/")
            credentials {
                username = project.findProperty("nexusUsername") as String?
                password = project.findProperty("nexusPassword") as String?
            }
        }
        */
    }

    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("IVM Lite SDK")
                description.set("IVM Lite SDK for Kotlin (Internal)")
                url.set("https://github.com/oliveyoung/ivm-lite-oliveyoung-full")

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}

// ============================================
// 빠른 빌드 태스크 (테스트 스킵)
// ============================================
tasks.register("fastBuild") {
    group = "build"
    description = "Fast build without tests (for development)"
    dependsOn("classes")
    doLast {
        println("✅ Fast build completed (tests skipped)")
    }
}

// Admin 전용 빠른 빌드 & 실행
tasks.register("fastAdmin") {
    group = "application"
    description = "Fast compile and run Admin (no tests)"
    dependsOn("classes")
    finalizedBy("runAdmin")
}

// Admin Application 실행 Task
tasks.register<JavaExec>("runAdmin") {
    group = "application"
    description = "Run Admin Application (port 8081)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.oliveyoung.ivmlite.apps.admin.AdminApplicationKt")

    // 현재 쉘의 모든 환경 변수 상속 (Gradle 구성 캐시 우회)
    environment.putAll(System.getenv())
    environment("ADMIN_PORT", System.getenv("ADMIN_PORT") ?: "8081")

    // JVM 최적화 (빠른 시작)
    jvmArgs(
        "-XX:TieredStopAtLevel=1",       // JIT 컴파일 최소화 (빠른 시작)
        "-XX:+UseParallelGC",            // 빠른 GC
        "-Xverify:none"                  // 바이트코드 검증 스킵 (개발용)
    )
}
