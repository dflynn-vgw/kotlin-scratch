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
            useDlq = true
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
        
        entries.forEach { entry ->
            logger.info("Reprocessing event at position ${entry.streamedEvent.offset.position}")
            resilientExecutor.execute(entry.streamedEvent) {
                // Your processing logic
            }
        }
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
