plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    // Apply the Versions plugin to allow checking for dependency updates.
    alias(libs.plugins.versions)
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Using version catalog aliases
    implementation(libs.kotlin.stdlib)

    // Test dependencies with consistent framework
    testImplementation(libs.kotlin.test.junit5)   // Kotlin test library with JUnit 5 support
    testImplementation(libs.junit.jupiter.engine) // JUnit 5 test engine
    testImplementation(libs.junit.jupiter.params) // JUnit 5 parameterized tests and CSV support
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

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // Define the main class for the application.
    mainClass = "org.example.AppKt"
}

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

// Add compiler options
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}
