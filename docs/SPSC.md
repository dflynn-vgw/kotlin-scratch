# SPSC (Single Producer, Single Consumer) Event Streaming Architecture

## Overview

This document describes the event streaming and SPSC pattern implementation that extends the EventStore with continuous event consumption capabilities, position tracking, and fault-tolerant processing.

## Problem Statement

The original EventStore provided point-in-time read/save operations suitable for aggregate hydration. To support continuous event processing with position tracking and fault tolerance, we needed:

1. Streaming capability to read events from a position
2. Checkpoint management (bookmarks) for consumer progress
3. An event processing pipeline using the SPSC pattern
4. Backpressure handling and fault tolerance

## Architecture

### 1. StreamingEventStore Interface

Extends `EventStore` with streaming and bookmark capabilities:

```kotlin
interface StreamingEventStore : EventStore {
    fun stream(fromPosition: Long = 0): Flow<Event<Any>>
    suspend fun saveBookmark(name: String, position: Long)
    suspend fun getBookmark(name: String): Bookmark?
}
```

**Rationale**: Separates streaming concerns from point-in-time queries, enabling both CQRS/ES patterns (aggregate hydration) and event processor patterns (continuous consumption).

### 2. Bookmark Entity

```kotlin
data class Bookmark(
    val name: String,
    val position: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
```

- Consumer identifier (`name`)
- Last processed position (`position`)
- Timestamp for audit/debugging

Enables resumable consumption: failure means no bookmark advance → automatic replay on retry.

### 3. InMemoryStreamingEventStore Implementation

Full in-memory implementation extending `InMemoryEventStore`:

- Thread-safe event logging with `ConcurrentHashMap` for bookmarks
- Global event position tracking
- `stream(fromPosition)` returns Flow<Event<Any>>
- Bookmark persistence in same storage as events

**Thread Safety**: Uses synchronized blocks to maintain consistency between event log and streaming position.

### 4. SPSC Component Structure

#### Configuration

```kotlin
data class SpscConfig(
    val producerBatchSize: Int = 10,
    val consumerBatchSize: Int = 5,
    val maxQueueDepth: Int = 100,
    val bookmarkName: String,
)
```

Configurable throughput tuning at startup.

#### Producer

```kotlin
interface EventProducer {
    suspend fun produce(
        eventStore: StreamingEventStore,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<Event<Any>>
}
```

- Fetches events from StreamingEventStore starting at a position
- Returns Flow for composable, cancellable streaming
- Respects backpressure from bounded queue

#### Consumer

```kotlin
fun interface EventConsumer {
    suspend fun consume(events: List<Event<Any>>, bookmark: Bookmark)
}
```

- Receives batch of events plus current bookmark
- Bookmark passed for context/optional custom checkpoint logic
- Returns successfully only if all events processed
- Failure means bookmark NOT advanced (triggers replay)

#### Internal Queue

```kotlin
class SpscQueue<T>(private val maxCapacity: Int = 100)
```

- Thread-safe bounded queue using `LinkedBlockingQueue<T>`
- `put(item)` - blocking if queue full (producer backpressure)
- `poll(timeoutMs)` - non-blocking with timeout (consumer polling)
- Implements natural backpressure: producer pauses when queue full

#### Coordinator

```kotlin
class SpscCoordinator(
    private val producer: EventProducer,
    private val consumer: EventConsumer,
    private val eventStore: StreamingEventStore,
    private val config: SpscConfig,
)
```

Orchestrates the pipeline:

1. **Startup** (`start()`)
   - Spawns producer on thread pool
   - Spawns consumer on separate thread
   - Producer reads starting position from bookmark

2. **Producer Thread**
   - Gets starting position from stored bookmark
   - Streams events from that position
   - Puts events into bounded queue (blocks if full)
   - Stops when told to via `isRunning` flag

3. **Consumer Thread**
   - Polls events from queue in configurable batches
   - Calls consumer with batch + current bookmark
   - On success: advances bookmark to position + batch size
   - On failure: bookmark NOT advanced (no side effects)
   - Retries or logs error without breaking pipeline

4. **Lifecycle**
   - `stop()` - gracefully signals shutdown
   - `await(timeoutMs)` - blocks until both threads complete

## Usage Pattern

### Basic Event Processing

```kotlin
// Setup
val eventStore = InMemoryStreamingEventStore()
val config = SpscConfig(
    producerBatchSize = 10,
    consumerBatchSize = 5,
    maxQueueDepth = 100,
    bookmarkName = "my-processor",
)

// Define consumer logic
val consumer = EventConsumer { events, bookmark ->
    events.forEach { event ->
        println("Processing ${event.type} from position ${bookmark.position}")
        // Your business logic here
    }
}

// Run coordinator
val coordinator = SpscCoordinator(
    DefaultEventProducer(),
    consumer,
    eventStore,
    config,
)

coordinator.start()
// ... processing happens ...
coordinator.stop()
coordinator.await(timeoutMs = 30000)
```

