# Event Streaming Architecture

## Overview

This document describes a comprehensive event streaming platform that provides continuous event consumption with position tracking, fault-tolerant processing, and multiple EventStream implementations for different use cases. The implementation uses the Single Producer, Single Consumer (SPSC) pattern as its internal orchestration mechanism.

## Problem Statement

The original EventStore provided point-in-time read/save operations suitable for aggregate hydration. To support continuous event processing with position tracking and fault tolerance, we needed:

1. Streaming capability to read events from a position with batch size control
2. Checkpoint management (bookmarks) for consumer progress
3. Position-aware events for exactly-once semantics
4. An event processing pipeline using the SPSC pattern
5. Backpressure handling and fault tolerance

## Architecture

### Core Components

#### EventStream Interface

Core abstraction for streaming events with position tracking:

```kotlin
interface EventStream {
    fun stream(fromPosition: Long = 0, batchSize: Int = 1): Flow<Event<Any>>
    suspend fun saveBookmark(name: String, position: Long)
    suspend fun getBookmark(name: String): Bookmark?
}
```

Supports multiple implementations for different use cases (in-memory, CSV files, databases).

#### StreamOffset & StreamedEvent

Position-aware event wrapper enabling exactly-once processing:

```kotlin
data class StreamOffset(val position: Long)

data class StreamedEvent(
    val event: Event<Any>,
    val offset: StreamOffset,
)
```

**Benefits**:
- Makes position semantics explicit and type-safe
- Consumer receives position information directly
- Enables idempotent processing using position as correlation ID
- Simplifies bookmark calculation: `batch.maxOf { it.offset.position } + 1L`

#### Bookmark Entity

```kotlin
data class Bookmark(
    val name: String,
    val position: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
```

Represents consumer progress checkpoint:
- `name`: Unique consumer identifier
- `position`: Last successfully processed event position
- `updatedAt`: Timestamp for audit/debugging

**Failure Semantics**: Bookmark only advances on successful consumption. Consumer exceptions prevent bookmark advancement, enabling automatic replay on retry.

### EventStream Implementations

#### InMemoryEventStream

In-memory implementation with thread-safe event caching:

```kotlin
class InMemoryEventStream(
    seedEvents: List<Event<Any>> = emptyList(),
    private val fixedTime: Long? = null,
) : EventStream
```

Features:
- Thread-safe with `Collections.synchronizedList()` for event log
- Lazy-loaded event caching
- In-memory bookmark persistence
- Deterministic testing with optional fixed timestamps

**Use Case**: Unit tests, lightweight in-process streaming, development.

#### CSVEventStream

File-based implementation for external data sources and integration testing:

```kotlin
class CSVEventStream(
    private val eventsCsvPath: String,
    private val bookmarksDir: String = ".",
) : EventStream
```

Features:
- Reads events from CSV: `ORDER_ID,CUSTOMER_ID,EVENT_TYPE,TIMESTAMP`
- Persists bookmarks to `{BookmarkName}.csv`: `POSITION,TIMESTAMP`
- Lazy-loads and caches events in memory
- Thread-safe with `ReentrantReadWriteLock`
- Graceful handling of malformed CSV lines
- Event payload generation using OrderEventBuilder

**Use Case**: Integration testing, simulating external event sources, development data.

### EventProducer

```kotlin
interface EventProducer {
    suspend fun produce(
        eventStream: EventStream,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<StreamedEvent>
}

class DefaultEventProducer : EventProducer {
    override suspend fun produce(
        eventStream: EventStream,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<StreamedEvent> = flow {
        var currentPosition = fromPosition
        eventStream.stream(fromPosition, batchSize).collect { event ->
            emit(StreamedEvent(event, StreamOffset(currentPosition)))
            currentPosition++
        }
    }
}
```

**Responsibilities**:
- Stream events from EventStream starting at given position
- Wrap each event with its StreamOffset
- Emit StreamedEvent items containing position information
- Support custom implementations for filtering/transformation

### EventConsumer

```kotlin
fun interface EventConsumer {
    suspend fun consume(streamedEvents: List<StreamedEvent>)
}
```

**Contract**:
- Receives batch of position-aware events
- Can calculate next bookmark: `batch.maxOf { it.offset.position } + 1L`
- Exception prevents bookmark advancement (enables replay)
- Ideal for implementing idempotent side effects

### SPSC Queue

```kotlin
class SpscQueue<T>(private val maxCapacity: Int = 100) {
    fun put(item: T): Boolean
    fun poll(timeoutMs: Long = 1000): T?
}
```

