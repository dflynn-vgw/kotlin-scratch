# Resilient Execution

This document describes the retry and Dead Letter Queue (DLQ) implementation for Process Managers and SPSC-style event processors.

## Overview

The resilient execution framework provides a unified approach to handling transient failures and managing events that cannot be processed successfully. It consists of three main components:

1. **ResilientExecutor** - Wraps event processing logic with retry capabilities
2. **RetryStrategy** - Configures retry behavior (attempts, delays, backoff)
3. **DeadLetterQueue** - Manages events that fail after exhausting all retry attempts

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                     Process Manager / Consumer             │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              ResilientExecutor                       │  │
│  │                                                      │  │
│  │  1. Execute action                                   │  │
│  │  2. On failure, check if retriable                   │  │
│  │  3. Retry with exponential backoff                   │  │
│  │  4. Send to DLQ if all retries exhausted             │  │
│  └──────────────────────────────────────────────────────┘  │
│            │                           │                   │
│            │ (retry)                   │ (failed)          │
│            ▼                           ▼                   │
│  ┌─────────────────┐        ┌──────────────────────┐       │
│  │ RetryStrategy   │        │  DeadLetterQueue     │       │
│  │ - maxAttempts   │        │  - FILE              │       │
│  │ - delays        │        │  - LOG               │       │
│  │ - backoff       │        │  - DATABASE (future) │       │
│  └─────────────────┘        └──────────────────────┘       │
└────────────────────────────────────────────────────────────┘
```

## ResilientExecutor

The `ResilientExecutor` provides a simple interface for executing event processing logic with automatic retry and DLQ support.

### Basic Usage

```kotlin
class OrderProcessManager(
    private val resilientExecutor: ResilientExecutor
) {
    fun processEvent(event: StreamedEvent) {
        // Wrap your processing logic with resilient execution
        val outcome = resilientExecutor.execute(event) {
            handleEvent(event)
        }
        
        when (outcome) {
            is ResilientExecutor.Outcome.Success -> 
                logger.info("Processed successfully after ${outcome.attemptCount} attempts")
            is ResilientExecutor.Outcome.Failure -> 
                logger.error("Failed after ${outcome.attemptCount} attempts", outcome.lastException)
        }
    }
    
    private fun handleEvent(event: StreamedEvent) {
        // Your business logic here
        updateReadModel(event)
        sendNotifications(event)
    }
}
```

### Configuration

```kotlin
@Bean
fun resilientExecutor(dlqService: DeadLetterQueue): ResilientExecutor {
    return ResilientExecutor(
        options = ResilientExecutor.Options(
            retryStrategy = RetryStrategy.DEFAULT,
            useDlq = true,
            dlqOptions = DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = "dlq/failed-events.jsonl",
                replayMode = DeadLetterQueue.Options.ReplayMode.MANUAL_REVIEW
            )
        ),
        dlqService = dlqService
    )
}
```

### Outcome

The `execute()` method returns an `Outcome` sealed class:

- **Success(attemptCount)** - Operation succeeded (possibly after retries)
- **Failure(attemptCount, lastException)** - Operation failed after exhausting all retries

## RetryStrategy

Configures how retries are performed, including the number of attempts, delays, and which exceptions should trigger retries.

### Predefined Strategies

```kotlin
// Default: 3 attempts with exponential backoff
RetryStrategy.DEFAULT

// No retry: fail immediately
RetryStrategy.NO_RETRY