### Resume from Checkpoint

```kotlin
// First run processes some events
coordinator.start()
Thread.sleep(5000)
coordinator.stop()
coordinator.await()

// Second run automatically resumes from saved bookmark
val coordinator2 = SpscCoordinator(
    DefaultEventProducer(),
    consumer,
    eventStore,
    config, // same bookmarkName
)

coordinator2.start() // Resumes from where it left off
```

### Fault Tolerance

```kotlin
val consumer = EventConsumer { events, _ ->
    try {
        processEvents(events)
        // Success: bookmark will advance
    } catch (e: Exception) {
        // Failure: bookmark NOT advanced
        // On next coordinator run, events replayed from same position
        throw e
    }
}
```

## Design Rationale

### Separation of Concerns

- **EventStore**: Point-in-time queries for aggregate hydration (CQRS/ES)
- **StreamingEventStore**: Continuous event flow for processors
- Allows different implementations/storage backends for each pattern

### Backpressure

- Bounded queue naturally throttles producer if consumer lags
- No memory explosion with slow consumer
- No artificial delays or complex flow control needed

### Fault Tolerance

- Bookmark only advances on successful consumption
- Failure → automatic replay on retry
- No lost events, no duplicates (exactly-once semantics with side effects)

### Thread-Based Parallelism

- Producer and consumer run on separate threads
- True parallelism without coroutine overhead
- Natural for CPU-bound or blocking operations in consumer

### Configurability

- Batch sizes tune throughput vs. latency
- Queue depth balances memory vs. responsiveness
- One config object for entire pipeline

## Implementation Files

| File | Purpose |
|------|---------|
| `StreamingEventStore.kt` | Interface + Bookmark entity |
| `InMemoryStreamingEventStore.kt` | In-memory implementation with threading |
| `SpscConfig.kt` | Configuration data class |
| `SpscQueue.kt` | Internal bounded queue wrapper |
| `EventProducer.kt` | Producer interface & default implementation |
| `EventConsumer.kt` | Consumer functional interface |
| `SpscCoordinator.kt` | Main orchestrator (145 lines) |
| `SpscCoordinatorTests.kt` | Integration test suite |
| `InMemoryStreamingEventStoreTests.kt` | Streaming functionality tests |

## Testing

Integration tests cover:

- Full pipeline: produce → queue → consume → bookmark
- Resume from bookmark on restart
- Consumer failure does not advance bookmark
- Queue respects depth and provides backpressure
- Batch sizing and processing

Run with:
```bash
./gradlew test --tests="SpscCoordinatorTests"
./gradlew test --tests="InMemoryStreamingEventStoreTests"
```

## Integration with CQRS/ES

### Event Sourcing (Aggregate Hydration)

```kotlin
val events = eventStore.read("order-123")
val aggregate = OrderAggregate()
events.forEach { aggregate.apply(it) }
// aggregate is now hydrated from its event history
```

### Event Processing (SPSC)

```kotlin
val coordinator = SpscCoordinator(
    DefaultEventProducer(),
    EventConsumer { events, _ ->
        events.forEach { event ->
            // Update read models
            // Send notifications
            // Trigger sagas
        }
    },
    eventStore,
    config,
)

coordinator.start()
// Runs continuously, processing events as they arrive
```

Both patterns coexist on the same EventStore, enabling full CQRS architecture.

## Future Enhancements

1. **Persistent Storage**: Replace InMemoryStreamingEventStore with database backend (PostgreSQL, etc.)
2. **Retry Policy**: Configurable exponential backoff on consumer failure
3. **Metrics**: Track throughput, latency, error rates
4. **Multi-Stream**: Support consuming from multiple streams or topics
5. **Dead Letter Queue**: Route failed events to DLQ after N retries
6. **Exactly-Once Guarantees**: Idempotent consumer operations or distributed transactions
7. **Consumer Groups**: Multiple consumers on same stream with automatic load balancing

## Performance Considerations

- **Producer Batch Size**: Larger batches = fewer lock acquisitions, more latency
- **Consumer Batch Size**: Larger batches = fewer context switches, more memory
- **Queue Depth**: Larger queue = more buffering, higher memory; smaller = more backpressure
- **Thread Count**: 2 threads (producer + consumer) typically sufficient; scale horizontally by running multiple coordinators

## Troubleshooting

**Slow Processing**: Increase consumer batch size or check consumer implementation for bottlenecks

**High Memory**: Decrease queue depth or consumer batch size

**Bookmark Not Advancing**: Check consumer exceptions; they prevent bookmark update

**Missing Events**: Verify bookmark position reflects correct position in event stream; check streaming implementation for position off-by-one errors
