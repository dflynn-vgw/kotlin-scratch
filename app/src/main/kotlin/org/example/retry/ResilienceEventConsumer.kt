package org.example.retry

import org.example.events.storage.StreamedEvent
import org.example.spsc.EventConsumer
import org.slf4j.LoggerFactory

/**
 * EventConsumer wrapper that adds retry logic and DLQ support to any EventConsumer.
 *
 * Usage:
 * ```kotlin
 * val resilientConsumer = ResilienceEventConsumer(
 *     delegate = myEventConsumer,
 *     retryService = retryService,
 *     dlqService = dlqService,
 *     strategy = RetryStrategy.DEFAULT
 * )
 * ```
 */
class ResilienceEventConsumer(
    private val delegate: EventConsumer,
    private val retryService: RetryService,
    private val dlqService: DeadLetterQueueService,
    private val strategy: RetryStrategy = RetryStrategy.DEFAULT,
    private val failurePolicy: FailurePolicy = FailurePolicy.CONTINUE_ON_FAILURE,
) : EventConsumer {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun consume(streamedEvents: List<StreamedEvent>) {
        val result = retryService.executeWithRetryBatch(
            streamedEvents = streamedEvents,
            strategy = strategy,
        ) { event ->
            // Process single event by delegating to the underlying consumer
            delegate.consume(listOf(event))
        }

        // Send failures to DLQ
        result.failures.forEach { failure ->
            dlqService.enqueue(failure)
        }

        // Log summary
        if (result.hasFailures) {
            logger.warn(
                "Batch processing completed: {} succeeded, {} failed (sent to DLQ)",
                result.successCount,
                result.failures.size,
            )
        } else {
            logger.debug(
                "Batch processing completed: {} events succeeded",
                result.successCount,
            )
        }

        // Apply failure policy
        when (failurePolicy) {
            FailurePolicy.FAIL_ON_ANY_FAILURE -> {
                if (result.hasFailures) {
                    throw BatchProcessingException(
                        "Batch processing failed: ${result.failures.size} events could not be processed",
                        result.failures,
                    )
                }
            }

            FailurePolicy.CONTINUE_ON_FAILURE -> {
                // Do nothing - failures are already in DLQ
            }
        }
    }
}

/**
 * Policy for handling failures in batch processing.
 */
enum class FailurePolicy {
    /**
     * Throw an exception if any event in the batch fails after retries.
     * This prevents bookmark advancement.
     */
    FAIL_ON_ANY_FAILURE,

    /**
     * Continue processing even if some events fail.
     * Failed events go to DLQ and bookmark advances.
     */
    CONTINUE_ON_FAILURE,
}

/**
 * Exception thrown when batch processing fails according to FailurePolicy.
 */
class BatchProcessingException(
    message: String,
    val failures: List<RetryOutcome.Failure>,
) : Exception(message)
