package org.example.retry

import org.example.common.extensions.toJSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.jvm.javaClass

/**
 * Service for managing the Dead Letter Queue (DLQ).
 *
 * Events that fail after exhausting all retry attempts are sent to the DLQ
 * for later investigation and potential reprocessing.
 */
@Service
class DeadLetterQueueService(
    private val options: DeadLetterQueueOptions,
    private val logger: Logger = LoggerFactory.getLogger(DeadLetterQueueService::class.java),
) {

    /**
     * Send a failed event to the DLQ.
     *
     * @param failure The retry failure containing the event and error details
     */
    suspend fun enqueue(failure: RetryOutcome.Failure) {
        if (!options.enabled) {
            logger.warn(
                "DLQ is disabled - event at position {} will be dropped",
                failure.streamedEvent.offset.position,
            )
            return
        }

        try {
            val dlqEntry = DeadLetterEntry(
                streamedEvent = failure.streamedEvent,
                failureReason = failure.lastException.message ?: "Unknown error",
                exceptionType = failure.lastException::class.java.name,
                stackTrace = failure.lastException.stackTraceToString(),
                attemptCount = failure.attemptCount,
                retriable = failure.retriable,
                enqueuedAt = Instant.now(),
            )

            when (options.type) {
                DeadLetterQueueOptions.StorageType.FILE -> writeToFile(dlqEntry)
                DeadLetterQueueOptions.StorageType.LOG -> writeToLog(dlqEntry)
                // StorageType.DATABASE -> writeToDatabase(dlqEntry) // Future implementation
                // StorageType.KAFKA -> writeToKafka(dlqEntry)         // Future implementation
            }

            logger.info(
                "Event at position {} sent to DLQ (type: {})",
                failure.streamedEvent.offset.position,
                options.type,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to enqueue event at position {} to DLQ",
                failure.streamedEvent.offset.position,
                e,
            )
        }
    }

    private fun writeToFile(entry: DeadLetterEntry) {
        val dlqPath = Path.of(options.filePath)
        Files.createDirectories(dlqPath.parent)

        val json = entry.toJSON()
        Files.writeString(dlqPath, "$json\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun writeToLog(entry: DeadLetterEntry) {
        logger.error(
            """
            |DLQ Entry:
            |  Event Position: ${entry.streamedEvent.offset.position}
            |  Event Type: ${entry.streamedEvent.event.type}
            |  Failure Reason: ${entry.failureReason}
            |  Exception Type: ${entry.exceptionType}
            |  Attempt Count: ${entry.attemptCount}
            |  Retriable: ${entry.retriable}
            |  Enqueued At: ${entry.enqueuedAt}
            |  Stack Trace: ${entry.stackTrace}
            """.trimMargin(),
        )
    }
}
