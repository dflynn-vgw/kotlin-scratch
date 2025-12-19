plugins {
    // Kotlin + Spring
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)

    // Spring Boot + Dependency Management
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)

    // Tooling
    alias(libs.plugins.versions)
    alias(libs.plugins.spotless)
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(kotlin("reflect"))

    // Spring Boot starters
    implementation(libs.spring.boot.starter) // allows non-web apps
    implementation(libs.spring.boot.starter.webflux) // reactive web (API)
    implementation(libs.spring.boot.starter.data.r2dbc) // reactive Postgres access

    // R2DBC driver + pool
    runtimeOnly(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)

    // JSON Kotlin support
    implementation(libs.jackson.module.kotlin)

    // Tests
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.reactor.test)
    testImplementation(libs.kotlin.test.junit5)
}

testing {
    suites {
        val test by
            @Suppress("UnstableApiUsage")
            getting(JvmTestSuite::class) {
                @Suppress("UnstableApiUsage")
                useJUnitJupiter()
            }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

springBoot {
    mainClass.set("org.example.AppKt")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    mainClass.set("org.example.AppKt")
}

// Spotless configuration
spotless {
    kotlin {
        target("**/*.kt")
        ktlint("1.8.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
    }
}

tasks.test {
    useJUnitPlatform()
}

// Kotlin compiler options
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
