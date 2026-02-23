plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Java 17 (루트 프로젝트와 동일)
kotlin {
    jvmToolchain(17)
}

dependencies {
    // Serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Arrow (Result/Either)
    api("io.arrow-kt:arrow-core:1.2.1")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
}

tasks.test {
    useJUnitPlatform()
}
