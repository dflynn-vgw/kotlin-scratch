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
) {
    companion object {
        /**
         * Creates a StreamedEvent from a given Event and position.
         *
         * @param T The type of the event payload.
         * @param event The event to be wrapped.
         * @param position The position of the event in the stream.
         * @return A StreamedEvent containing the event and its offset.
         */
        fun <T> fromEvent(event: Event<T>, position: Long): StreamedEvent = StreamedEvent(
            event = event,
            offset = StreamOffset(position = position),
        )
    }
}
