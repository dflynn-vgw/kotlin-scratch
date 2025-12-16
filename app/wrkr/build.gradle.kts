plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    // Module dependencies - can access all lower layers
    implementation(project(":app:intg"))
    implementation(project(":app:domn"))
    implementation(project(":app:core"))

    implementation(libs.kotlin.stdlib)
    implementation(kotlin("reflect"))

    // Spring Boot starter (NO WebFlux - this is a worker/background service)
    implementation(libs.spring.boot.starter)

    // JSON support for Kotlin
    implementation(libs.jackson.module.kotlin)

    // Tests
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

springBoot {
    mainClass.set("org.example.wrkr.WorkerApplicationKt")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    mainClass.set("org.example.wrkr.WorkerApplicationKt")
}

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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
