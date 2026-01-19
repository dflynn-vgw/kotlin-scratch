package org.example.events.storage

import kotlinx.serialization.Contextual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import org.example.events.Event

/**
 * Represents an event with its position in the stream.
 * Enables consumers to be aware of event positions for idempotent processing
 * and explicit bookmark management.
 */
@Serializable
data class StreamedEvent(
    @Polymorphic val event: Event<*>,
    val offset: StreamOffset,
)
