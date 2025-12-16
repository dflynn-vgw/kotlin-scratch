# Dependency Management

This project uses two complementary mechanisms for dependency management:
1. **Gradle version catalog** (`gradle/libs.versions.toml`)
2. **Spring Boot BOM** (Bill of Materials)

## Gradle Version Catalog

The version catalog provides centralized dependency management across all subprojects. It's defined in `gradle/libs.versions.toml` and contains three main sections:

### [versions]
Defines version variables that can be referenced throughout the catalog:
```toml
kotlin = "2.1.0"
spring-boot = "3.5.8"
junit = "5.14.1"
```

### [plugins]
Defines Gradle plugins with their versions:
```toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
```

### [libraries]
Defines library dependencies. These can either:
- Specify explicit versions (e.g., Kotlin, JUnit)
- Omit versions when managed by Spring Boot BOM (e.g., Spring Boot starters)

**Example:**
```toml
# Explicitly versioned
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

# Managed by Spring Boot BOM (no version)
spring-boot-starter-webflux = { module = "org.springframework.boot:spring-boot-starter-webflux" }
```

## Spring Boot BOM (Bill of Materials)

A **BOM** is a curated list of compatible dependency versions maintained by the Spring team. When you apply the Spring Boot dependency management plugin, it automatically imports the Spring Boot BOM.

### How it Works

The `io.spring.dependency-management` plugin (applied in modules like `app/http`) automatically imports the Spring Boot BOM:

```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}
```

This provides:
- **Transitive version management**: Dependencies managed by the BOM don't need explicit versions
- **Compatibility guarantees**: All Spring ecosystem libraries work together
- **Reduced version conflicts**: Spring team ensures tested compatibility

### What the BOM Manages

The Spring Boot BOM manages versions for:
- All `org.springframework.boot:*` dependencies
- Spring Framework (`org.springframework:*`)
- Related libraries like Jackson, Reactor, R2DBC, logging frameworks, etc.

In our version catalog, these are marked with comments:
```toml
# Spring Boot managed libraries (versions come from Spring Boot BOM)
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter" }
reactor-test = { module = "io.projectreactor:reactor-test" }
r2dbc-postgresql = { module = "org.postgresql:r2dbc-postgresql" }
```

## Using Dependencies in build.gradle.kts

### With Version Catalog Aliases

```kotlin
dependencies {
    // Explicit version from catalog
    implementation(libs.kotlin.stdlib)
    
    // Managed by BOM (no version in catalog)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.jackson.module.kotlin)
}
```

### Module Organization

Our project uses a layered architecture:
- **core**: Pure Kotlin, no Spring dependencies
- **domn**: Domain logic, minimal dependencies
- **intg**: Integration layer (repositories, external APIs)
- **http**: HTTP/REST layer with Spring Boot WebFlux
- **wrkr**: Worker/background processing

Only modules that need Spring Boot (like `http` and `wrkr`) apply the Spring Boot plugins and use BOM-managed dependencies.

## Benefits of This Approach

1. **Single source of truth**: Version catalog centralizes all version definitions
2. **Type-safe accessors**: `libs.spring.boot.starter.webflux` provides IDE autocomplete
3. **Tested compatibility**: Spring Boot BOM ensures Spring ecosystem libraries work together
4. **Easy upgrades**: Update Spring Boot version in one place, all managed dependencies upgrade
5. **Reduced boilerplate**: No need to specify versions for dozens of Spring-related libraries

## Upgrading Dependencies

### Upgrade Spring Boot (and all managed dependencies)
```toml
# In gradle/libs.versions.toml
spring-boot = "3.5.8"  # Update this version
```

### Upgrade Independent Libraries
```toml
# In gradle/libs.versions.toml
kotlin = "2.1.0"  # Update this version
junit = "5.14.1"  # Update this version
```

### Check for Updates
```bash
./gradlew dependencyUpdates
```

This uses the `com.github.ben-manes.versions` plugin to report available updates.