// Aggressive: 5 attempts with faster retries
RetryStrategy.AGGRESSIVE
```

### Custom Strategy

```kotlin
val customStrategy = RetryStrategy(
    maxAttempts = 5,              // Total attempts (including initial)
    initialDelay = 200.milliseconds,  // First retry delay
    maxDelay = 10.seconds,        // Maximum delay cap
    backoffMultiplier = 2.0,      // Exponential multiplier
    retryableExceptions = setOf(  // Optional: only retry these
        IOException::class.java,
        TimeoutException::class.java
    )
)
```

### Backoff Calculation

The retry delay grows exponentially:

```
Attempt 1: 100ms
Attempt 2: 100ms × 2.0 = 200ms
Attempt 3: 200ms × 2.0 = 400ms
Attempt 4: 400ms × 2.0 = 800ms
Attempt 5: 800ms × 2.0 = 1600ms (capped at maxDelay)
```

### Non-Retriable Exceptions

The following exceptions are **never retried** as they indicate programming errors or unrecoverable states:

- `NullPointerException`
- `ClassCastException`
- `IndexOutOfBoundsException`
- `IllegalArgumentException`
- `IllegalStateException`
- `NoSuchElementException`
- `NumberFormatException`
- `UninitializedPropertyAccessException`
- `TypeCastException`
- `SecurityException`

### Retriable Exception Modes

**Default Mode** (empty `retryableExceptions`):
- Retries all exceptions **except** those in the non-retriable list
- Good for general-purpose retry behavior

**Explicit Mode** (specified `retryableExceptions`):
- **Only** retries the specified exception types
- More restrictive and predictable
- Good when you know exactly which failures are transient

```kotlin
// Default mode: retry everything except programming errors
RetryStrategy(maxAttempts = 3)

// Explicit mode: only retry specific exceptions
RetryStrategy(
    maxAttempts = 3,
    retryableExceptions = setOf(
        IOException::class.java,
        TimeoutException::class.java
    )
)
```

## DeadLetterQueue

Events that fail after exhausting all retry attempts are sent to the DLQ for investigation and potential reprocessing.

### Storage Types

#### FILE (Development/Testing)

Writes failed events to a JSONL file:

```kotlin
@Bean
fun deadLetterQueue(): DeadLetterQueue {
    return DeadLetterQueue(
        options = DeadLetterQueue.Options(
            enabled = true,
            type = DeadLetterQueue.Options.StorageType.FILE,
            filePath = "dlq/failed-events.jsonl"
        )
    )
}
```

The file format is newline-delimited JSON (JSONL), with one entry per line:

```json
{"streamedEvent":{...},"failureReason":"Connection timeout","exceptionType":"java.io.IOException","stackTrace":"...","attemptCount":3,"retriable":true,"enqueuedAt":"2026-01-13T09:00:00Z"}
```

#### LOG (Development)

Logs failed events using SLF4J:

```kotlin
DeadLetterQueue.Options(
    enabled = true,
    type = DeadLetterQueue.Options.StorageType.LOG
)
```

Outputs structured log entries:

```
ERROR org.example.retry.DeadLetterQueue - DLQ Entry:
  Event Position: 1234
  Event Type: ORDER_PLACED
  Failure Reason: Connection timeout
  Exception Type: java.io.IOException
  Attempt Count: 3
  Retriable: true
  Enqueued At: 2026-01-13T09:00:00Z
  Stack Trace: ...
