package org.example.events.storage

import org.example.events.Event

/**
 * Represents an event with its position in the stream.
 * Enables consumers to be aware of event positions for idempotent processing
 * and explicit bookmark management.
 */
data class StreamedEvent(
    val event: Event<Any>,
    val offset: StreamOffset,
)
