package org.example.retry

import kotlinx.coroutines.delay
import org.example.events.storage.StreamedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service that provides retry logic for event processing operations.
 *
 * Wraps any operation on a StreamedEvent with configurable retry behavior.
 * Handles exponential backoff and distinguishes between retriable and non-retriable exceptions.
 */
@Service
class RetryService {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Execute an operation with retry logic.
     *
     * @param streamedEvent The event being processed
     * @param strategy The retry strategy to use
     * @param operation The operation to execute (receives the StreamedEvent)
     * @return RetryOutcome indicating success or failure
     */
    suspend fun <T> executeWithRetry(
        streamedEvent: StreamedEvent,
        strategy: RetryStrategy = RetryStrategy.DEFAULT,
        operation: suspend (StreamedEvent) -> T,
    ): RetryOutcome {
        var lastException: Throwable? = null
        var attemptCount = 0

        repeat(strategy.maxAttempts) { attempt ->
            attemptCount = attempt + 1

            try {
                if (attempt > 0) {
                    val delay = strategy.calculateDelay(attempt - 1)
                    logger.debug(
                        "Retrying event at position {} (attempt {}/{}) after {}ms",
                        streamedEvent.offset.position,
                        attemptCount,
                        strategy.maxAttempts,
                        delay.inWholeMilliseconds,
                    )
                    delay(delay)
                }

                // Execute the operation
                operation(streamedEvent)

                // Success!
                if (attempt > 0) {
                    logger.info(
                        "Event at position {} succeeded after {} attempts",
                        streamedEvent.offset.position,
                        attemptCount,
                    )
                }
                return RetryOutcome.Success(attemptCount)
            } catch (e: Throwable) {
                lastException = e
                val retriable = NonRetriableExceptions.isRetriable(e, strategy.retryableExceptions)

                if (!retriable) {
                    logger.error(
                        "Non-retriable exception for event at position {}: {}",
                        streamedEvent.offset.position,
                        e.message,
                        e,
                    )
                    return RetryOutcome.Failure(
                        streamedEvent = streamedEvent,
                        lastException = e,
                        attemptCount = attemptCount,
                        retriable = false,
                    )
                }

                logger.warn(
                    "Retriable exception for event at position {} (attempt {}/{}): {}",
                    streamedEvent.offset.position,
                    attemptCount,
                    strategy.maxAttempts,
                    e.message,
                )
            }
        }

        // All retries exhausted
        logger.error(
            "Event at position {} failed after {} attempts",
            streamedEvent.offset.position,
            attemptCount,
        )
        return RetryOutcome.Failure(
            streamedEvent = streamedEvent,
            lastException = lastException!!,
            attemptCount = attemptCount,
            retriable = true,
        )
    }

    /**
     * Execute an operation on a batch of events with retry logic for each event.
     *
     * @param streamedEvents The batch of events to process
     * @param strategy The retry strategy to use
     * @param operation The operation to execute on each event
     * @return Pair of success count and list of failures
     */
    suspend fun <T> executeWithRetryBatch(
        streamedEvents: List<StreamedEvent>,
        strategy: RetryStrategy = RetryStrategy.DEFAULT,
        operation: suspend (StreamedEvent) -> T,
    ): BatchRetryResult {
        val failures = mutableListOf<RetryOutcome.Failure>()
        var successCount = 0

        for (event in streamedEvents) {
            when (val outcome = executeWithRetry(event, strategy, operation)) {
                is RetryOutcome.Success -> successCount++
                is RetryOutcome.Failure -> failures.add(outcome)
            }
        }

        return BatchRetryResult(
            successCount = successCount,
            failures = failures,
        )
    }
}

/**
 * Result of processing a batch of events with retry logic.
 */
data class BatchRetryResult(
    val successCount: Int,
    val failures: List<RetryOutcome.Failure>,
) {
    val totalCount: Int get() = successCount + failures.size
    val hasFailures: Boolean get() = failures.isNotEmpty()
}
