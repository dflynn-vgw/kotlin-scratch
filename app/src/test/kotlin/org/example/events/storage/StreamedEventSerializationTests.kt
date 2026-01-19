package org.example.events.storage

import org.example.common.extensions.fromJSON
import org.example.common.extensions.toJSON
import org.example.events.OrderEvents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for StreamedEvent serialization and deserialization
 * Each test verifies:
 *  - JSON serialization using toJSON() extension
 *  - JSON contains expected discriminator (__type)
 *  - JSON contains key payload fields
 *  - Deserialization using fromJSON<StreamedEvent>() extension
 *  - Correct polymorphic type resolution
 *  - All Event properties preserved (id, version, timestamp, type, streamType, payload, context)
 *  - StreamOffset preservation
 * */
class StreamedEventSerializationTests {
    private val fixedUUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val fixedTimestamp = 1625158800000L
    private val fixedPosition = 42L

    @Test
    fun `should serialize and deserialize OrderPlacedEvent in StreamedEvent`() {
        val event = OrderEvents.OrderPlacedEvent(
            id = fixedUUID,
            version = 1,
            timestamp = fixedTimestamp,
            payload = OrderEvents.OrderPlacedEvent.Payload(
                orderId = "order-123",
                customerId = "customer-456",
                items = listOf("item-1", "item-2"),
                totalAmount = 99.99,
                receivedAt = fixedTimestamp,
            ),
            context = mapOf("source" to "api", "traceId" to "trace-123"),
        )

        val streamedEvent = StreamedEvent.fromEvent(event, fixedPosition)

        // Serialize to JSON
        val json = streamedEvent.toJSON()

        // Verify JSON contains expected fields
        assertNotNull(json)
        assertTrue(json.contains("\"__type\":\"OrderPlacedEvent\""))
        assertTrue(json.contains("\"orderId\":\"order-123\""))
        assertTrue(json.contains("\"position\":$fixedPosition"))

        // Deserialize back
        val deserialized = json.fromJSON<StreamedEvent>()

        // Verify deserialized event
        assertEquals(streamedEvent.offset, deserialized.offset)
        assertTrue(deserialized.event is OrderEvents.OrderPlacedEvent)

        val deserializedEvent = deserialized.event as OrderEvents.OrderPlacedEvent
        assertEquals(event.id, deserializedEvent.id)
        assertEquals(event.version, deserializedEvent.version)
        assertEquals(event.timestamp, deserializedEvent.timestamp)
        assertEquals(event.type, deserializedEvent.type)
        assertEquals(event.streamType, deserializedEvent.streamType)
        assertEquals(event.payload, deserializedEvent.payload)
        assertEquals(event.context, deserializedEvent.context)
    }

    @Test
    fun `should serialize and deserialize OrderModifiedEvent in StreamedEvent`() {
        val event = OrderEvents.OrderModifiedEvent(
            id = fixedUUID,
            version = 2,
            timestamp = fixedTimestamp,
            payload = OrderEvents.OrderModifiedEvent.Payload(
                orderId = "order-123",
                modifiedAt = fixedTimestamp,
                items = listOf("item-1", "item-3"),
                totalAmount = 149.99,
            ),
            context = mapOf("userId" to "user-789"),
        )

        val streamedEvent = StreamedEvent.fromEvent(event, fixedPosition)

        // Serialize to JSON
        val json = streamedEvent.toJSON()

        // Verify JSON contains expected fields
        assertTrue(json.contains("\"__type\":\"OrderModifiedEvent\""))
        assertTrue(json.contains("\"orderId\":\"order-123\""))
        assertTrue(json.contains("\"modifiedAt\":$fixedTimestamp"))

        // Deserialize back
        val deserialized = json.fromJSON<StreamedEvent>()

        // Verify deserialized event
        assertEquals(streamedEvent.offset, deserialized.offset)
        assertTrue(deserialized.event is OrderEvents.OrderModifiedEvent)

        val deserializedEvent = deserialized.event as OrderEvents.OrderModifiedEvent
        assertEquals(event.id, deserializedEvent.id)
        assertEquals(event.version, deserializedEvent.version)
        assertEquals(event.timestamp, deserializedEvent.timestamp)
        assertEquals(event.type, deserializedEvent.type)
        assertEquals(event.payload, deserializedEvent.payload)
        assertEquals(event.context, deserializedEvent.context)
    }

