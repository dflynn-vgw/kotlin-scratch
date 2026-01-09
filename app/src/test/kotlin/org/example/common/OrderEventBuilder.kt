package org.example.common

import org.example.events.Event
import org.example.events.OrderEvents
import java.util.UUID

/** Builder for order-related events for testing purposes */
class OrderEventBuilder(
    /** Fixed UUID string for all events built by this builder */
    id: String,
    /** Fixed timestamp for all events built by this builder */
    val timestamp: Long,
) {
    private val id: UUID = UUID.fromString(id)

    /** Build a list of events of the specified types */
    fun build(orderId: String, customerId: String, eventTypes: List<Event.EventType>, startingVersion: Int = 1): List<Event<Any>> = eventTypes.mapIndexed { index, eventType ->
        buildEvent(orderId, customerId, eventType, startingVersion + index)
    }

    /** Build an event of the specified type */
    private fun buildEvent(orderId: String, customerId: String, eventType: Event.EventType, version: Int): Event<Any> = when (eventType) {
        Event.EventType.ORDER_PLACED -> OrderEvents.OrderPlacedEvent(
            id = id,
            version = version,
            timestamp = timestamp,
            payload = OrderEvents.OrderPlacedEvent.Payload(
                orderId = orderId,
                customerId = customerId,
                items = listOf("Item1", "Item2"),
                totalAmount = 100.0,
                receivedAt = timestamp,
            ),
        )

        Event.EventType.ORDER_CONFIRMED -> OrderEvents.OrderConfirmedEvent(
            id = id,
            version = version,
            timestamp = timestamp,
            payload = OrderEvents.OrderConfirmedEvent.Payload(
                orderId = orderId,
                confirmedAt = timestamp,
            ),
        )

        Event.EventType.ORDER_CANCELLED -> OrderEvents.OrderCancelledEvent(
            id = id,
            version = version,
            timestamp = timestamp,
            payload = OrderEvents.OrderCancelledEvent.Payload(
                orderId = orderId,
                cancelledAt = timestamp,
                reason = "Customer Request",
            ),
        )

        Event.EventType.ORDER_MODIFIED -> OrderEvents.OrderModifiedEvent(
            id = id,
            version = version,
            timestamp = timestamp,
            payload = OrderEvents.OrderModifiedEvent.Payload(
                orderId = orderId,
                modifiedAt = timestamp,
                items = listOf("Item1", "Item2"),
                totalAmount = 100.0,
            ),
        )
    }
}
