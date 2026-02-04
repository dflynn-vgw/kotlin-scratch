package org.example.events

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.negativeLong
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.example.events.storage.Bookmark
import org.example.events.storage.StreamOffset
import org.example.events.storage.StreamedEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Property-based tests for event models using Kotest.
 *
 * Property testing generates hundreds of random inputs to verify invariants
 * hold for all possible values, catching edge cases that example-based tests miss.
 */
class EventPropertyTests {

    // ===========================================
    // Custom Generators (Arbs) for domain types
    // ===========================================

    /** Generator for valid order IDs (non-empty alphanumeric strings) */
    private val orderIdArb: Arb<String> = Arb.string(minSize = 1, maxSize = 20)
        .map { it.filter { c -> c.isLetterOrDigit() }.ifEmpty { "ORD1" } }

    /** Generator for customer IDs */
    private val customerIdArb: Arb<String> = Arb.string(minSize = 1, maxSize = 20)
        .map { "CUST${it.filter { c -> c.isLetterOrDigit() }.take(10)}" }

    /** Generator for order items */
    private val itemsArb: Arb<List<String>> = Arb.list(Arb.string(1..50), 1..10)

    /** Generator for monetary amounts (positive doubles, excluding NaN/Infinity) */
    private val amountArb: Arb<Double> = Arb.double(min = 0.01, max = 99999.99)
        .filter { it.isFinite() }

    /** Generator for timestamps (positive longs representing millis) */
    private val timestampArb: Arb<Long> = Arb.long(min = 0L, max = Long.MAX_VALUE / 2)

    /** Generator for event versions (positive ints) */
    private val versionArb: Arb<Int> = Arb.positiveInt(max = 1000)

    /** Generator for context maps */
    private val contextArb: Arb<Map<String, String>> = Arb.map(
        Arb.string(1..20),
        Arb.string(1..50),
        minSize = 0,
        maxSize = 5,
    )

    /** Generator for OrderPlacedEvent.Payload */
    private val orderPlacedPayloadArb: Arb<OrderEvents.OrderPlacedEvent.Payload> = arbitrary {
        OrderEvents.OrderPlacedEvent.Payload(
            orderId = orderIdArb.bind(),
            customerId = customerIdArb.bind(),
            items = itemsArb.bind(),
            totalAmount = amountArb.bind(),
            receivedAt = timestampArb.bind(),
        )
    }

    /** Generator for OrderPlacedEvent */
    private val orderPlacedEventArb: Arb<OrderEvents.OrderPlacedEvent> = arbitrary {
        OrderEvents.OrderPlacedEvent(
            id = Arb.uuid().bind(),
            version = versionArb.bind(),
            payload = orderPlacedPayloadArb.bind(),
            context = contextArb.bind(),
            timestamp = timestampArb.bind(),
        )
    }

    /** Generator for OrderCancelledEvent.Payload */
    private val orderCancelledPayloadArb: Arb<OrderEvents.OrderCancelledEvent.Payload> = arbitrary {
        OrderEvents.OrderCancelledEvent.Payload(
            orderId = orderIdArb.bind(),
            cancelledAt = timestampArb.bind(),
            reason = Arb.string(1..100).bind(),
        )
    }

    /** Generator for OrderCancelledEvent */
    private val orderCancelledEventArb: Arb<OrderEvents.OrderCancelledEvent> = arbitrary {
        OrderEvents.OrderCancelledEvent(
            id = Arb.uuid().bind(),
            version = versionArb.bind(),
            payload = orderCancelledPayloadArb.bind(),
            context = contextArb.bind(),
            timestamp = timestampArb.bind(),
        )
    }

    // ===========================================
    // StreamOffset Property Tests
    // ===========================================

    @Test
    fun `StreamOffset accepts any non-negative position`(): Unit = runBlocking {
        forAll(Arb.long(min = 0L, max = Long.MAX_VALUE)) { position ->
            val offset = StreamOffset(position)
            offset.position == position
        }
    }

    @Test
    fun `StreamOffset rejects negative positions`(): Unit = runBlocking {
        checkAll(Arb.negativeLong()) { negativePosition ->
            assertThrows<IllegalArgumentException> {
                StreamOffset(negativePosition)
            }
        }
    }

    @Test
    fun `StreamOffset equality is based on position`(): Unit = runBlocking {
        forAll(Arb.long(min = 0L, max = Long.MAX_VALUE)) { position ->
            StreamOffset(position) == StreamOffset(position)
        }
    }

    // ===========================================
    // Bookmark Property Tests
    // ===========================================