    @Test
    fun `should serialize and deserialize OrderConfirmedEvent in StreamedEvent`() {
        val event = OrderEvents.OrderConfirmedEvent(
            id = fixedUUID,
            version = 3,
            timestamp = fixedTimestamp,
            payload = OrderEvents.OrderConfirmedEvent.Payload(
                orderId = "order-123",
                confirmedAt = fixedTimestamp,
            ),
            context = emptyMap(),
        )

        val streamedEvent = StreamedEvent.fromEvent(event, fixedPosition)

        // Serialize to JSON
        val json = streamedEvent.toJSON()

        // Verify JSON contains expected fields
        assertTrue(json.contains("\"__type\":\"OrderConfirmedEvent\""))
        assertTrue(json.contains("\"orderId\":\"order-123\""))
        assertTrue(json.contains("\"confirmedAt\":$fixedTimestamp"))

        // Deserialize back
        val deserialized = json.fromJSON<StreamedEvent>()

        // Verify deserialized event
        assertEquals(streamedEvent.offset, deserialized.offset)
        assertTrue(deserialized.event is OrderEvents.OrderConfirmedEvent)

        val deserializedEvent = deserialized.event as OrderEvents.OrderConfirmedEvent
        assertEquals(event.id, deserializedEvent.id)
        assertEquals(event.version, deserializedEvent.version)
        assertEquals(event.timestamp, deserializedEvent.timestamp)
        assertEquals(event.type, deserializedEvent.type)
        assertEquals(event.payload, deserializedEvent.payload)
        assertEquals(event.context, deserializedEvent.context)
    }

    @Test
    fun `should serialize and deserialize OrderCancelledEvent in StreamedEvent`() {
        val event = OrderEvents.OrderCancelledEvent(
            id = fixedUUID,
            version = 4,
            timestamp = fixedTimestamp,
            payload = OrderEvents.OrderCancelledEvent.Payload(
                orderId = "order-123",
                cancelledAt = fixedTimestamp,
                reason = "Customer request",
            ),
            context = mapOf("cancelledBy" to "customer-456"),
        )

        val streamedEvent = StreamedEvent.fromEvent(event, fixedPosition)

        // Serialize to JSON
        val json = streamedEvent.toJSON()

        // Verify JSON contains expected fields
        assertTrue(json.contains("\"__type\":\"OrderCancelledEvent\""))
        assertTrue(json.contains("\"orderId\":\"order-123\""))
        assertTrue(json.contains("\"reason\":\"Customer request\""))

        // Deserialize back
        val deserialized = json.fromJSON<StreamedEvent>()

        // Verify deserialized event
        assertEquals(streamedEvent.offset, deserialized.offset)
        assertTrue(deserialized.event is OrderEvents.OrderCancelledEvent)

        val deserializedEvent = deserialized.event as OrderEvents.OrderCancelledEvent
        assertEquals(event.id, deserializedEvent.id)
        assertEquals(event.version, deserializedEvent.version)
        assertEquals(event.timestamp, deserializedEvent.timestamp)
        assertEquals(event.type, deserializedEvent.type)
        assertEquals(event.payload, deserializedEvent.payload)
        assertEquals(event.context, deserializedEvent.context)
    }

    @Test
    fun `should handle empty context map in serialization`() {
        val event = OrderEvents.OrderPlacedEvent(
            id = fixedUUID,
            version = 1,
            timestamp = fixedTimestamp,
            payload = OrderEvents.OrderPlacedEvent.Payload(
                orderId = "order-999",
                customerId = "customer-999",
                items = listOf("item-1"),
                totalAmount = 10.00,
                receivedAt = fixedTimestamp,
            ),
            context = emptyMap(),
        )

        val streamedEvent = StreamedEvent(
            event = event,
            offset = StreamOffset(1L),
        )

        val json = streamedEvent.toJSON()
        val deserialized = json.fromJSON<StreamedEvent>()

        val deserializedEvent = deserialized.event as OrderEvents.OrderPlacedEvent
        assertEquals(emptyMap<String, String>(), deserializedEvent.context)
    }