```

### DLQ Entry

Each DLQ entry contains:

- **streamedEvent** - The full event that failed (including offset)
- **failureReason** - Exception message
- **exceptionType** - Java class name of the exception
- **stackTrace** - Full stack trace for debugging
- **attemptCount** - Number of attempts made
- **retriable** - Whether the exception was considered retriable
- **enqueuedAt** - Timestamp when added to DLQ
- **enqueuedBy** - Source identifier of the component that enqueued the entry
- **status** - Current lifecycle status (PENDING, REPLAY, RESOLVED, FAILED, DISCARDED)

### Replay Modes

The DLQ supports two replay modes that determine the initial status of entries:

#### MANUAL_REVIEW (Default)

Entries are created with `PENDING` status and require engineer intervention before reprocessing:

```kotlin
DeadLetterQueue.Options(
    enabled = true,
    type = DeadLetterQueue.Options.StorageType.FILE,
    replayMode = DeadLetterQueue.Options.ReplayMode.MANUAL_REVIEW
)
```

**Use when:**
- You want engineers to investigate failures before retry
- Failures might indicate data quality issues
- Manual triage is part of your incident response process

**Workflow:**
1. Event fails → Entry created with status `PENDING`
2. Engineer investigates the failure
3. Engineer marks entry as `REPLAY` (or `DISCARDED` if invalid)
4. Reprocessing system picks up `REPLAY` entries
5. After successful replay → status becomes `RESOLVED`

#### AUTOMATIC_REPLAY

Entries are created with `REPLAY` status and are immediately ready for reprocessing:

```kotlin
DeadLetterQueue.Options(
    enabled = true,
    type = DeadLetterQueue.Options.StorageType.FILE,
    replayMode = DeadLetterQueue.Options.ReplayMode.AUTOMATIC_REPLAY
)
```

**Use when:**
- Failures are typically transient (network issues, temporary outages)
- You have high confidence in automatic retry safety
- Manual review overhead is too high for your volume

**Workflow:**
1. Event fails → Entry created with status `REPLAY`
2. Reprocessing system automatically picks up entry
3. After successful replay → status becomes `RESOLVED`
4. If replay fails → status becomes `FAILED` (may require manual review)

### Entry Status Lifecycle

The `Entry.Status` enum tracks the lifecycle of DLQ entries:

- **PENDING** - Awaiting manual review/investigation
- **REPLAY** - Ready for reprocessing (either set automatically or by engineer)
- **RESOLVED** - Successfully reprocessed
- **FAILED** - Reprocessing failed
- **DISCARDED** - Entry marked as invalid/not worth reprocessing

### Circuit Breaker

The DLQ includes a circuit breaker to prevent system overload when failure rates are high. When the enqueue rate exceeds a configured threshold, the circuit breaker opens and rejects new entries.

#### Configuration

```kotlin
DeadLetterQueue.Options(
    enabled = true,
    type = DeadLetterQueue.Options.StorageType.FILE,
    filePath = "dlq/failed-events.jsonl",
    circuitBreaker = DeadLetterQueue.Options.CircuitBreakerOptions(
        enabled = true,
        rateThreshold = 10.0,      // Max 10 events/second
        windowMillis = 60_000       // Calculate rate over 1 minute
    )
)
```

**Parameters:**
- `enabled` - Enable/disable circuit breaker (default: false)
- `rateThreshold` - Maximum enqueue rate in events per second (default: 10.0)
- `windowMillis` - Time window for rate calculation in milliseconds (default: 60,000 = 1 minute)

#### Behavior

1. **Rate Tracking**: The DLQ tracks all successful enqueue timestamps within the configured window
2. **Rate Calculation**: Rate = (number of enqueues in window) / (window duration in seconds)
3. **Circuit Opens**: When rate ≥ threshold, `enqueue()` returns `CircuitBreakerOpen` outcome
4. **Rejection**: Circuit breaker rejects new entries until rate drops below threshold
5. **Automatic Recovery**: As timestamps age out of the window, rate decreases and circuit may close

#### EnqueueOutcome

The `enqueue()` method now returns an `EnqueueOutcome` sealed class:

```kotlin
sealed class EnqueueOutcome {
    abstract val currentRate: Double
    
    data class Success(override val currentRate: Double)
    
    data class Failure(
        override val currentRate: Double,
        val exception: Throwable
    ) {
        fun isCircuitBreakerOpen(): Boolean = 
            exception is DlqThresholdExceededException
    }
}
```

When the circuit breaker opens, `enqueue()` returns `Failure` with a `DlqThresholdExceededException`:

```kotlin
class DlqThresholdExceededException(
    val currentRate: Double,
    val threshold: Double
) : RuntimeException("DLQ circuit breaker threshold exceeded...")
```

#### Process Manager Integration

Process Managers can check the circuit breaker state and halt processing:

```kotlin
class OrderProcessManager(
    private val resilientExecutor: ResilientExecutor
) {
    fun processEvents(events: List<StreamedEvent>): Boolean {
        events.forEach { event ->
            val outcome = resilientExecutor.execute("OrderPM", event) {
                handleOrderEvent(event)
            }
            
            when (outcome) {
                is ResilientExecutor.Outcome.Success ->
                    logger.info("Processed event successfully")
                    
                is ResilientExecutor.Outcome.Failure -> {
                    if (outcome.isCircuitBreakerOpen()) {
                        logger.error("DLQ circuit breaker OPEN - halting event processing")
                        return false // Stop processing
                    }
                    logger.error("Event failed but circuit breaker closed")
                }
            }
        }
        return true // Continue processing
    }
}
```

#### Monitoring Current Rate

Access the current enqueue rate:

```kotlin
val dlq = DeadLetterQueue(options)
val currentRate = dlq.getEnqueueRate() // events per second
logger.info("Current DLQ enqueue rate: $currentRate/sec")
```

#### Use Cases

**Enable circuit breaker when:**
- Cascading failures might overwhelm your system
- DLQ storage has limited capacity
- High failure rates indicate systemic issues requiring immediate attention
- You want to "fail fast" and stop processing when things go wrong

**Disable circuit breaker when:**
- You want to ensure all failures are captured regardless of rate
- DLQ storage is effectively unlimited (e.g., Kafka topic)
- Manual intervention is always required anyway
- Stopping event processing causes worse problems than continuing

### Disabling DLQ

```kotlin
DeadLetterQueue.Options(enabled = false)
```

When disabled, failed events are logged as warnings and dropped.

## Complete Example

```kotlin
@Configuration
class ProcessManagerConfiguration {
    
