package org.example.events

import kotlinx.serialization.Serializable
import java.util.UUID

/** Sealed class representing all order-related events */
@Serializable
sealed class OrderEvents(
    id: UUID,
    type: EventType,
    streamId: String,
    streamType: Event.StreamType,
    version: Int,
    payload: Any? = null,
    context: Map<String, Any> = emptyMap(),
    timestamp: Long = System.currentTimeMillis(),
) : Event<Any>(
    id = id,
    type = type,
    streamId = streamId,
    streamType = streamType,
    version = version,
    payload = payload,
    context = context,
    timestamp = timestamp,
) {

    /** Customer has placed a new order */
    data class OrderPlacedEvent(
        override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, Any> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : OrderEvents(
        id = id,
        type = EventType.ORDER_PLACED,
        streamId = "order-${payload.orderId}",
        streamType = StreamType.ORDER_STREAM,
        version = version,
        payload = payload,
        context = context,
        timestamp = timestamp,
    ) {
        @Serializable
        data class Payload(
            val orderId: String,
            val customerId: String,
            val items: List<String>,
            val totalAmount: Double,
            val receivedAt: Long,
        )
    }

    /** Order has been modified */
    data class OrderModifiedEvent(
        override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, Any> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : OrderEvents(
        id = id,
        type = EventType.ORDER_MODIFIED,
        streamId = "order-${payload.orderId}",
        streamType = StreamType.ORDER_STREAM,
        version = version,
        payload = payload,
        context = context,
        timestamp = timestamp,
    ) {
        @Serializable
        data class Payload(
            val orderId: String,
            val modifiedAt: Long,
            val items: List<String>,
            val totalAmount: Double,
        )
    }

    /** Order has been confirmed and accepted for processing */
    data class OrderConfirmedEvent(
        override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, Any> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : OrderEvents(
        id = id,
        type = EventType.ORDER_CONFIRMED,
        streamId = "order-${payload.orderId}",
        streamType = StreamType.ORDER_STREAM,
        version = version,
        payload = payload,
        context = context,
        timestamp = timestamp,
    ) {
        @Serializable
        data class Payload(
            val orderId: String,
            val confirmedAt: Long,
        )
    }

    /** Order has been cancelled */
    data class OrderCancelledEvent(
        override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, Any> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : OrderEvents(
        id = id,
        type = EventType.ORDER_CANCELLED,
        streamId = "order-${payload.orderId}",
        streamType = StreamType.ORDER_STREAM,
        version = version,
        payload = payload,
        context = context,
        timestamp = timestamp,
    ) {
        @Serializable
        data class Payload(
            val orderId: String,
            val cancelledAt: Long,
            val reason: String,
        )
    }
}
