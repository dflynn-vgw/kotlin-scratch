package org.example.retry

import kotlinx.serialization.Serializable
import org.example.common.extensions.toJSON
import org.example.events.storage.StreamedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/**
 * Service for managing the Dead Letter Queue (DLQ).
 *
 * Events that fail after exhausting all retry attempts are sent to the DLQ
 * for later investigation and potential reprocessing.
 */
class DeadLetterQueue(
    private val options: Options,
    private val logger: Logger = LoggerFactory.getLogger(DeadLetterQueue::class.java),
) {

    /** Enqueue a failed event into the Dead Letter Queue.
     *
     * @param entry The DeadLetterEntry containing event and failure details
     */
    fun enqueue(entry: Entry) {
        if (!options.enabled) {
            logger.warn(
                "DLQ is disabled - event at position {} will be dropped. Details: {}",
                entry.streamedEvent.offset.position,
                entry.streamedEvent.toJSON(),
            )
            return
        }

        try {
            val dlqEntry = entry.copy(enqueuedAt = Instant.now())

            when (options.type) {
                Options.StorageType.FILE -> writeToFile(dlqEntry)
                Options.StorageType.LOG -> writeToLog(dlqEntry)
                // StorageType.DATABASE -> writeToDatabase(dlqEntry) // Future implementation
                // StorageType.KAFKA -> writeToKafka(dlqEntry)         // Future implementation
            }

            logger.info(
                "Event at position {} sent to DLQ (type: {})",
                entry.streamedEvent.offset.position,
                options.type,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to enqueue event at position {} to DLQ",
                entry.streamedEvent.offset.position,
                e,
            )
        }
    }

    private fun writeToFile(entry: Entry) {
        val dlqPath = Path.of(options.filePath)
        Files.createDirectories(dlqPath.parent)

        val json = entry.toJSON()
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
            |  Enqueued At: ${entry.enqueuedAt}
            |  Stack Trace: ${entry.stackTrace}
            """.trimMargin(),
        )
    }

    /** Configuration for DLQ behavior. */
    data class Options(
        val enabled: Boolean = true,
        val type: StorageType = StorageType.FILE,
        val filePath: String = "dlq/failed-events.jsonl",
    ) {
        enum class StorageType {
            FILE,
            LOG,
            // DATABASE, // Future implementation for production use
            // KAFKA,   // Future implementation for production use
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
        val enqueuedAt: Instant = Instant.now(),
    )
}