    @Bean
    fun deadLetterQueue(): DeadLetterQueue {
        return DeadLetterQueue(
            options = DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = "dlq/failed-events.jsonl"
            )
        )
    }
    
    @Bean
    fun resilientExecutor(dlqService: DeadLetterQueue): ResilientExecutor {
        return ResilientExecutor(
            options = ResilientExecutor.Options(
                retryStrategy = RetryStrategy(
                    maxAttempts = 5,
                    initialDelay = 200.milliseconds,
                    maxDelay = 10.seconds,
                    backoffMultiplier = 2.0,
                    retryableExceptions = setOf(
                        IOException::class.java,
                        TimeoutException::class.java
                    )
                ),
                useDlq = true
            ),
            dlqService = dlqService
        )
    }
    
    @Bean
    fun orderProcessManager(
        resilientExecutor: ResilientExecutor,
        orderRepository: OrderRepository
    ): EventConsumer {
        return DefaultEventConsumer { events ->
            events.forEach { event ->
                resilientExecutor.execute(event) {
                    when (event.event.type) {
                        Event.EventType.ORDER_PLACED -> 
                            orderRepository.createOrder(event)
                        Event.EventType.ORDER_CONFIRMED -> 
                            orderRepository.confirmOrder(event)
                        Event.EventType.ORDER_CANCELLED -> 
                            orderRepository.cancelOrder(event)
                        Event.EventType.ORDER_MODIFIED -> 
                            orderRepository.modifyOrder(event)
                    }
                }
            }
        }
    }
}
```

## Best Practices

### 1. Choose Appropriate Retry Strategies

- **Network operations**: Use `RetryStrategy.DEFAULT` or `AGGRESSIVE`
- **Database operations**: Use shorter delays and fewer retries
- **External APIs**: Consider rate limits and use longer delays
- **Non-idempotent operations**: Use `RetryStrategy.NO_RETRY`

### 2. DLQ Storage Selection

- **Development**: Use `StorageType.LOG` for simplicity
- **Testing**: Use `StorageType.FILE` for inspection
- **Production**: Plan for `DATABASE` or `KAFKA` (future implementations)

### 3. Monitor DLQ

Set up alerts when events enter the DLQ:

```kotlin
class MonitoredDeadLetterQueue(
    options: Options,
    private val metrics: MetricsService
) : DeadLetterQueue(options) {
    
    override fun enqueue(entry: Entry) {
        super.enqueue(entry)
        metrics.incrementCounter("dlq.events.total")
        metrics.recordGauge("dlq.event.attempt_count", entry.attemptCount.toDouble())
    }
}
```

### 4. Idempotency

Ensure your event handlers are idempotent, as retries will execute the same logic multiple times:

```kotlin
fun handleOrderPlaced(event: StreamedEvent) {
    val order = extractOrder(event)
    
    // Check if already processed
    if (orderRepository.exists(order.id)) {
        logger.warn("Order ${order.id} already processed, skipping")
        return
    }
    
    orderRepository.save(order)
}
```

### 5. DLQ Reprocessing

Implement a mechanism to reprocess DLQ entries:

```kotlin
class DlqReprocessor(
    private val resilientExecutor: ResilientExecutor
) {
    fun reprocessFailedEvents(dlqFilePath: String) {
        val entries = Files.readAllLines(Path.of(dlqFilePath))
            .map { it.fromJSON<DeadLetterQueue.Entry>() }
            .filter { it.status == DeadLetterQueue.Entry.Status.REPLAY } // Only replay marked entries
        
        entries.forEach { entry ->
            logger.info("Reprocessing event at position ${entry.streamedEvent.offset.position}")
            val outcome = resilientExecutor.execute("dlq-reprocessor", entry.streamedEvent) {
                // Your processing logic
            }
            
            // Update status based on outcome
            when (outcome) {
                is ResilientExecutor.Outcome.Success -> 
                    updateEntryStatus(entry, DeadLetterQueue.Entry.Status.RESOLVED)
                is ResilientExecutor.Outcome.Failure -> 
                    updateEntryStatus(entry, DeadLetterQueue.Entry.Status.FAILED)
            }
        }
    }
    
    private fun updateEntryStatus(entry: DeadLetterQueue.Entry, newStatus: DeadLetterQueue.Entry.Status) {
        // Implementation depends on DLQ storage type
        // For FILE: rewrite the JSONL file with updated status
        // For DATABASE: UPDATE statement
    }
}
```

### 6. Manual Status Management

For MANUAL_REVIEW mode, provide tooling to change entry status:

```kotlin
class DlqManager {
    fun markForReplay(dlqFilePath: String, position: Long) {
        val entries = Files.readAllLines(Path.of(dlqFilePath))
            .map { it.fromJSON<DeadLetterQueue.Entry>() }
            .map { entry ->
                if (entry.streamedEvent.offset.position == position) {
                    entry.copy(status = DeadLetterQueue.Entry.Status.REPLAY)
                } else {
                    entry
                }
            }
        
        // Rewrite file with updated entries
        Files.writeString(
            Path.of(dlqFilePath),
            entries.joinToString("\n") { it.toJSON() } + "\n"
        )
    }
    
