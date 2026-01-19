package org.example.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Sealed class representing all order-related events */
sealed interface OrderEvents {

    /** Customer has placed a new order */
    @Serializable
    @SerialName("OrderPlacedEvent")
    data class OrderPlacedEvent(
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderPlacedEvent.Payload>() {
        override val type: EventType = EventType.ORDER_PLACED
        override val streamId: String get() = "order-${payload.orderId}"
        override val streamType: StreamType = StreamType.ORDER_STREAM

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
    @Serializable
    @SerialName("OrderModifiedEvent")
    data class OrderModifiedEvent(
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderModifiedEvent.Payload>() {
        override val type: EventType = EventType.ORDER_MODIFIED
        override val streamId: String get() = "order-${payload.orderId}"
        override val streamType: StreamType = StreamType.ORDER_STREAM

        @Serializable
        data class Payload(
            val orderId: String,
            val modifiedAt: Long,
            val items: List<String>,
            val totalAmount: Double,
        )
    }

    /** Order has been confirmed and accepted for processing */
    @Serializable
    @SerialName("OrderConfirmedEvent")
    data class OrderConfirmedEvent(
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderConfirmedEvent.Payload>() {
        override val type: EventType = EventType.ORDER_CONFIRMED
        override val streamId: String get() = "order-${payload.orderId}"
        override val streamType: StreamType = StreamType.ORDER_STREAM

        @Serializable
        data class Payload(
            val orderId: String,
            val confirmedAt: Long,
        )
    }

    /** Order has been cancelled */
    @Serializable
    @SerialName("OrderCancelledEvent")
    data class OrderCancelledEvent(
        @Contextual override val id: UUID = UUID.randomUUID(),
        override val version: Int,
        override val payload: Payload,
        override val context: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Event<OrderCancelledEvent.Payload>() {
        override val type: EventType = EventType.ORDER_CANCELLED
        override val streamId: String get() = "order-${payload.orderId}"
        override val streamType: StreamType = StreamType.ORDER_STREAM

        @Serializable
        data class Payload(
            val orderId: String,
            val cancelledAt: Long,
            val reason: String,
        )
    }
}
