package org.example.retry

import kotlinx.coroutines.runBlocking
import org.example.events.storage.StreamedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Default implementation of ResilientExecutor that provides retry and DLQ support.
 *
 * This executor wraps operations with retry logic and automatically sends failed
 * operations to the Dead Letter Queue after exhausting all retries.
 */
@Component
class DefaultResilientExecutor(
    private val retryStrategy: RetryStrategy = RetryStrategy.DEFAULT,
    private val dlqService: DeadLetterQueueService,
    private val useDlq: Boolean = false,
) : ResilientExecutor {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(event: StreamedEvent, action: () -> Unit): ResilientExecutor.Outcome {
        var lastException: Throwable? = null
        var attemptCount = 0

        for (attempt in 0 until retryStrategy.maxAttempts) {
            attemptCount = attempt + 1

            try {
                if (attempt > 0) {
                    val delay = retryStrategy.calculateDelay(attempt - 1)
                    logger.debug("Retrying action (attempt {}/{})", attemptCount, retryStrategy.maxAttempts)
                    Thread.sleep(delay.inWholeMilliseconds)
                }

                action()

                if (attempt > 0) {
                    logger.info("Action succeeded after {} attempts", attemptCount)
                }
                return ResilientExecutor.Outcome.Success(attemptCount)
            } catch (e: Throwable) {
                lastException = e

                if (!NonRetriableExceptions.isRetriable(e, retryStrategy.retryableExceptions)) {
                    logger.error("Non-retriable exception: {}", e.message, e)
                    break
                }

                logger.warn("Retriable exception (attempt {}/{}): {}", attemptCount, retryStrategy.maxAttempts, e.message)
            }
        }

        logger.error("Action failed after {} attempts", attemptCount)

        // Send to DLQ if failed
        if (useDlq) {
            enqueueToDlq(event, lastException!!, attemptCount, NonRetriableExceptions.isRetriable(lastException, retryStrategy.retryableExceptions))
        }
        return ResilientExecutor.Outcome.Failure(attemptCount, lastException!!)
    }

    /** Enqueue the failed event to the Dead Letter Queue (DLQ). */
    private fun enqueueToDlq(event: StreamedEvent, lastException: Throwable, attemptCount: Int, retriable: Boolean) {
        runBlocking {
            dlqService.enqueue(
                DeadLetterEntry(
                    streamedEvent = event,
                    failureReason = lastException.message ?: "Unknown error",
                    exceptionType = lastException.javaClass.name,
                    stackTrace = lastException.stackTraceToString(),
                    attemptCount = attemptCount,
                    retriable = retriable,
                ),
            )
        }
    }
}
