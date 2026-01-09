package org.example.spsc

import kotlinx.coroutines.runBlocking
import org.example.common.OrderEventBuilder
import org.example.events.Event
import org.example.events.storage.Bookmark
import org.example.events.storage.InMemoryStreamingEventStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("SPSC Coordinator Tests")
class SpscCoordinatorTests {

    private val testId = "717e15f3-2463-49fd-b105-3a9b214b4a0b"
    private val testTime = 1630886400000L
    private val bookmarkName = "test-consumer"

    private lateinit var eventStore: InMemoryStreamingEventStore
    private lateinit var config: SpscConfig

    @BeforeEach
    fun setup() {
        eventStore = InMemoryStreamingEventStore()
        config = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 2,
            maxQueueDepth = 5,
            bookmarkName = bookmarkName,
        )
    }

    @Test
    fun testFullPipelineProduceConsumeWithBookmarkAdvancement() {
        // GIVEN: Create and save initial events
        val events = OrderEventBuilder(testId, testTime).build(
            "order-001",
            "customer@acme.com",
            listOf(
                Event.EventType.ORDER_PLACED,
                Event.EventType.ORDER_MODIFIED,
                Event.EventType.ORDER_CONFIRMED,
            ),
        )

        runBlocking {
            eventStore.save(events)
        }

        // AND: Track consumed events
        val consumedEvents = mutableListOf<Event<Any>>()
        val consumer = EventConsumer { batch, _ ->
            consumedEvents.addAll(batch)
        }

        // WHEN: Run coordinator
        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            eventStore,
            config,
        )

        coordinator.start()

        // Wait for processing to complete
        Thread.sleep(1000)
        coordinator.stop()
        assertTrue(coordinator.await(5000))

        // THEN: Verify all events were consumed
        assertEquals(events.size, consumedEvents.size)
        assertEquals(events, consumedEvents)

        // AND: Verify bookmark was advanced
        val finalBookmark = runBlocking {
            eventStore.getBookmark(bookmarkName)
        }
        assertNotNull(finalBookmark)
        assertEquals(events.size.toLong(), finalBookmark.position)
    }

    @Test
    fun testCoordinatorResumesFromBookmarkOnRestart() {
        // GIVEN: Create initial events
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

        runBlocking {
            eventStore.save(events)
        }

        // FIRST RUN: Consume only first 2 events
        val firstRunConsumer = mutableListOf<Event<Any>>()
        var consumeCount = 0
        val consumer = EventConsumer { batch, _ ->
            consumeCount++
            if (consumeCount == 1) {
                // Only consume first batch
                firstRunConsumer.addAll(batch)
                return@EventConsumer
            }
            // Stop after first batch
            throw IllegalStateException("Stop processing")
        }

        var coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            eventStore,
            config,
        )

        coordinator.start()
        Thread.sleep(500)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: First run consumed 2 events and advanced bookmark
        assertEquals(2, firstRunConsumer.size)
        val bookmarkAfterFirstRun = runBlocking {
            eventStore.getBookmark(bookmarkName)
        }
        assertNotNull(bookmarkAfterFirstRun)
        assertEquals(2L, bookmarkAfterFirstRun.position)

        // SECOND RUN: Consume remaining events
        val secondRunConsumer = mutableListOf<Event<Any>>()
        val consumer2 = EventConsumer { batch, _ ->
            secondRunConsumer.addAll(batch)
        }

        coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer2,
            eventStore,
            config,
        )

        coordinator.start()
        Thread.sleep(500)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: Second run consumed remaining 2 events
        assertEquals(2, secondRunConsumer.size)
        assertEquals(
            listOf(events[2], events[3]),
            secondRunConsumer,
        )

        // AND: Bookmark advanced to end
        val finalBookmark = runBlocking {
            eventStore.getBookmark(bookmarkName)
        }
        assertNotNull(finalBookmark)
        assertEquals(4L, finalBookmark.position)
    }

    @Test
    fun testConsumerFailureDoesNotAdvanceBookmark() {
        // GIVEN: Create events
        val events = OrderEventBuilder(testId, testTime).build(
            "order-003",
            "customer@acme.com",
            listOf(
                Event.EventType.ORDER_PLACED,
                Event.EventType.ORDER_MODIFIED,
            ),
        )

        runBlocking {
            eventStore.save(events)
        }

        // AND: Consumer that throws on first call
        val consumer = EventConsumer { _, _ ->
            throw RuntimeException("Processing failed")
        }

        // WHEN: Run coordinator with failing consumer
        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            eventStore,
            config,
        )

        coordinator.start()
        Thread.sleep(500)
        coordinator.stop()
        coordinator.await(5000)

        // THEN: Verify bookmark was NOT advanced
        val bookmark = runBlocking {
            eventStore.getBookmark(bookmarkName)
        }

        // Bookmark should either be null or at position 0
        assertTrue(bookmark == null || bookmark.position == 0L)
    }

    @Test
    fun testQueueRespectsMaxDepthAndProvidesBackpressure() {
        // GIVEN: Create many events
        val eventTypes = List(20) { Event.EventType.ORDER_PLACED }
        val events = OrderEventBuilder(testId, testTime).build(
            "order-004",
            "customer@acme.com",
            eventTypes,
        )

        runBlocking {
            eventStore.save(events)
        }

        // AND: Slow consumer that processes one event at a time
        val consumedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val consumer = EventConsumer { batch, _ ->
            Thread.sleep(100) // Simulate slow processing
            consumedCount.addAndGet(batch.size)
        }

        // WHEN: Run with small queue to test backpressure
        val slowConfig = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 1,
            maxQueueDepth = 3, // Small queue to force backpressure
            bookmarkName = bookmarkName,
        )

        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            eventStore,
            slowConfig,
        )

        coordinator.start()
        Thread.sleep(2000)
        coordinator.stop()
        coordinator.await(10000)

        // THEN: Verify all events were processed (backpressure worked)
        assertEquals(20, consumedCount.get())
    }

    @Test
    fun testConsumerReceivesEventsInBatchesOfConfiguredSize() {
        // GIVEN: Create 6 events
        val events = OrderEventBuilder(testId, testTime).build(
            "order-005",
            "customer@acme.com",
            List(6) { Event.EventType.ORDER_PLACED },
        )

        runBlocking {
            eventStore.save(events)
        }

        // AND: Track batch sizes
        val batchSizes = mutableListOf<Int>()
        val consumer = EventConsumer { batch, _ ->
            batchSizes.add(batch.size)
        }

        // WHEN: Run with specific batch size
        val config = SpscConfig(
            producerBatchSize = 3,
            consumerBatchSize = 2, // Expect batches of 2
            maxQueueDepth = 10,
            bookmarkName = bookmarkName,
        )

        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            eventStore,
            config,
        )

        coordinator.start()
        Thread.sleep(1000)
        coordinator.stop()
        coordinator.await(5000)

        // THEN: Verify batches are of expected size
        assertEquals(3, batchSizes.size) // 6 events / 2 per batch = 3 batches
        assertTrue(batchSizes.all { it == 2 })
    }
}
