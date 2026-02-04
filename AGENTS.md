# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Build & Test Commands

```bash
# Build and run tests
./gradlew :app:build

# Run all tests
./gradlew :app:test

# Run a single test class
./gradlew :app:test --tests "*SpscIntegrationTests"

# Run a specific test method
./gradlew :app:test --tests "*SpscIntegrationTests.should process events*"

# Run with Spring Boot (worker mode, default)
./gradlew :app:bootRun

# Run with Spring Boot (API mode with WebFlux)
./gradlew :app:bootRun --args='--spring.profiles.active=api'

# Format code (Spotless with ktlint)
./gradlew :app:spotlessApply

# Check formatting
./gradlew :app:spotlessCheck

# Check for dependency updates
./gradlew :app:dependencyUpdates
```

## Pre-commit Hook

Enable the formatting hook after cloning:
```bash
git config core.hooksPath .githooks
```

The hook runs `spotlessApply` then `spotlessCheck` on commit.

## Spring Profiles

- **worker** (default): Background processing, no web server (`spring.main.web-application-type=none`)
- **api**: Reactive web with WebFlux

## Project Architecture

```
app/src/main/kotlin/org/example/
├── App.kt                    # Spring Boot entry point
├── common/                   # Shared utilities
│   ├── extensions/           # Kotlin extension functions (JSON)
│   └── serializers/          # Kotlinx serializers
├── events/                   # Event sourcing core
│   ├── Event.kt              # Generic Event<T> type
│   ├── OrderEvents.kt        # Domain events (OrderPlaced, OrderCancelled, etc.)
│   └── storage/              # Event persistence
│       ├── EventStream.kt    # Streaming interface with bookmark support
│       ├── Bookmark.kt       # Consumer progress checkpoint
│       ├── StreamedEvent.kt  # Event + position wrapper
│       ├── InMemoryEventStream.kt
│       └── CSVEventStream.kt
├── spsc/                     # Single Producer, Single Consumer pattern
│   ├── SpscCoordinator.kt    # Orchestrates producer/consumer on separate threads
│   ├── SpscWorkerService.kt  # Spring-managed worker lifecycle
│   ├── SpscQueue.kt          # Bounded thread-safe queue
│   ├── EventProducer.kt      # Streams events with position tracking
│   ├── EventConsumer.kt      # Functional interface for batch processing
│   ├── SpscConfig.kt         # Runtime configuration
│   └── SpscProperties.kt     # Spring @ConfigurationProperties
└── retry/                    # Resilience patterns
    ├── ResilientExecutor.kt  # Retry with exponential backoff
    ├── DeadLetterQueue.kt    # Failed event storage
    └── RetryStrategy.kt      # Configurable retry policies
```

## Key Patterns

### Event Streaming with Position Tracking
Events are wrapped in `StreamedEvent` containing the event and its `StreamOffset(position)`. Consumers receive batches and calculate the next bookmark as `batch.maxOf { it.offset.position } + 1L`.

### SPSC Coordination
`SpscCoordinator` spawns producer and consumer on separate threads, connected by a bounded queue. The producer reads from `EventStream`, the consumer processes batches and advances bookmarks only on success (failure = automatic replay).

### Configuration
SPSC settings are in `application.yml` under `app.spsc.*` and can be overridden via environment variables (e.g., `APP_SPSC_CSV_PATH`, `APP_SPSC_PRODUCER_BATCH_SIZE`).

## Dependency Management

Dependencies are managed in `gradle/libs.versions.toml`. Spring Boot BOM manages transitive versions for Spring-related dependencies.

## Kotlin Conventions

- Uses Kotlin coroutines and Flows for async/streaming
- Uses `kotlinx.serialization` for JSON
- Uses data classes for domain objects
- Tests use JUnit 5 with `kotlin.test` assertions
