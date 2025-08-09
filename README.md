# My Kotlin App

A basic Kotlin application project created with the latest Gradle and Kotlin versions, targeting the JVM.

## 📋 Project Overview

This is a simple Kotlin console application that demonstrates a basic project structure with modern tooling and best practices. The project uses Gradle for dependency management and build automation, with Kotlin DSL for build scripts.

## 🛠️ Technology Stack

- **Kotlin**: 2.2.0 (latest stable)
- **Gradle**: 9.0.0 (latest stable) 
- **Java**: 21 (LTS)
- **Testing**: kotlin.test framework
- **Build Script**: Kotlin DSL (build.gradle.kts)

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

```
my-app/
├── app/                          # Main application module
│   ├── build.gradle.kts         # Build configuration (Kotlin DSL)
│   └── src/
│       ├── main/kotlin/         # Your Kotlin source code
│       │   └── org/example/App.kt
│       ├── main/resources/      # Resources
│       ├── test/kotlin/         # Test code
│       │   └── org/example/AppTest.kt
│       └── test/resources/      # Test resources
├── settings.gradle.kts          # Project settings
├── gradle.properties           # Gradle properties
├── gradlew                     # Gradle wrapper (Unix)
├── gradlew.bat                 # Gradle wrapper (Windows)
└── README.md                   # This file
```

## 🚀 Getting Started

### Prerequisites

- **Java 17+** (JDK 21 recommended)
- **No need to install Gradle** - the project includes Gradle Wrapper

### Running the Application

```bash
# Run the main application
./gradlew run
```

Expected output:
```
Hello World!
```

### Running Tests

```bash
# Run all tests
./gradlew test
```

### Building the Project

```bash
# Clean and build the project
./gradlew clean build
```

### Other Useful Commands

```bash
# View project dependencies
./gradlew dependencies

# View available tasks
./gradlew tasks

# Generate distribution archives
./gradlew assembleDist
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

This project includes several performance optimizations enabled by default:

- **Configuration Cache**: Speeds up subsequent builds
- **Build Cache**: Reuses outputs from previous builds
- **Parallel Execution**: Runs tasks in parallel when possible

These are configured in `gradle.properties`:
```properties
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.caching=true
```

## 🧪 Testing

The project uses **kotlin.test** framework for unit testing. Tests are located in:
- `app/src/test/kotlin/org/example/AppTest.kt`

### Adding New Tests

Create new test files in the `app/src/test/kotlin/` directory following the same package structure as your source code.

Example test:
```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class MyTest {
    @Test 
    fun `should do something`() {
        // Test implementation
        assertEquals(expected = "Hello", actual = "Hello")
    }
}
```

## 📦 Dependencies

Current dependencies are managed in `app/build.gradle.kts`:

- **Google Guava**: Utility library (implementation)
- **Kotlin Test**: Testing framework (test scope)

### Adding New Dependencies

Add dependencies to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("group:artifact:version")
    testImplementation("group:test-artifact:version")
}
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