Thread-safe bounded queue:
- Uses `LinkedBlockingQueue<T>` internally
- Blocking `put()` when full (producer backpressure)
- Non-blocking `poll()` with timeout
- Respects queue capacity constraints

### SpscCoordinator

```kotlin
class SpscCoordinator(
    private val producer: EventProducer,
    private val consumer: EventConsumer,
    private val eventStream: EventStream,
    private val config: SpscConfig,
) {
    fun start()
    fun stop()
    fun await(timeoutMs: Long = 30000): Boolean
}
```

Orchestrates the SPSC pipeline:

1. **Startup** (`start()`)
   - Spawns producer on thread pool
   - Spawns consumer on separate thread
   - Producer reads starting position from saved bookmark

2. **Producer Thread**
   - Gets starting position from `eventStream.getBookmark()`
   - Calls `producer.produce(eventStream, fromPosition, batchSize)`
   - Emits `StreamedEvent` items with positions
   - Puts streamed events into bounded queue
   - Stops when `isRunning` flag cleared

3. **Consumer Thread**
   - Polls `StreamedEvent` items from queue in batches
   - Batches up to `consumerBatchSize` items
   - Calls `consumer.consume(batch)` with positioned events
   - On success: advances bookmark to `batch.maxOf { it.offset.position } + 1L`
   - On exception: bookmark NOT advanced (triggers replay)
   - Continues processing despite consumer exceptions

4. **Lifecycle**
   - `start()`: Begin processing
   - `stop()`: Signal graceful shutdown
   - `await(timeoutMs)`: Block until both threads complete

### Configuration

```kotlin
data class SpscConfig(
    val producerBatchSize: Int = 10,
    val consumerBatchSize: Int = 5,
    val maxQueueDepth: Int = 100,
    val bookmarkName: String,
)
```

## Usage Examples

### Basic Event Processing with CSVEventStream

```kotlin
// Setup
val eventStream = CSVEventStream("events.csv", ".")
val config = SpscConfig(
    producerBatchSize = 10,
    consumerBatchSize = 5,
    maxQueueDepth = 100,
    bookmarkName = "my-processor",
)

// Define consumer logic
val consumer = EventConsumer { streamedEvents ->
    streamedEvents.forEach { streamedEvent ->
        println("Processing ${streamedEvent.event.type} at position ${streamedEvent.offset.position}")
        // Your business logic here
    }
}

// Run coordinator
val coordinator = SpscCoordinator(
    DefaultEventProducer(),
    consumer,
    eventStream,
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
    eventStream,
    config, // same bookmarkName
)

coordinator2.start() // Resumes from last saved position
```

### Fault Tolerance with Retry

```kotlin
val consumer = EventConsumer { streamedEvents ->
    try {
        streamedEvents.forEach { streamedEvent ->
            processEvent(streamedEvent.event)
        }
        // Success: bookmark advances
    } catch (e: Exception) {
        // Failure: bookmark NOT advanced
        // On next coordinator run, events replayed from same position
        throw e
    }
}
```

### Position-Based Idempotence

```kotlin
val consumer = EventConsumer { streamedEvents ->
    streamedEvents.forEach { streamedEvent ->
        val position = streamedEvent.offset.position
        val eventId = "${bookmarkName}-$position"
        
        // Use position as correlation/idempotency key
        if (!isProcessed(eventId)) {
            processEvent(streamedEvent.event)
            markProcessed(eventId)
        }
    }
}
```

## Design Rationale

### Position-Aware Events (StreamedEvent)

- **Explicit Intent**: Consumer always knows event position
- **Type Safety**: Prevents position from being confused with other Longs
- **Simplicity**: Consumer calculates next bookmark, not coordinator
- **Extensibility**: StreamOffset can be extended (timestamp, stream ID, checksum)

### Multiple EventStream Implementations

- **Flexibility**: Different backends for different needs
- **Testing**: CSV allows deterministic, portable test data
- **Realism**: External file source simulates real streaming scenarios
- **Separation**: EventStore (point-in-time) vs EventStream (continuous)

### Bounded Queue with Backpressure

- **Memory Safe**: Prevents unbounded queue growth
- **Producer Throttling**: Producer waits if queue full
- **Natural Flow Control**: No artificial delays needed
- **Configurability**: Queue depth tunes memory vs responsiveness

### Failure Isolation

