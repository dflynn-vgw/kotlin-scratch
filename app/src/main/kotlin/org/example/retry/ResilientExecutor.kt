package org.example.retry

import kotlinx.coroutines.runBlocking
import org.example.events.storage.StreamedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Executes actions with retry logic and optional Dead Letter Queue (DLQ) support. */
class ResilientExecutor(
    private val options: Options,
    private val dlqService: DeadLetterQueue,
    private val logger: Logger = LoggerFactory.getLogger(ResilientExecutor::class.java),
) {
    /** Execute the given action with retry logic and DLQ handling. */
    fun execute(
        /** The source identifier for logging and DLQ purposes. */
        source: String,
        /** The event being processed. */
        event: StreamedEvent,
        /** The action to execute with retry logic. */
        action: () -> Unit,
    ): Outcome {
        val (retryStrategy, useDlq) = options
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
                return Outcome.Success(attemptCount)
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
            enqueueToDlq(source, event, lastException!!, attemptCount, NonRetriableExceptions.isRetriable(lastException, retryStrategy.retryableExceptions))
        }
        return Outcome.Failure(attemptCount, lastException!!)
    }

    /** Enqueue the failed event to the Dead Letter Queue (DLQ). */
    private fun enqueueToDlq(source: String, event: StreamedEvent, lastException: Throwable, attemptCount: Int, retriable: Boolean) {
        runBlocking {
            dlqService.enqueue(
                DeadLetterQueue.Entry(
                    streamedEvent = event,
                    failureReason = lastException.message ?: "Unknown error",
                    exceptionType = lastException.javaClass.name,
                    stackTrace = lastException.stackTraceToString(),
                    attemptCount = attemptCount,
                    retriable = retriable,
                    enqueuedAt = System.currentTimeMillis(),
                    enqueuedBy = source,
                ),
            )
        }
    }

    /** Represents the outcome of a retry operation. */
    sealed class Outcome {
        /** Operation succeeded (possibly after retries). */
        data class Success(val attemptCount: Int) : Outcome()

        /** Operation failed after exhausting all retry attempts. */
        data class Failure(val attemptCount: Int, val lastException: Throwable) : Outcome()
    }

    data class Options(
        val retryStrategy: RetryStrategy = RetryStrategy.DEFAULT,
        val useDlq: Boolean = false,
    )

    private companion object {
        /** Exceptions that should NOT be retried as they indicate programming errors or unrecoverable states. */
        object NonRetriableExceptions {
            /** Set of exception types that should never be retried. */
            val TYPES: Set<Class<out Throwable>> = setOf<Class<out Throwable>>(
                // Kotlin exceptions
                NullPointerException::class.java,
                ClassCastException::class.java,
                IndexOutOfBoundsException::class.java,
                IllegalArgumentException::class.java,
                IllegalStateException::class.java,
                NoSuchElementException::class.java,
                NumberFormatException::class.java,
                UninitializedPropertyAccessException::class.java,
                TypeCastException::class.java,

                // Security exceptions - should never retry
                SecurityException::class.java,

                // Validation failures - data is wrong, retry won't help
                // Add your custom validation exceptions here if needed
            )

            /**
             * Check if an exception is retriable based on its type.
             * @param exception The exception to check
             * @param retryableExceptions Optional set of explicitly retriable exceptions
             * @return true if the exception should be retried, false otherwise
             */
            fun isRetriable(
                exception: Throwable,
                retryableExceptions: Set<Class<out Throwable>> = emptySet(),
            ): Boolean {
                // If specific retriable exceptions are provided, only those are retriable
                if (retryableExceptions.isNotEmpty()) {
                    return retryableExceptions.any { it.isInstance(exception) }
                }

                // Otherwise, everything except non-retriable exceptions is retriable
                return TYPES.none { it.isInstance(exception) }
            }
        }
    }
}
