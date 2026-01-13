package org.example.retry

import kotlinx.serialization.Serializable
import org.example.events.storage.StreamedEvent
import java.time.Instant

/** Dead Letter Queue entry containing failed event and error details. */
@Serializable
data class DeadLetterEntry(
    val streamedEvent: StreamedEvent,
    val failureReason: String,
    val exceptionType: String,
    val stackTrace: String,
    val attemptCount: Int,
    val retriable: Boolean,
    val enqueuedAt: Instant = Instant.now(),
)
