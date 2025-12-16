# Kotlin Scratch (with SpringBoot)

A Kotlin playground repository for exploring various algorithms, data structures, and programming challenges. The main branch contains the core tech stack, while feature branches contain specific implementations and experiments.

## Branch Structure

- **SPRINGB**: Spring Boot baseline — branch off this for Kotlin/SpringBoot APIs and background services.
- **main**: Core tech stack and baseline project structure
- **Feature branches**: Specific implementations and challenges (see [Project Branches](#-project-branches) below)

## 🛠️ Technology Stack

- **Kotlin**: 2.1.0 (see note below)
- **Java**: 21 (LTS)
- **Gradle**: 9.x (wrapper)
- **Spring Boot**: 3.5.8
- **Web**: Spring WebFlux (reactive)
- **Data**: Spring Data R2DBC; PostgreSQL via `r2dbc-postgresql` with pooling (`r2dbc-pool`)
- **JSON**: Jackson Module Kotlin
- **Testing**: `spring-boot-starter-test`, Reactor Test, `kotlin.test`
- **Build Script**: Kotlin DSL (`build.gradle.kts`)
- **Architecture**: Multi-module with Onion Architecture principles

> **Note on Kotlin 2.1.0**: This project uses Kotlin 2.1.0 instead of 2.2.x due to a known incompatibility between Kotlin 2.2.21 and Gradle 8.14/9.x when using multi-module projects with inter-module dependencies. The issue manifests as a `ClasspathEntrySnapshotter$Settings` error during incremental compilation of modules with `project()` dependencies. Single-module projects work fine with Kotlin 2.2.21, but multi-module setups require Kotlin 2.1.x until this is resolved in a future Kotlin or Gradle release.

## 🏗️ How This Project Was Initialized

This project was created using Gradle's interactive `init` command with the following selections:

1. **Build Type**: Application
2. **Language**: Kotlin
3. **Java Version**: 21
4. **Project Name**: my-app
5. **Structure**: Single application project
6. **Build Script DSL**: Kotlin
7. **Test Framework**: kotlin.test
8. **New APIs**: Enabled (for latest features)

### Setup Commands Used:

```bash
# Update to latest Gradle
sdk install gradle 9.0.0

# Update to latest Kotlin (while keeping 2.1.0 for work projects)
sdk install kotlin 2.2.0
sdk use kotlin 2.2.0

# Initialize the project
gradle init
```

## 📁 Project Structure

### Multi-Module Architecture

The project follows **Onion Architecture** principles with proper Inversion of Control (IoC):

```
my-app/
├── app/
│   ├── core/                    # Core utilities (no Spring deps)
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/
│   │       └── org/example/core/
│   │           └── Result.kt    # Functional result type
│   │
│   ├── domn/                    # Domain layer (business logic)
│   │   ├── build.gradle.kts     # Depends on: core
│   │   └── src/main/kotlin/
│   │       └── org/example/domn/
│   │           └── Greeting.kt  # Domain models
│   │
│   ├── intg/                    # Integration layer (infrastructure)
│   │   ├── build.gradle.kts     # Depends on: domn, core
│   │   └── src/main/kotlin/
│   │       └── org/example/intg/
│   │           └── R2dbcConfig.kt  # R2DBC setup
│   │
│   ├── http/                    # HTTP/WebFlux layer (API)
│   │   ├── build.gradle.kts     # Depends on: intg, domn, core
│   │   └── src/
│   │       ├── main/kotlin/
│   │       │   └── org/example/http/
│   │       │       ├── HttpApplication.kt
│   │       │       └── HelloController.kt
│   │       └── main/resources/
│   │           └── application.yml
│   │
│   └── wrkr/                    # Worker layer (background services)
│       ├── build.gradle.kts     # Depends on: intg, domn, core
│       └── src/
│           ├── main/kotlin/
│           │   └── org/example/wrkr/
│           │       ├── WorkerApplication.kt
│           │       └── GreetingTask.kt  # Scheduled tasks
│           └── main/resources/
│               └── application.yml
│
├── build.gradle.kts             # Root build with plugin management
├── settings.gradle.kts          # Multi-module settings
├── gradle.properties            # Gradle properties
├── gradlew                      # Gradle wrapper (Unix)
├── gradlew.bat                  # Gradle wrapper (Windows)
└── README.md                    # This file
```

### Dependency Direction (IoC)

Upper layers can access any lower layer directly (not strict layering):

```
http/wrkr → intg, domn, core
intg     → domn, core
domn     → core
core     → (no dependencies)
```

**Example**: The `http` layer can use utilities from `core` directly without going through `domn`.

### Module Responsibilities

- **app:core** - Pure utilities with zero Spring dependencies (Result types, extensions, helpers)
- **app:domn** - Domain models, value objects, CQRS commands/queries, pure business logic
- **app:intg** - Infrastructure (R2DBC repos, external service clients, PostgreSQL-specific code)
- **app:http** - WebFlux controllers, REST request/response models, HTTP API entrypoint
- **app:wrkr** - Scheduled tasks, background processors, worker entrypoint (no web server)

## 🚀 Getting Started

### Prerequisites

- **Java 21** (LTS)
- **No need to install Gradle** - the project includes Gradle Wrapper

### Building the Project

```bash
# Clean and build all modules
./gradlew clean build

# Build specific module
./gradlew :app:http:build
```

### Running the HTTP API (app:http)

Runs a reactive WebFlux API server:

```bash
./gradlew :app:http:bootRun
```

Test the endpoint:
```bash
curl http://localhost:8080/hello
# Response: {"message":"Hello, Spring WebFlux!","status":"ok"}

# With query parameter
curl "http://localhost:8080/hello?name=Kotlin"
# Response: {"message":"Hello, Kotlin!","status":"ok"}
```

### Running the Worker (app:wrkr)

Runs background services without a web server:

```bash
./gradlew :app:wrkr:bootRun
```

The worker logs scheduled tasks every 30 seconds:
```
Scheduled task: Hello, Worker! [ok]
```

### Database Configuration

Configure PostgreSQL via environment variables:

```bash
export R2DBC_URL="r2dbc:pool:postgresql://localhost:5432/mydb"
export DB_USERNAME="myuser"
export DB_PASSWORD="mypass"
```

Defaults (if not set):
- `R2DBC_URL`: `r2dbc:pool:postgresql://localhost:5432/app`
- `DB_USERNAME`: `postgres`
- `DB_PASSWORD`: `postgres`

### Running Tests

```bash
# Run all tests across all modules
./gradlew test

# Run tests for specific module
./gradlew :app:http:test
./gradlew :app:wrkr:test
```

### Other Useful Commands

```bash
# View project structure
./gradlew projects

# View dependencies for a module
./gradlew :app:http:dependencies

# View available tasks
./gradlew tasks

# Format code with Spotless
./gradlew spotlessApply

# Generate executable JARs
./gradlew :app:http:bootJar
./gradlew :app:wrkr:bootJar
```

## 🔧 Development Setup

### IntelliJ IDEA

1. **Open IntelliJ IDEA**
2. **File** → **Open**
3. **Select the project directory** (`/path/to/your/project`)
4. **IntelliJ will automatically detect** it as a Gradle project and import it
5. **Wait for Gradle sync** to complete

The IDE will automatically configure:
- Kotlin plugin
- Gradle integration
- Java 21 SDK
- Project structure

### VS Code

1. **Install extensions**:
   - Kotlin Language
   - Gradle for Java
2. **Open the project directory**
3. **Run Gradle sync** when prompted

## ⚡ Performance Features

Configured in `gradle.properties`:

- **Parallel Execution**: Runs tasks in parallel when possible (`org.gradle.parallel=true`)
- **Configuration Cache**: Currently disabled due to Kotlin 2.1.0 compatibility with Gradle 9.x
- **Build Cache**: Currently disabled

## 🧪 Testing

The project uses:
- **Spring Boot Test** for integration tests
- **kotlin.test** with JUnit 5 for unit tests
- **WebTestClient** for reactive HTTP endpoint testing
- **Reactor Test** for reactive stream testing

### Test Structure

Tests are organized by module:
```
app/
├── core/src/test/kotlin/      # Core utility tests
├── domn/src/test/kotlin/      # Domain model tests
├── intg/src/test/kotlin/      # Integration tests
├── http/src/test/kotlin/      # HTTP endpoint tests
└── wrkr/src/test/kotlin/      # Worker task tests
```

### Example Tests

HTTP endpoint test (app/http):
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpApplicationTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `hello endpoint returns greeting`() {
        webTestClient.get().uri("/hello")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Hello, Spring WebFlux!")
            .jsonPath("$.status").isEqualTo("ok")
    }
}
```

Domain model test (app/domn):
```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.example.domn.Greeting

class GreetingTest {
    @Test
    fun `greeting factory creates valid greeting`() {
        val greeting = Greeting.hello("Kotlin")
        assertEquals("Hello, Kotlin!", greeting.message)
        assertEquals("ok", greeting.status)
    }
}
```

## 📦 Dependencies

Dependencies are managed via Gradle version catalog (`gradle/libs.versions.toml`) and Spring Boot BOM.

### Module Dependencies

- **app:core**: Kotlin stdlib only (no external deps)
- **app:domn**: core module
- **app:intg**: domn, core, Spring Data R2DBC, R2DBC PostgreSQL, R2DBC Pool
- **app:http**: intg, domn, core, Spring Boot Starter WebFlux, Jackson Kotlin
- **app:wrkr**: intg, domn, core, Spring Boot Starter (no WebFlux)

### Adding New Dependencies

1. Add version to `gradle/libs.versions.toml`:
```toml
[versions]
my-lib = "1.0.0"

[libraries]
my-library = { module = "com.example:my-library", version.ref = "my-lib" }
```

2. Reference in module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.my.library)
}
```

### Spring Boot Managed Dependencies

Spring Boot versions are managed by the BOM - no version needed:
```kotlin
implementation(libs.spring.boot.starter.webflux)  // Version from Spring Boot 3.5.8
```

## 🔄 Version Management

If you need to switch Kotlin versions (useful for work projects):

```bash
# Switch to Kotlin 2.1.0 (work projects)
sdk use kotlin 2.1.0

# Switch back to Kotlin 2.2.0 (personal projects)  
sdk use kotlin 2.2.0

# Check available versions
sdk list kotlin
```

## 🌱 Project Branches

This repository uses a branch-based approach for different projects and experiments:

### 🧩 SUDOKO

**Branch**: `SUDOKO`  
**Status**: ✅ Complete

A full-featured Sudoku solver implementation with CLI interface.

**Features:**
- Backtracking algorithm implementation
- Command-line solver supporting single and multiple puzzles
- Statistics tracking (steps, backtracks, solve time)
- Immutable puzzle solving (original puzzle preserved)
- Comprehensive test coverage
- Beautiful console output with puzzle visualization

**Key Components:**
- `Puzzle.kt` - Main puzzle representation (data class)
- `BacktrackingSolver.kt` - Backtracking algorithm implementation
- `App.kt` - CLI interface for solving puzzles
- Outcome pattern with `Success`/`Failure` types
- Stats tracking with performance metrics

**Usage:**
```bash
git checkout SUDOKO
./gradlew run --args="530070000600195000098000060800060003400803001700020006060000280000419005000080079"
```

**Performance:**
- Easy puzzles: ~8,000 steps, ~4,000 backtracks, <30ms
- Medium puzzles: ~15,000 steps, ~7,000 backtracks, <50ms
- Hard puzzles: ~50,000+ steps, ~25,000+ backtracks, <200ms

See `app/README.md` on the SUDOKO branch for detailed documentation.

---

### Future Branches

Planned explorations:
- Graph algorithms (DFS, BFS, Dijkstra)
- Dynamic programming challenges
- Data structure implementations
- Design pattern examples

## 📚 Learn More

- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Gradle User Guide](https://docs.gradle.org/current/userguide/)
- [Building Kotlin Applications with Gradle](https://docs.gradle.org/9.0.0/samples/sample_building_kotlin_applications.html)
- [kotlin.test Documentation](https://kotlinlang.org/docs/kotlin-test.html)

## 🤝 Contributing

1. **Fork the project**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit your changes** (`git commit -m 'Add some amazing feature'`)
4. **Push to the branch** (`git push origin feature/amazing-feature`)
5. **Open a Pull Request**

---

**Happy Coding!** 🎉
