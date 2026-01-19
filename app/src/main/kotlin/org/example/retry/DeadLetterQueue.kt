package org.example.retry

import kotlinx.serialization.Serializable
import org.example.common.extensions.toJSON
import org.example.events.storage.StreamedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

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

    /** Enqueue a failed event into the Dead Letter Queue.
     *
     * @param entry The DeadLetterEntry containing event and failure details
     */
    open fun enqueue(entry: Entry) {
        if (!options.enabled) {
            logger.warn(
                "DLQ is disabled - event at position {} will be dropped. Details: {}",
                entry.streamedEvent.offset.position,
                entry.streamedEvent.toJSON(),
            )
            return
        }

        try {
            when (options.type) {
                Options.StorageType.FILE -> writeToFile(entry)
                Options.StorageType.LOG -> writeToLog(entry)
                // StorageType.DATABASE -> writeToDatabase(entry) // Future implementation
                // StorageType.KAFKA -> writeToKafka(entry)       // Future implementation
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

    /** Configuration for DLQ behavior. */
    data class Options(
        val enabled: Boolean = true,
        val type: StorageType = StorageType.FILE,
        val filePath: String = "dlq/failed-events.jsonl",
        val replayMode: ReplayMode = ReplayMode.MANUAL_REVIEW,
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