- **No Bookmark Advance**: Consumer exception leaves checkpoint unchanged
- **Automatic Replay**: Next run resumes from same position
- **Side Effect Safety**: Enables idempotent processing
- **No Data Loss**: Failed events never dropped

## Implementation Files

Core abstractions:
- `EventStream.kt` - Interface definition
- `StreamOffset.kt` - Position value type
- `StreamedEvent.kt` - Event + offset wrapper
- `Bookmark.kt` - Checkpoint entity

Implementations:
- `InMemoryEventStream.kt` - In-memory implementation
- `CSVEventStream.kt` - CSV file-based implementation
- `EventProducer.kt` - Producer interface & default implementation
- `EventConsumer.kt` - Consumer functional interface
- `SpscConfig.kt` - Configuration
- `SpscQueue.kt` - Internal bounded queue
- `SpscCoordinator.kt` - Main orchestrator (160 lines)

Tests:
- `InMemoryEventStreamTests.kt` - Stream functionality tests
- `CSVEventStreamTests.kt` - CSV implementation tests (12 scenarios)
- `SpscIntegrationTests.kt` - Full pipeline integration tests (7 scenarios)

## Test Coverage

### EventStream Tests
- Streaming from various positions
- Batch size limiting
- Bookmark persistence and retrieval
- Multiple independent bookmarks
- Malformed data handling

### SPSC Integration Tests
- Full pipeline: produce → queue → consume → bookmark
- Bookmark resume on coordinator restart
- Consumer failure doesn't advance bookmark
- Batch size limiting and aggregation
- Position tracking across multiple calls
- Multiple consumers with independent bookmarks
- Large batch processing

Run with:
```bash
./gradlew test
```

## Integration with CQRS/ES

### Event Sourcing (Aggregate Hydration)

```kotlin
// Point-in-time query for aggregate rebuilding
val events = eventStore.read("order-123")
val aggregate = OrderAggregate()
events.forEach { aggregate.apply(it) }
```

### Event Processing (SPSC Streaming)

```kotlin
val coordinator = SpscCoordinator(
    DefaultEventProducer(),
    EventConsumer { streamedEvents ->
        streamedEvents.forEach { streamedEvent ->
            // Update read models
            updateProjection(streamedEvent.event)
            // Send notifications
            notifyListeners(streamedEvent.event)
            // Trigger sagas
            triggerSaga(streamedEvent.event)
        }
    },
    eventStream,
    config,
)

// Run continuously
coordinator.start()
```

Both patterns coexist using the same event source, enabling full CQRS architecture.

## Performance Tuning

### Configuration Impact

- **Producer Batch Size**: Larger = fewer I/O operations, more latency
- **Consumer Batch Size**: Larger = fewer context switches, higher memory
- **Queue Depth**: Larger = more buffering, higher memory; smaller = more backpressure
- **Event Source**: In-memory fast, CSV slightly slower (cached after first read)

### Horizontal Scaling

Run multiple coordinators with different `bookmarkName` values:
```kotlin
// Consumer 1: processes orders
val coordinator1 = SpscCoordinator(..., SpscConfig(..., bookmarkName = "order-processor"))

// Consumer 2: processes payments  
val coordinator2 = SpscCoordinator(..., SpscConfig(..., bookmarkName = "payment-processor"))

// Both process same events independently with separate checkpoints
```

## Future Enhancements

1. **Database EventStream**: PostgreSQL, MySQL, etc. backend
2. **Retry Policy**: Configurable exponential backoff
3. **Metrics/Observability**: Throughput, latency, error tracking
4. **Multi-Stream**: Consume from multiple streams simultaneously
5. **Dead Letter Queue**: Failed events after N retries
6. **Exactly-Once Guarantee**: Distributed transaction support
7. **Consumer Groups**: Auto load-balancing across multiple consumers

## Troubleshooting

**Events not processing**:
- Check if consumer exceptions are being thrown (silently caught)
- Verify `eventStream.stream()` returns events
- Confirm producer is emitting StreamedEvent items

**High memory usage**:
- Reduce `maxQueueDepth` in SpscConfig
- Reduce `consumerBatchSize` to process more frequently
- Check if events are very large

**Bookmark not advancing**:
- Consumer exception prevents advancement (check logs)
- Verify consumer completes successfully
- Check `eventStream.getBookmark(name)` returns expected position

**Missing events**:
- Verify bookmark position is correct with `getBookmark()`
- Check EventStream implementation supports streaming from that position
- Confirm batchSize doesn't truncate events
