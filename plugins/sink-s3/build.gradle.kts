plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "8.1.1"  // Fat JAR
}

group = "com.oliveyoung.ivmlite.plugins"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // :sinks-contract만 의존 (LOCK - RFC-017)
    implementation(project(":sinks-contract"))

    // AWS SDK
    implementation("software.amazon.awssdk:s3:2.20.0")
    implementation("software.amazon.awssdk:sqs:2.20.0")

    // Lambda 런타임
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")

    // Arrow (에러 처리)
    implementation("io.arrow-kt:arrow-core:1.2.1")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.1")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
}

// Lambda 배포용 Fat JAR 생성
tasks.shadowJar {
    archiveFileName.set("s3-sink-lambda.jar")
    // manifest main-class 제거 (Lambda handler만 SSOT)
}
