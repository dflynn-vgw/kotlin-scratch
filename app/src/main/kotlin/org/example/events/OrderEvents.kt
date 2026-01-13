package org.example.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

/** Sealed class representing all order-related events */
sealed interface OrderEvents {

    /** Customer has placed a new order */
    data class OrderPlacedEvent(
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderPlacedEvent.Payload>(
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
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderModifiedEvent.Payload>(
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
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderConfirmedEvent.Payload>(
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
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderCancelledEvent.Payload>(
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
