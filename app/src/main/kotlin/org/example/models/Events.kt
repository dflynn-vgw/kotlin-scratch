package org.example.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed class Events(
    val id: UUID = UUID.randomUUID(),
    val type: EventType,
    val streamId: String,
    val streamType: StreamType,
    val version: Int = 0,
) {
    enum class StreamType { ORDER_STREAM }
    enum class EventType { ORDER_PLACED, ORDER_CONFIRMED, ORDER_CANCELLED, ORDER_MODIFIED }

    /** Customer has placed a new order */
    data class OrderPlacedEvent(
        val orderId: String,
        val customerId: String,
        val items: List<String>,
        val totalAmount: Double,
        val receivedAt: Long,
    ) : Events(
        streamId = "order-$orderId",
        streamType = StreamType.ORDER_STREAM,
        type = EventType.ORDER_PLACED,
    )

    data class OrderModifiedEvent(
        val orderId: String,
        val modifiedAt: Long,
        val items: List<String>,
        val totalAmount: Double,
    ) : Events(
        streamId = "order-$orderId",
        streamType = StreamType.ORDER_STREAM,
        type = EventType.ORDER_MODIFIED,
    )

    /** Order has been confirmed and accepted for processing */
    data class OrderConfirmedEvent(
        val orderId: String,
        val confirmedAt: Long,
    ) : Events(
        streamId = "order-$orderId",
        streamType = StreamType.ORDER_STREAM,
        type = EventType.ORDER_CONFIRMED,
    )

    /** Order has been cancelled */
    data class OrderCancelledEvent(
        val orderId: String,
        val cancelledAt: Long,
        val reason: String,
    ) : Events(
        streamId = "order-$orderId",
        streamType = StreamType.ORDER_STREAM,
        type = EventType.ORDER_CANCELLED,
    )
}