    @Test
    fun `Bookmark preserves all provided values`(): Unit = runBlocking {
        forAll(
            Arb.string(1..50),
            Arb.long(min = 0L),
            timestampArb,
        ) { name, position, updatedAt ->
            val bookmark = Bookmark(name, position, updatedAt)
            bookmark.name == name && bookmark.position == position && bookmark.updatedAt == updatedAt
        }
    }

    @Test
    fun `Bookmark equality is based on all fields`(): Unit = runBlocking {
        forAll(
            Arb.string(1..50),
            Arb.long(min = 0L),
            timestampArb,
        ) { name, position, updatedAt ->
            Bookmark(name, position, updatedAt) == Bookmark(name, position, updatedAt)
        }
    }

    // ===========================================
    // OrderEvents Property Tests
    // ===========================================

    @Test
    fun `OrderPlacedEvent streamId always follows order-{orderId} format`(): Unit = runBlocking {
        forAll(orderPlacedEventArb) { event ->
            event.streamId == "order-${event.payload.orderId}"
        }
    }

    @Test
    fun `OrderPlacedEvent type is always ORDER_PLACED`(): Unit = runBlocking {
        forAll(orderPlacedEventArb) { event ->
            event.type == Event.EventType.ORDER_PLACED
        }
    }

    @Test
    fun `OrderPlacedEvent streamType is always ORDER_STREAM`(): Unit = runBlocking {
        forAll(orderPlacedEventArb) { event ->
            event.streamType == Event.StreamType.ORDER_STREAM
        }
    }

    @Test
    fun `OrderCancelledEvent streamId always follows order-{orderId} format`(): Unit = runBlocking {
        forAll(orderCancelledEventArb) { event ->
            event.streamId == "order-${event.payload.orderId}"
        }
    }

    @Test
    fun `OrderCancelledEvent type is always ORDER_CANCELLED`(): Unit = runBlocking {
        forAll(orderCancelledEventArb) { event ->
            event.type == Event.EventType.ORDER_CANCELLED
        }
    }

    @Test
    fun `OrderPlacedEvent preserves all payload fields`(): Unit = runBlocking {
        forAll(orderPlacedPayloadArb, versionArb) { payload, version ->
            val event = OrderEvents.OrderPlacedEvent(version = version, payload = payload)
            event.payload.orderId == payload.orderId &&
                event.payload.customerId == payload.customerId &&
                event.payload.items == payload.items &&
                event.payload.totalAmount == payload.totalAmount &&
                event.payload.receivedAt == payload.receivedAt
        }
    }

    @Test
    fun `OrderPlacedEvent totalAmount is always positive`(): Unit = runBlocking {
        forAll(orderPlacedEventArb) { event ->
            event.payload.totalAmount > 0
        }
    }

    @Test
    fun `OrderPlacedEvent items list is never empty`(): Unit = runBlocking {
        forAll(orderPlacedEventArb) { event ->
            event.payload.items.isNotEmpty()
        }
    }

    // ===========================================
    // StreamedEvent Property Tests
    // ===========================================

    @Test
    fun `StreamedEvent fromEvent preserves event and position`(): Unit = runBlocking {
        forAll(orderPlacedEventArb, Arb.long(min = 0L, max = Long.MAX_VALUE)) { event, position ->
            val streamed = StreamedEvent.fromEvent(event, position)
            streamed.event == event && streamed.offset.position == position
        }
    }

    @Test
    fun `StreamedEvent offset position matches factory input`(): Unit = runBlocking {
        forAll(orderCancelledEventArb, Arb.long(min = 0L, max = Long.MAX_VALUE)) { event, position ->
            val streamed = StreamedEvent.fromEvent(event, position)
            streamed.offset == StreamOffset(position)
        }
    }

    @Test
    fun `StreamedEvent equality includes both event and offset`(): Unit = runBlocking {
        forAll(orderPlacedEventArb, Arb.long(min = 0L, max = Long.MAX_VALUE)) { event, position ->
            val streamed1 = StreamedEvent.fromEvent(event, position)
            val streamed2 = StreamedEvent.fromEvent(event, position)
            streamed1 == streamed2
        }
    }

    // ===========================================
    // Cross-cutting invariants
    // ===========================================

    @Test
    fun `All order events have consistent stream type`(): Unit = runBlocking {
        // Property: Any order event should have ORDER_STREAM as its stream type
        forAll(orderPlacedEventArb) { event ->
            event.streamType == Event.StreamType.ORDER_STREAM
        }
        forAll(orderCancelledEventArb) { event ->
            event.streamType == Event.StreamType.ORDER_STREAM
        }
    }

    @Test
    fun `Event versions are always positive`(): Unit = runBlocking {
        forAll(orderPlacedEventArb) { event ->
            event.version > 0
        }
        forAll(orderCancelledEventArb) { event ->
            event.version > 0
        }
    }
}
