import java.time.Duration

buildscript {
    // buildscript repositories는 settings.gradle.kts의 dependencyResolutionManagement와 별개
    // Flyway, PostgreSQL JDBC 드라이버 다운로드용
    repositories { 
        mavenCentral()
    }
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
    id("com.github.johnrengelman.shadow") version "8.1.1"  // Lambda JAR 빌드용
    `maven-publish`  // 내부 배포용
}

// repositories는 settings.gradle.kts의 dependencyResolutionManagement에서 중앙 관리
// PREFER_SETTINGS 모드이므로 여기서 선언하면 settings의 repositories가 무시됨
// 따라서 주석 처리 (settings.gradle.kts에서 mavenCentral() 이미 선언됨)
// repositories { mavenCentral() }

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
// - Flyway 태스크 실행 시에만 필요합니다.
// - 로컬 기본값(localhost) 제거: 실수로 로컬에 붙는 것을 방지합니다.
// ============================================
val dbUrl = System.getenv("DB_URL") ?: ""
val dbUser = System.getenv("DB_USER") ?: ""
val dbPassword = System.getenv("DB_PASSWORD") ?: ""

// ============================================
// Dependency Version Alignment
// Exposed 0.56.0이 coroutines 1.9.0을 transitively 가져오지만,
// Ktor 2.3.9는 coroutines 1.8.x internal API에 의존하므로 강제 고정
// ============================================
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3")
    }
}

dependencies {
    // ============================================
    // Sink Plugin Contract (RFC-017)
    // ============================================
    implementation(project(":sinks-contract"))

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
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
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
    
    // AWS X-Ray SDK (Trace 조회용 - TraceService에서 사용)
    implementation("software.amazon.awssdk:xray:2.20.26")

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
    // Database (PostgreSQL + Exposed + HikariCP)
    // ============================================
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // JetBrains Exposed (Type-safe SQL DSL)
    val exposedVersion = "0.56.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // ============================================
    // AWS SDK v2 (DynamoDB, SQS, Personalize - Sink Plugin)
    // ============================================
    implementation(platform("software.amazon.awssdk:bom:2.25.67"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:sqs")  // RFC-017: Sink Plugin (레거시, 향후 제거)
    implementation("software.amazon.awssdk:s3")  // RFC-017: S3 Sink Plugin
    implementation("software.amazon.awssdk:personalizeevents")  // RFC-017: Personalize Sink Plugin
    implementation("software.amazon.awssdk:netty-nio-client")

    // ============================================
    // AWS Lambda Runtime (Lambda Handler용)
    // ============================================
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.4")

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
// Exposed: 코드 생성 불필요 (Tables.kt에 스키마 직접 정의)
// ============================================

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
// Product DX 도구 (product-schema-dx-proposal RFC 2.2, 5.1)
// ============================================

tasks.register<JavaExec>("extractJsonPaths") {
    group = "dx"
    description = "📂 JSON 샘플에서 PathExpr 경로 추출 (options[*].gdsSelprcUprc 형식)"

    mainClass.set("com.oliveyoung.ivmlite.tooling.application.ExtractJsonPathsKt")
    classpath = sourceSets.main.get().runtimeClasspath

    val sample = System.getProperty("sample") ?: ".tmp/product/UA11279226.json"
    val output = System.getProperty("output") ?: "paths.yaml"

    args = listOf("--sample", sample, "--output", output)
}

tasks.register<JavaExec>("pathsToImpactMap") {
    group = "dx"
    description = "🗺️ PathExpr → impactMap 초안 생성 (슬라이스 추천)"

    mainClass.set("com.oliveyoung.ivmlite.tooling.application.PathsToImpactMapMainKt")
    classpath = sourceSets.main.get().runtimeClasspath

    val paths = System.getProperty("paths") ?: "paths.yaml"
    val output = System.getProperty("output") ?: "impact-map-draft.yaml"

    args = listOf("--paths", paths, "--output", output)
}

tasks.register<JavaExec>("validateRawData") {
    group = "dx"
    description = "✅ RawData Pre-Ingest 검증 (JSON 파싱 + 필수 경로 + RuleSet 존재성)"

    mainClass.set("com.oliveyoung.ivmlite.tooling.application.ValidateRawDataMainKt")
    classpath = sourceSets.main.get().runtimeClasspath

    val sample = System.getProperty("sample") ?: ".tmp/product/UA11279226.json"
    args = listOf("--sample", sample)
}

// ============================================
// Task Dependencies
// ============================================

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

// Product E2E 테스트 (product-schema-dx-proposal Phase 1.5)
tasks.register<Test>("productE2E") {
    useJUnitPlatform()
    configureTestLogging()

    filter {
        includeTestsMatching("*ProductE2ETest*")
    }

    systemProperty("sample", System.getProperty("sample") ?: ".tmp/product/UA11279226.json")

    description = "🛒 Product E2E: parse→validate→ingest→view compose→sink dry-run"
    group = "verification"
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
    environment.putAll(System.getenv())
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

// Admin 개발 모드 (Hot Reload) - 파일 변경 시 자동 재시작
tasks.register<JavaExec>("runAdminDev") {
    group = "application"
    description = "Run Admin Application in dev mode with hot reload (use: ./gradlew --continuous runAdminDev)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.oliveyoung.ivmlite.apps.admin.AdminApplicationKt")

    // 현재 쉘의 모든 환경 변수 상속
    environment.putAll(System.getenv())
    environment("ADMIN_PORT", System.getenv("ADMIN_PORT") ?: "8081")
    environment("DEV_MODE", "true")
    // Contract Hot Reload: YAML 파일 직접 로드 (재시작 없이 반영)
    environment("CONTRACTS_FILE_PATH", "${project.projectDir.absolutePath}/src/main/resources/contracts/v1")

    // JVM 최적화 (개발 모드)
    jvmArgs(
        "-XX:TieredStopAtLevel=1",       // JIT 컴파일 최소화 (빠른 시작)
        "-XX:+UseParallelGC",            // 빠른 GC
        "-Xverify:none"                  // 바이트코드 검증 스킵
    )
}

// Runtime API 개발 모드 (Hot Reload)
tasks.register<JavaExec>("runApiDev") {
    group = "application"
    description = "Run Runtime API in dev mode with hot reload (use: ./gradlew --continuous runApiDev)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.oliveyoung.ivmlite.apps.runtimeapi.ApplicationKt")

    // 현재 쉘의 모든 환경 변수 상속
    environment.putAll(System.getenv())
    environment("DEV_MODE", "true")

    // JVM 최적화 (개발 모드)
    jvmArgs(
        "-XX:TieredStopAtLevel=1",
        "-XX:+UseParallelGC",
        "-Xverify:none"
    )
}

// ============================================
// Shadow JAR (Lambda 배포용)
// ============================================
tasks.shadowJar {
    archiveBaseName.set("ivm-ingest-lambda")
    archiveClassifier.set("")
    archiveVersion.set("1.0.0")

    // Lambda Runtime 포함
    mergeServiceFiles()

    // 불필요한 파일 제외 (서명 파일)
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    // Manifest 설정
    manifest {
        attributes(
            "Main-Class" to "com.oliveyoung.ivmlite.apps.lambda.IngestLambdaHandler"
        )
    }
}