    @Test
    fun `should preserve all Event fields through serialization roundtrip`() {
        val event = OrderEvents.OrderPlacedEvent(
            id = fixedUUID,
            version = 5,
            timestamp = fixedTimestamp,
            payload = OrderEvents.OrderPlacedEvent.Payload(
                orderId = "order-special",
                customerId = "customer-special",
                items = listOf("premium-item-1", "premium-item-2", "premium-item-3"),
                totalAmount = 299.99,
                receivedAt = fixedTimestamp,
            ),
            context = mapOf("channel" to "mobile", "promotionCode" to "SAVE20"),
        )

        val streamedEvent = StreamedEvent(
            event = event,
            offset = StreamOffset(100L),
        )

        // Serialize and deserialize
        val json = streamedEvent.toJSON()
        val deserialized = json.fromJSON<StreamedEvent>()
        val deserializedEvent = deserialized.event as OrderEvents.OrderPlacedEvent

        // Verify all properties are preserved
        assertEquals(event.id, deserializedEvent.id)
        assertEquals(event.type, deserializedEvent.type)
        assertEquals(event.streamType, deserializedEvent.streamType)
        assertEquals(event.version, deserializedEvent.version)
        assertEquals(event.timestamp, deserializedEvent.timestamp)
        assertEquals(event.payload.orderId, deserializedEvent.payload.orderId)
        assertEquals(event.payload.customerId, deserializedEvent.payload.customerId)
        assertEquals(event.payload.items, deserializedEvent.payload.items)
        assertEquals(event.payload.totalAmount, deserializedEvent.payload.totalAmount)
        assertEquals(event.payload.receivedAt, deserializedEvent.payload.receivedAt)
        assertEquals(event.context, deserializedEvent.context)
        assertEquals(event.streamId, deserializedEvent.streamId)
    }

    @Test
    fun `should correctly serialize polymorphic Event type discriminator`() {
        val events = listOf(
            OrderEvents.OrderPlacedEvent(
                id = fixedUUID,
                version = 1,
                timestamp = fixedTimestamp,
                payload = OrderEvents.OrderPlacedEvent.Payload(
                    orderId = "order-1",
                    customerId = "customer-1",
                    items = listOf("item-1"),
                    totalAmount = 10.0,
                    receivedAt = fixedTimestamp,
                ),
            ),
            OrderEvents.OrderModifiedEvent(
                id = fixedUUID,
                version = 2,
                timestamp = fixedTimestamp,
                payload = OrderEvents.OrderModifiedEvent.Payload(
                    orderId = "order-1",
                    modifiedAt = fixedTimestamp,
                    items = listOf("item-1", "item-2"),
                    totalAmount = 20.0,
                ),
            ),
            OrderEvents.OrderConfirmedEvent(
                id = fixedUUID,
                version = 3,
                timestamp = fixedTimestamp,
                payload = OrderEvents.OrderConfirmedEvent.Payload(
                    orderId = "order-1",
                    confirmedAt = fixedTimestamp,
                ),
            ),
            OrderEvents.OrderCancelledEvent(
                id = fixedUUID,
                version = 4,
                timestamp = fixedTimestamp,
                payload = OrderEvents.OrderCancelledEvent.Payload(
                    orderId = "order-1",
                    cancelledAt = fixedTimestamp,
                    reason = "Test",
                ),
            ),
        )

        val expectedDiscriminators = listOf(
            "OrderPlacedEvent",
            "OrderModifiedEvent",
            "OrderConfirmedEvent",
            "OrderCancelledEvent",
        )

        events.forEachIndexed { index, event ->
            val streamedEvent = StreamedEvent(event, StreamOffset(index.toLong()))
            val json = streamedEvent.toJSON()

            // Verify the correct type discriminator is present
            assertTrue(json.contains("\"__type\":\"${expectedDiscriminators[index]}\""))

            // Verify deserialization produces the correct type
            val deserialized = json.fromJSON<StreamedEvent>()
            assertEquals(event::class, deserialized.event::class)
        }
    }
}