    fun discardEntry(dlqFilePath: String, position: Long) {
        // Similar implementation, setting status to DISCARDED
    }
}
```

## Testing

### Unit Testing with ResilientExecutor

```kotlin
@Test
fun `should retry on transient failure and eventually succeed`() {
    val dlq = DeadLetterQueue(DeadLetterQueue.Options(enabled = false))
    val executor = ResilientExecutor(
        ResilientExecutor.Options(
            retryStrategy = RetryStrategy(maxAttempts = 3),
            useDlq = false
        ),
        dlq
    )
    
    var attempts = 0
    val outcome = executor.execute(testEvent) {
        attempts++
        if (attempts < 3) throw IOException("Transient error")
        // Succeed on third attempt
    }
    
    assertTrue(outcome is ResilientExecutor.Outcome.Success)
    assertEquals(3, (outcome as ResilientExecutor.Outcome.Success).attemptCount)
}
```

### Testing DLQ Behavior

```kotlin
@Test
fun `should send to DLQ after exhausting retries`() {
    val tempFile = Files.createTempFile("test-dlq", ".jsonl")
    val dlq = DeadLetterQueue(
        DeadLetterQueue.Options(
            enabled = true,
            type = DeadLetterQueue.Options.StorageType.FILE,
            filePath = tempFile.toString()
        )
    )
    
    val executor = ResilientExecutor(
        ResilientExecutor.Options(
            retryStrategy = RetryStrategy(maxAttempts = 2),
            useDlq = true
        ),
        dlq
    )
    
    val outcome = executor.execute(testEvent) {
        throw IOException("Persistent error")
    }
    
    assertTrue(outcome is ResilientExecutor.Outcome.Failure)
    assertTrue(Files.exists(tempFile))
    assertTrue(Files.readString(tempFile).contains("Persistent error"))
}
```

## Future Enhancements

- **DATABASE storage**: Store DLQ entries in a relational database for querying
- **KAFKA storage**: Publish failed events to a Kafka topic for distributed processing
- **Jitter**: Add randomized jitter to backoff delays to prevent thundering herd
- **Circuit breaker**: Skip retries when downstream service is known to be down
- **Metrics**: Built-in metrics for retry counts, DLQ size, success rates
- **Reprocessing API**: REST endpoints for DLQ inspection and reprocessing
