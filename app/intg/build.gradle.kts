plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.dependency.management)
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.8")
    }
}

dependencies {
    implementation(project(":app:domn"))
    implementation(project(":app:core"))

    implementation(libs.kotlin.stdlib)
    implementation(kotlin("reflect"))

    // Spring Data R2DBC for reactive database access
    implementation(libs.spring.boot.starter.data.r2dbc)

    // R2DBC driver and pooling
    runtimeOnly(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
