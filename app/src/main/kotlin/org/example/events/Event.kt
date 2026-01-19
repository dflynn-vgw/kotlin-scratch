package org.example.events

import kotlinx.serialization.Contextual
import java.util.UUID

abstract class Event<T> {
    @Contextual abstract val id: UUID
    abstract val type: EventType
    abstract val streamId: String
    abstract val streamType: StreamType
    abstract val timestamp: Long
    abstract val version: Int
    abstract val payload: T?
    abstract val context: Map<String, String>

    enum class StreamType { ORDER_STREAM }
    enum class EventType { ORDER_PLACED, ORDER_CONFIRMED, ORDER_CANCELLED, ORDER_MODIFIED }
}
