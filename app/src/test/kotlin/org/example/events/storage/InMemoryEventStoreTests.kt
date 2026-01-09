package org.example.events.storage

import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat
import kotlinx.coroutines.runBlocking
import org.example.common.EventTypeListConverter
import org.example.events.Event
import org.example.events.OrderEvents
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.converter.ConvertWith
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID
import kotlin.test.assertEquals

class InMemoryEventStoreTests {

    @BeforeEach
    fun setup() {
        CONTEXT.reset()
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | ORDER_ID | CUSTOMER_ID   | SEED_EVENTS                                      | SAVE_EVENTS        | READ_FROM  | READ_EVENTS
            Basic Save and Read         | 00000001 | d.fly@acme.co | [ORDER_PLACED, ORDER_MODIFIED, ORDER_CONFIRMED]  | [ORDER_CANCELLED]  | 1          | [ORDER_PLACED, ORDER_MODIFIED, ORDER_CONFIRMED, ORDER_CANCELLED]""",
    )
    @DisplayName("Scenarios for InMemoryEventStore")
    fun scenarios(
        name: String,
        orderId: String,
        customerId: String,
        @ConvertWith(EventTypeListConverter::class) seedEvents: List<Event.EventType>,
        @ConvertWith(EventTypeListConverter::class) saveEvents: List<Event.EventType>,
        readFrom: Int,
        @ConvertWith(EventTypeListConverter::class) readEvents: List<Event.EventType>,
    ) {
        runBlocking {
            GIVEN.seedEvents(orderId, customerId, seedEvents)
            WHEN.saveEvents(orderId, customerId, saveEvents, seedEvents.size + 1)
            THEN.readEvents(orderId, customerId, readFrom, readEvents)
        }
    }

    private companion object {

        private fun streamId(orderId: String) = "order-$orderId"
        private fun parseEvent(orderId: String, customerId: String, eventType: Event.EventType, version: Int): Event<Any> = when (eventType) {
            Event.EventType.ORDER_PLACED -> OrderEvents.OrderPlacedEvent(
                id = UUID.fromString(CONTEXT.ID),
                version = version,
                timestamp = CONTEXT.TIME,
                payload = OrderEvents.OrderPlacedEvent.Payload(
                    orderId = orderId,
                    customerId = customerId,
                    items = listOf("Item1", "Item2"),
                    totalAmount = 100.0,
                    receivedAt = CONTEXT.TIME,
                ),
            )

            Event.EventType.ORDER_CONFIRMED -> OrderEvents.OrderConfirmedEvent(
                id = UUID.fromString(CONTEXT.ID),
                version = version,
                timestamp = CONTEXT.TIME,
                payload = OrderEvents.OrderConfirmedEvent.Payload(
                    orderId = orderId,
                    confirmedAt = CONTEXT.TIME,
                ),
            )

            Event.EventType.ORDER_CANCELLED -> OrderEvents.OrderCancelledEvent(
                id = UUID.fromString(CONTEXT.ID),
                version = version,
                timestamp = CONTEXT.TIME,
                payload = OrderEvents.OrderCancelledEvent.Payload(
                    orderId = orderId,
                    cancelledAt = CONTEXT.TIME,
                    reason = "Customer Request",
                ),
            )

            Event.EventType.ORDER_MODIFIED -> OrderEvents.OrderModifiedEvent(
                id = UUID.fromString(CONTEXT.ID),
                version = version,
                timestamp = CONTEXT.TIME,
                payload = OrderEvents.OrderModifiedEvent.Payload(
                    orderId = orderId,
                    modifiedAt = CONTEXT.TIME,
                    items = listOf("Item1", "Item2"),
                    totalAmount = 100.0,
                ),
            )
        }

        private fun parseEvents(orderId: String, customerId: String, eventTypes: List<Event.EventType>, startingVersion: Int = 1): List<Event<Any>> = eventTypes.mapIndexed { index, eventType ->
            parseEvent(orderId, customerId, eventType, startingVersion + index)
        }

        object CONTEXT {
            lateinit var eventStore: EventStore

            /** Fixed test time for all tests ("2021-09-06T00:00:00Z") */
            const val TIME = 1630886400000L

            /** Fixed test ID for all tests ("717e15f3-2463-49fd-b105-3a9b214b4a0b") */
            const val ID = "717e15f3-2463-49fd-b105-3a9b214b4a0b"

            fun reset() {
                eventStore = InMemoryEventStore()
            }
        }

        object GIVEN {
            fun seedEvents(orderId: String, customerId: String, eventTypes: List<Event.EventType>): GIVEN {
                val events = parseEvents(orderId, customerId, eventTypes)
                CONTEXT.eventStore = InMemoryEventStore(events)
                return this
            }
        }

        object WHEN {

            suspend fun saveEvents(orderId: String, customerId: String, eventTypes: List<Event.EventType>, fromVersion: Int = 0) {
                val events = parseEvents(orderId, customerId, eventTypes, fromVersion)
                if (eventTypes.size == 1) {
                    CONTEXT.eventStore.save(events.first())
                    return
                }

                CONTEXT.eventStore.save(events)
                return
            }
        }

        object THEN {
            suspend fun readEvents(orderId: String, customerId: String, fromVersion: Int, expectedEvents: List<Event.EventType>) {
                val expect = parseEvents(orderId, customerId, expectedEvents, fromVersion)
                val actual = CONTEXT.eventStore.read(streamId(orderId), fromVersion)
                assertEquals(expect, actual)
            }
        }
    }
}
