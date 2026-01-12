package org.example.events.storage

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.example.common.EventTypeListConverter
import org.example.common.OrderEventBuilder
import org.example.events.Event
import org.example.events.storage.InMemoryEventStreamTests.Companion.CONTEXT.TIME
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.converter.ConvertWith
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class InMemoryEventStreamTests {

    @BeforeEach
    fun setup() {
        CONTEXT.reset()
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                                        | ORDER_ID | CUSTOMER_ID   | SEED_EVENTS                                      | BOOKMARK    | BATCH_SIZE | EVENTS
            Verify empty stream reads empty                 | 00000000 | a.bee@acme.co | []                                               | 0           | 5          | []
            Verify 1 event streamed (from start)            | 00000001 | b.cee@acme.co | [ORDER_PLACED]                                   | 0           | 5          | [ORDER_PLACED]
            Verify 3 events streamed (from start)           | 00000002 | c.dee@acme.co | [ORDER_PLACED, ORDER_MODIFIED, ORDER_CONFIRMED]  | 0           | 5          | [ORDER_PLACED, ORDER_MODIFIED, ORDER_CONFIRMED]
            Verify 3 events streamed (from position 2)      | 00000003 | d.eee@acme.co | [ORDER_PLACED, ORDER_MODIFIED, ORDER_CONFIRMED]  | 1           | 5          | [ORDER_MODIFIED, ORDER_CONFIRMED]
            Verify 2 events streamed (batch size 2)         | 00000004 | e.fff@acme.co | [ORDER_PLACED, ORDER_MODIFIED, ORDER_CONFIRMED]  | 0           | 2          | [ORDER_PLACED, ORDER_MODIFIED]""",
    )
    @DisplayName("Scenarios for InMemoryEventStore")
    fun scenarios(
        name: String,
        orderId: String,
        customerId: String,
        @ConvertWith(EventTypeListConverter::class) seedEvents: List<Event.EventType>,
        bookmark: Long,
        batchSize: Int,
        @ConvertWith(EventTypeListConverter::class) events: List<Event.EventType>,
    ) {
        runBlocking {
            GIVEN
                .seedEvents(orderId, customerId, seedEvents)
            WHEN
                .streamEvents(bookmark, batchSize)
                .saveBookmark(position = bookmark + CONTEXT.events.size)
            THEN
                .eventsStreamedAre(orderId, customerId, events, (bookmark + 1).toInt())
                .bookmarkPositionIs(position = bookmark + CONTEXT.events.size)
        }
    }

    private companion object {

        object CONTEXT {
            lateinit var eventStream: EventStream

            lateinit var events: List<Event<Any>>

            /** Fixed test time for all tests ("2021-09-06T00:00:00Z") */
            const val TIME = 1630886400000L

            /** Fixed test ID for all tests ("717e15f3-2463-49fd-b105-3a9b214b4a0b") */
            const val ID = "717e15f3-2463-49fd-b105-3a9b214b4a0b"

            /** Fixed test bookmark name for all tests */
            const val BOOKMARK = "TEST_BOOKMARK"

            fun reset() {
                events = emptyList()
            }
        }

        object GIVEN {
            fun seedEvents(orderId: String, customerId: String, eventTypes: List<Event.EventType>): GIVEN {
                val events = OrderEventBuilder(CONTEXT.ID, CONTEXT.TIME).build(orderId, customerId, eventTypes)
                CONTEXT.eventStream = InMemoryEventStream(events, fixedTime = TIME)
                return this
            }
        }

        object WHEN {

            suspend fun streamEvents(bookmark: Long, batchSize: Int): WHEN {
                CONTEXT.events = CONTEXT.eventStream.stream(bookmark, batchSize).toList()
                return this
            }

            suspend fun saveBookmark(name: String = CONTEXT.BOOKMARK, position: Long): WHEN {
                CONTEXT.eventStream.saveBookmark(name, position)
                return this
            }
        }

        object THEN {
            fun eventsStreamedAre(orderId: String, customerId: String, expectedEvents: List<Event.EventType>, startingVersion: Int = 1): THEN {
                val expect = OrderEventBuilder(CONTEXT.ID, CONTEXT.TIME).build(orderId, customerId, expectedEvents, startingVersion)
                val actual = CONTEXT.events
                assertEquals(expect, actual)
                return this
            }

            suspend fun bookmarkPositionIs(name: String = CONTEXT.BOOKMARK, position: Long): THEN {
                assertEquals(Bookmark(name, position, CONTEXT.TIME), CONTEXT.eventStream.getBookmark(name))
                return this
            }
        }
    }
}
