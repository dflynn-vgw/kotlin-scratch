# Retry & DLQ Implementation

Unified approach for resilient Process Manager event processing with retry strategies and Dead Letter Queue (DLQ) support.

## Overview

The retry and DLQ system provides:
- **Configurable retry strategies** with exponential backoff
- **Non-retriable exception detection** (e.g., NullPointerException, ClassCastException)
- **Dead Letter Queue** for events that fail after all retries
- **Easy integration** with existing EventConsumer implementations
- **Two failure policies**: fail-fast or continue-on-failure

## Quick Start

### 1. Basic Usage with ResilienceEventConsumer

Wrap your existing EventConsumer with resilience features:

```kotlin
@Configuration
class MyProcessManagerConfig {
    
    @Bean
    fun myBusinessLogicConsumer(): EventConsumer = DefaultEventConsumer { events ->
        events.forEach { event ->
            // Your business logic here
            when (event.event.type) {
                "OrderCreated" -> handleOrderCreated(event)
                "OrderShipped" -> handleOrderShipped(event)
                else -> logger.warn("Unknown event type: ${event.event.type}")
            }
        }
    }

    @Bean
    fun resilientConsumer(
        businessLogicConsumer: EventConsumer,
        retryService: RetryService,
        dlqService: DeadLetterQueueService
    ): EventConsumer = ResilienceEventConsumer(
        delegate = businessLogicConsumer,
        retryService = retryService,
        dlqService = dlqService,
        strategy = RetryStrategy.DEFAULT, // 3 attempts, exponential backoff
        failurePolicy = FailurePolicy.CONTINUE_ON_FAILURE // Failed events go to DLQ
    )

    @Bean
    fun spscCoordinator(
        resilientConsumer: EventConsumer,
        producer: EventProducer,
        eventStream: EventStream,
        config: SpscConfig
    ): SpscCoordinator = SpscCoordinator(
        producer = producer,
        consumer = resilientConsumer, // Use resilient wrapper
        eventStream = eventStream,
        config = config
    )
}
```

### 2. Direct Usage of RetryService

For fine-grained control, use RetryService directly:

```kotlin
class MyEventProcessor(
    private val retryService: RetryService,
    private val dlqService: DeadLetterQueueService
) {
    suspend fun processEvent(streamedEvent: StreamedEvent) {
        val outcome = retryService.executeWithRetry(
            streamedEvent = streamedEvent,
            strategy = RetryStrategy(
                maxAttempts = 5,
                initialDelay = 200.milliseconds,
                maxDelay = 30.seconds,
                backoffMultiplier = 2.0
            )
        ) { event ->
            // Your processing logic
            updateReadModel(event)
            sendNotification(event)
        }

        when (outcome) {
            is RetryOutcome.Success -> {
                logger.info("Processed successfully after ${outcome.attemptCount} attempts")
            }
            is RetryOutcome.Failure -> {
                logger.error("Failed after ${outcome.attemptCount} attempts")
                dlqService.enqueue(outcome)
            }
        }
    }
}
```

## Retry Strategies

### Predefined Strategies

```kotlin
// Default: 3 attempts, 100ms initial delay, 10s max delay, 2.0 multiplier
RetryStrategy.DEFAULT

// No retry: Fail immediately
RetryStrategy.NO_RETRY

// Aggressive: 5 attempts, 50ms initial delay, 5s max delay, 1.5 multiplier
RetryStrategy.AGGRESSIVE
```

### Custom Strategy

```kotlin
val customStrategy = RetryStrategy(
    maxAttempts = 4,
    initialDelay = 250.milliseconds,
    maxDelay = 20.seconds,
    backoffMultiplier = 1.8,
    retryableExceptions = setOf(
        IOException::class.java,
        TimeoutException::class.java
    ) // Only retry these specific exceptions
)
```

### Backoff Calculation

The retry delay follows exponential backoff:
- Attempt 1: `initialDelay`
- Attempt 2: `initialDelay * backoffMultiplier`
- Attempt 3: `initialDelay * backoffMultiplier²`
- ...capped at `maxDelay`

Example with DEFAULT strategy:
- Initial attempt: 0ms (immediate)
- Retry 1: 100ms delay
- Retry 2: 200ms delay
- Retry 3: 400ms delay (but only 3 attempts max)

## Non-Retriable Exceptions

