package org.example.retry

import kotlinx.serialization.Serializable
import org.example.common.extensions.toJSON
import org.example.events.storage.StreamedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Collections

/**
 * Exception thrown when DLQ circuit breaker threshold is exceeded.
 *
 * @property currentRate The current enqueue rate (events per second)
 * @property threshold The configured threshold that was exceeded
 */
class DlqThresholdExceededException(
    val currentRate: Double,
    val threshold: Double,
) : RuntimeException(
    "DLQ circuit breaker threshold exceeded: current rate $currentRate/sec exceeds threshold $threshold/sec",
)

/**
 * Service for managing the Dead Letter Queue (DLQ).
 *
 * Events that fail after exhausting all retry attempts are sent to the DLQ
 * for later investigation and potential reprocessing.
 */
open class DeadLetterQueue(
    private val options: Options,
    private val logger: Logger = LoggerFactory.getLogger(DeadLetterQueue::class.java),
) {
    // Track enqueue timestamps for rate calculation (thread-safe list)
    private val enqueueTimestamps: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    /** Enqueue a failed event into the Dead Letter Queue.
     *
     * @param entry The DeadLetterEntry containing event and failure details
     * @return EnqueueOutcome indicating success or circuit breaker trip
     */
    open fun enqueue(entry: Entry): EnqueueOutcome {
        if (!options.enabled) {
            logger.warn(
                "DLQ is disabled - event at position {} will be dropped. Details: {}",
                entry.streamedEvent.offset.position,
                entry.streamedEvent.toJSON(),
            )
            return EnqueueOutcome.Success(currentRate = 0.0)
        }

        // Check circuit breaker before attempting to enqueue
        val currentRate = getCurrentEnqueueRate()
        if (options.circuitBreaker.enabled && currentRate >= options.circuitBreaker.rateThreshold) {
            val exception = DlqThresholdExceededException(
                currentRate = currentRate,
                threshold = options.circuitBreaker.rateThreshold,
            )
            logger.error(
                "Circuit breaker OPEN - DLQ enqueue rate ({} events/sec) exceeds threshold ({} events/sec). Event at position {} rejected.",
                String.format("%.2f", currentRate),
                options.circuitBreaker.rateThreshold,
                entry.streamedEvent.offset.position,
            )
            return EnqueueOutcome.Failure(
                currentRate = currentRate,
                exception = exception,
            )
        }

        try {
            when (options.type) {
                Options.StorageType.FILE -> writeToFile(entry)
                Options.StorageType.LOG -> writeToLog(entry)
                // StorageType.DATABASE -> writeToDatabase(entry) // Future implementation
                // StorageType.KAFKA -> writeToKafka(entry)       // Future implementation
            }

            // Record successful enqueue for rate tracking
            recordEnqueue()

            logger.info(
                "Event at position {} sent to DLQ (type: {}, current rate: {}/sec)",
                entry.streamedEvent.offset.position,
                options.type,
                String.format("%.2f", getCurrentEnqueueRate()),
            )

            return EnqueueOutcome.Success(currentRate = getCurrentEnqueueRate())
        } catch (e: Exception) {
            logger.error(
                "Failed to enqueue event at position {} to DLQ",
                entry.streamedEvent.offset.position,
                e,
            )
            return EnqueueOutcome.Failure(
                currentRate = getCurrentEnqueueRate(),
                exception = e,
            )
        }
    }

    private fun writeToFile(entry: Entry) {
        val dlqPath = Path.of(options.filePath)
        Files.createDirectories(dlqPath.parent)

        val json = try {
            entry.toJSON()
        } catch (e: Exception) {
            // Fallback to simple string representation if serialization fails
            logger.warn("Failed to serialize DLQ entry, using fallback representation: {}", e.message)
            """{"position":${entry.streamedEvent.offset.position},"failureReason":"${entry.failureReason}","attemptCount":${entry.attemptCount}}"""
        }
        Files.writeString(dlqPath, "$json\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun writeToLog(entry: Entry) {
        logger.error(
            """
            |DLQ Entry:
            |  Event Position: ${entry.streamedEvent.offset.position}
            |  Event Type: ${entry.streamedEvent.event.type}
            |  Failure Reason: ${entry.failureReason}
            |  Exception Type: ${entry.exceptionType}
            |  Attempt Count: ${entry.attemptCount}
            |  Retriable: ${entry.retriable}
            |  Status: ${entry.status}
            |  Enqueued At: ${entry.enqueuedAt}
            |  Enqueued By: ${entry.enqueuedBy}
            |  Stack Trace: ${entry.stackTrace}
            """.trimMargin(),
        )
    }

    /** Record an enqueue event for rate tracking. */
    private fun recordEnqueue() {
        val now = System.currentTimeMillis()
        enqueueTimestamps.add(now)

        // Remove timestamps outside the window
        val windowStart = now - options.circuitBreaker.windowMillis
        synchronized(enqueueTimestamps) {
            enqueueTimestamps.removeAll { it < windowStart }
        }
    }

    /** Calculate current enqueue rate (events per second). */
    private fun getCurrentEnqueueRate(): Double {
        val now = System.currentTimeMillis()
        val windowStart = now - options.circuitBreaker.windowMillis

        // Synchronized block for compound operation (remove + calculate)
        synchronized(enqueueTimestamps) {
            // Remove stale timestamps
            enqueueTimestamps.removeAll { it < windowStart }

            // Calculate rate: events per second
            val windowSeconds = options.circuitBreaker.windowMillis / 1000.0
            return enqueueTimestamps.size / windowSeconds
        }
    }

    /** Get current enqueue rate without modifying state (for external callers). */
    fun getEnqueueRate(): Double = getCurrentEnqueueRate()

    /** Configuration for DLQ behavior. */
    data class Options(
        val enabled: Boolean = true,
        val type: StorageType = StorageType.FILE,
        val filePath: String = "dlq/failed-events.jsonl",
        val replayMode: ReplayMode = ReplayMode.MANUAL_REVIEW,
        val circuitBreaker: CircuitBreakerOptions = CircuitBreakerOptions(),
    ) {
        enum class StorageType {
            FILE,
            LOG,
            // DATABASE, // Future implementation for production use
            // KAFKA,   // Future implementation for production use
        }

        /**
         * Determines how DLQ entries are initially marked and whether they require manual review.
         *
         * - MANUAL_REVIEW: Entries start with PENDING status, requiring engineer intervention to mark as REPLAY
         * - AUTOMATIC_REPLAY: Entries start with REPLAY status, ready for immediate reprocessing
         */
        enum class ReplayMode {
            /** Entries require manual review before replay (initial status: PENDING) */
            MANUAL_REVIEW,

            /** Entries are automatically marked for replay (initial status: REPLAY) */
            AUTOMATIC_REPLAY,
        }

        /**
         * Circuit breaker configuration to prevent overwhelming the DLQ.
         *
         * When the enqueue rate exceeds the threshold, the circuit breaker opens and
         * new enqueue attempts fail, allowing Process Managers to halt processing.
         */
        data class CircuitBreakerOptions(
            /** Enable circuit breaker behavior */
            val enabled: Boolean = false,

            /** Maximum allowed enqueue rate (events per second) */
            val rateThreshold: Double = 10.0,

            /** Time window for rate calculation (milliseconds) */
            val windowMillis: Long = 60_000, // 1 minute default
        )
    }

    /** Outcome of an enqueue operation. */
    sealed class EnqueueOutcome {
        abstract val currentRate: Double

        /** Enqueue succeeded. */
        data class Success(override val currentRate: Double) : EnqueueOutcome()

        /**
         * Enqueue failed due to an exception.
         *
         * Check for [DlqThresholdExceededException] to detect circuit breaker trips.
         */
        data class Failure(
            override val currentRate: Double,
            val exception: Throwable,
        ) : EnqueueOutcome() {
            /** Check if this failure is due to circuit breaker opening. */
            fun isCircuitBreakerOpen(): Boolean = exception is DlqThresholdExceededException
        }
    }

    /** Dead Letter Queue entry containing failed event and error details. */
    @Serializable
    data class Entry(
        val streamedEvent: StreamedEvent,
        val failureReason: String,
        val exceptionType: String,
        val stackTrace: String,
        val attemptCount: Int,
        val retriable: Boolean,
        val enqueuedAt: Long,
        val enqueuedBy: String,
        val status: Status = Status.PENDING,
    ) {
        /**
         * Status of a DLQ entry in its lifecycle.
         *
         * Typical flow:
         * 1. Entry created with PENDING (manual review) or REPLAY (automatic) based on ReplayMode
         * 2. Engineer marks PENDING entries as REPLAY after investigation
         * 3. Reprocessing system picks up REPLAY entries
         * 4. After successful replay, marked as RESOLVED
         * 5. If replay fails or entry is invalid, marked as FAILED or DISCARDED
         */
        @Serializable
        enum class Status {
            /** Awaiting manual review/investigation */
            PENDING,

            /** Ready for reprocessing */
            REPLAY,

            /** Successfully reprocessed */
            RESOLVED,

            /** Reprocessing failed */
            FAILED,

            /** Entry discarded (not worth reprocessing) */
            DISCARDED,
        }
    }
}
