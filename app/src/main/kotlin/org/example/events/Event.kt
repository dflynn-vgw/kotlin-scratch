package org.example.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
abstract class Event<T>(
    @Contextual open val id: UUID,
    val type: EventType,
    val streamId: String,
    val streamType: StreamType,
    open val timestamp: Long = System.currentTimeMillis(),
    open val version: Int,
    open val payload: T? = null,
    open val context: Map<String, String> = emptyMap(),
) {
    enum class StreamType { ORDER_STREAM }
    enum class EventType { ORDER_PLACED, ORDER_CONFIRMED, ORDER_CANCELLED, ORDER_MODIFIED }
}