These exceptions indicate programming errors and will NOT be retried:
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
- `AccessControlException`

Add your own non-retriable exceptions by updating `NonRetriableExceptions.TYPES`.

## Dead Letter Queue (DLQ)

### Configuration

```kotlin
@Bean
fun deadLetterQueueOptions(): DeadLetterQueueOptions = DeadLetterQueueOptions(
    enabled = true,
    type = DeadLetterQueueOptions.StorageType.FILE, // or StorageType.LOG
    filePath = "dlq/failed-events.jsonl"
)
```

### DLQ Entry Format

Each DLQ entry contains:
- Original `StreamedEvent` (position, event data)
- Failure reason and exception type
- Full stack trace
- Number of attempts made
- Whether the failure was retriable
- Timestamp

Example DLQ entry (FILE mode):
```json
{
  "streamedEvent": {
    "event": {"type": "OrderCreated", "data": {...}},
    "offset": {"position": 42}
  },
  "failureReason": "Connection timeout",
  "exceptionType": "java.net.SocketTimeoutException",
  "stackTrace": "...",
  "attemptCount": 3,
  "retriable": true,
  "enqueuedAt": "2026-01-13T02:57:30Z"
}
```

## Failure Policies

### CONTINUE_ON_FAILURE (Recommended)

Failed events go to DLQ, processing continues, bookmark advances.

```kotlin
ResilienceEventConsumer(
    delegate = consumer,
    retryService = retryService,
    dlqService = dlqService,
    failurePolicy = FailurePolicy.CONTINUE_ON_FAILURE
)
```

**Use when:** You want to maximize throughput and handle failures asynchronously.

### FAIL_ON_ANY_FAILURE

Throws exception if any event fails, preventing bookmark advancement.

```kotlin
ResilienceEventConsumer(
    delegate = consumer,
    retryService = retryService,
    dlqService = dlqService,
    failurePolicy = FailurePolicy.FAIL_ON_ANY_FAILURE
)
```

**Use when:** Event processing must be strictly sequential and you can't skip failures.

## Testing

### Simulating Failures

```kotlin
class FailingEventConsumer : EventConsumer {
    var failureCount = 0
    
    override suspend fun consume(streamedEvents: List<StreamedEvent>) {
        if (failureCount++ < 2) {
            throw IOException("Simulated transient failure")
        }
        // Succeed after 2 failures
    }
}
```

### Testing Retry Logic

```kotlin
@Test
fun `should retry transient failures and succeed`() = runBlocking {
    val consumer = FailingEventConsumer()
    val resilient = ResilienceEventConsumer(
        delegate = consumer,
        retryService = retryService,
        dlqService = dlqService,
        strategy = RetryStrategy(maxAttempts = 3)
    )
    
    resilient.consume(listOf(testEvent))
    
    // Verify succeeded after retries
    assertEquals(3, consumer.failureCount)
}
```

## Architecture

```
┌─────────────────────────────────────┐
│   SpscCoordinator                   │
│   (Manages Producer/Consumer)       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   ResilienceEventConsumer           │
│   (Wrapper with retry + DLQ)        │
└──────────────┬──────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌─────────────┐ ┌──────────────────┐
│ RetryService│ │ DLQService       │
│ (Retries)   │ │ (Failed events)  │
└──────┬──────┘ └──────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│   Your EventConsumer                │
│   (Business Logic)                  │
└─────────────────────────────────────┘
```

## Best Practices

1. **Choose appropriate strategies**: Use aggressive retries for external service calls, conservative for local operations
2. **Monitor DLQ**: Set up alerts when DLQ accumulates entries
3. **Handle DLQ events**: Build tooling to replay or investigate failed events
4. **Use CONTINUE_ON_FAILURE**: For most use cases, to maximize throughput
5. **Log appropriately**: Retry logic logs at DEBUG (retries) and ERROR (failures)
6. **Custom non-retriable exceptions**: Add your validation exceptions to avoid pointless retries

## Production Considerations

- **DLQ Storage**: Consider implementing database-backed DLQ for production
- **DLQ Monitoring**: Expose metrics for DLQ size and failure rate
- **Replay Mechanism**: Build tooling to reprocess DLQ events
- **Alerting**: Alert on DLQ growth or high retry rates
- **Testing**: Load test with simulated failures to validate retry behavior
