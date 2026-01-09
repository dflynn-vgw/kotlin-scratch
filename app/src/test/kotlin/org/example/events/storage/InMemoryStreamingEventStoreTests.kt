package org.example.events.storage

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.example.common.OrderEventBuilder
import org.example.events.Event
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("InMemoryStreamingEventStore Tests")
class InMemoryStreamingEventStoreTests {

    private val testId = "717e15f3-2463-49fd-b105-3a9b214b4a0b"
    private val testTime = 1630886400000L

    private lateinit var eventStore: InMemoryStreamingEventStore

    @BeforeEach
    fun setup() {
        eventStore = InMemoryStreamingEventStore()
    }

    @Test
    fun streamingFromPositionZero() {
        runBlocking {
            // GIVEN
            val events = OrderEventBuilder(testId, testTime).build(
                "order-001",
                "customer@acme.com",
                listOf(
                    Event.EventType.ORDER_PLACED,
                    Event.EventType.ORDER_MODIFIED,
                    Event.EventType.ORDER_CONFIRMED,
                ),
            )
            eventStore.save(events)

            // WHEN
            val streamed = eventStore.stream(0).toList()

            // THEN
            assertEquals(3, streamed.size)
            assertEquals(events, streamed)
        }
    }

    @Test
    fun streamingFromPosition2() {
        runBlocking {
            // GIVEN
            val events = OrderEventBuilder(testId, testTime).build(
                "order-002",
                "customer@acme.com",
                listOf(
                    Event.EventType.ORDER_PLACED,
                    Event.EventType.ORDER_MODIFIED,
                    Event.EventType.ORDER_CONFIRMED,
                    Event.EventType.ORDER_CANCELLED,
                ),
            )
            eventStore.save(events)

            // WHEN
            val streamed = eventStore.stream(2).toList()

            // THEN
            assertEquals(2, streamed.size)
            assertEquals(listOf(events[2], events[3]), streamed)
        }
    }

    @Test
    fun bookmarkPersistence() {
        runBlocking {
            // GIVEN
            eventStore.saveBookmark("consumer-1", 5)

            // WHEN
            val bookmark = eventStore.getBookmark("consumer-1")

            // THEN
            assertEquals("consumer-1", bookmark?.name)
            assertEquals(5L, bookmark?.position)
        }
    }

    @Test
    fun multipleBookmarks() {
        runBlocking {
            // GIVEN
            eventStore.saveBookmark("consumer-1", 3)
            eventStore.saveBookmark("consumer-2", 7)

            // WHEN
            val b1 = eventStore.getBookmark("consumer-1")
            val b2 = eventStore.getBookmark("consumer-2")

            // THEN
            assertEquals(3L, b1?.position)
            assertEquals(7L, b2?.position)
        }
    }
}
