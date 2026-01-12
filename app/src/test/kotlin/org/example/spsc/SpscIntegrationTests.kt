package org.example.spsc

import kotlinx.coroutines.runBlocking
import org.example.events.storage.CSVEventStream
import org.example.events.storage.StreamedEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("SPSC Integration Tests")
class SpscIntegrationTests {

    @TempDir
    lateinit var tempDir: File

    private lateinit var csvStream: CSVEventStream
    private lateinit var eventsFile: File

    @BeforeEach
    fun setup() {
        // Copy test events.csv to temp directory
        val testResourcesUrl = this::class.java.classLoader.getResource("events.csv")
        checkNotNull(testResourcesUrl) { "Test resource events.csv not found" }

        eventsFile = File(tempDir, "events.csv")
        eventsFile.writeText(testResourcesUrl.readText())

        csvStream = CSVEventStream(eventsFile.absolutePath, tempDir.absolutePath)
    }

    @DisplayName("Full SPSC pipeline: produce → consume → bookmark")
    fun testFullPipeline() {
        val config = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 2,
            maxQueueDepth = 5,
            bookmarkName = "test-consumer",
        )

        val consumedEvents = mutableListOf<StreamedEvent>()
        val consumer = EventConsumer { streamedEvents ->
            consumedEvents.addAll(streamedEvents)
        }

        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            csvStream,
            config,
        )

        coordinator.start()

        // Wait for processing to complete
        Thread.sleep(1000)
        coordinator.stop()
        assertTrue(coordinator.await(5000))

        // THEN: Verify all 5 events were consumed
        assertEquals(5, consumedEvents.size)

        // Verify each event has correct position
        consumedEvents.forEachIndexed { index, streamedEvent ->
            assertEquals(index.toLong(), streamedEvent.offset.position)
        }

        // Verify bookmark was advanced
        runBlocking {
            val bookmark = csvStream.getBookmark("test-consumer")
            assertNotNull(bookmark)
            assertEquals(5L, bookmark.position) // After 5 events (positions 0-4)
        }
    }

    @DisplayName("Resume from bookmark on coordinator restart")
    fun testBookmarkResume() {
        val config = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 1,
            maxQueueDepth = 5,
            bookmarkName = "resumable-consumer",
        )

        // FIRST RUN: Process only first batch
        val firstRunEvents = mutableListOf<StreamedEvent>()
        var callCount = 0
        val consumer1 = EventConsumer { streamedEvents ->
            callCount++
            if (callCount <= 2) { // Allow first 2 calls
                firstRunEvents.addAll(streamedEvents)
            } else {
                throw IllegalStateException("Simulated failure on third call")
            }
        }

        var coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer1,
            csvStream,
            config,
        )

        coordinator.start()
        Thread.sleep(500)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: First run processed some events
        assertTrue(firstRunEvents.isNotEmpty())
        val lastProcessedPosition = firstRunEvents.maxOf { it.offset.position }

        // VERIFY: Bookmark was saved
        runBlocking {
            val bookmark = csvStream.getBookmark("resumable-consumer")
            assertNotNull(bookmark)
            assertTrue(bookmark.position > 0)
        }

        // SECOND RUN: Resume from bookmark
        val secondRunEvents = mutableListOf<StreamedEvent>()
        val consumer2 = EventConsumer { streamedEvents ->
            secondRunEvents.addAll(streamedEvents)
        }

        coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer2,
            csvStream,
            config,
        )

        coordinator.start()
        Thread.sleep(500)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: Second run continued from where first left off
        assertTrue(secondRunEvents.isNotEmpty())

        // Events should continue from last processed position
        secondRunEvents.forEach { streamedEvent ->
            assertTrue(streamedEvent.offset.position > lastProcessedPosition)
        }

        // VERIFY: All events eventually consumed
        val allConsumed = firstRunEvents.size + secondRunEvents.size
        assertTrue(allConsumed >= 3) // At least consumed beyond initial batch
    }

    @DisplayName("Consumer failure does NOT advance bookmark")
    fun testFailureNoBookmarkAdvance() {
        val config = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 2,
            maxQueueDepth = 5,
            bookmarkName = "failing-consumer",
        )

        val consumer = EventConsumer { _ ->
            throw RuntimeException("Simulated consumer failure")
        }

        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            csvStream,
            config,
        )

        coordinator.start()
        Thread.sleep(500)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: Bookmark was NOT advanced
        runBlocking {
            val bookmark = csvStream.getBookmark("failing-consumer")
            assertTrue(bookmark == null || bookmark.position == 0L)
        }
    }

    @DisplayName("Batch size limiting in consumer")
    fun testBatchSizeLimiting() {
        val config = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 2, // Request 2 at a time
            maxQueueDepth = 5,
            bookmarkName = "batch-consumer",
        )

        val batchSizes = mutableListOf<Int>()
        val consumer = EventConsumer { streamedEvents ->
            batchSizes.add(streamedEvents.size)
        }

        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            csvStream,
            config,
        )

        coordinator.start()
        Thread.sleep(1000)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: Batches respect configured size
        // With 5 events and batch size 2, we expect: [2, 2, 1]
        assertTrue(batchSizes.isNotEmpty())
        assertTrue(batchSizes[0] <= 2, "First batch should be max 2 items")
        batchSizes.dropLast(1).forEach { size ->
            assertEquals(2, size, "All batches except last should be size 2")
        }
        assertTrue(batchSizes.last() <= 2, "Last batch should be at most 2 items")
    }

    @DisplayName("Position tracking across batches")
    fun testPositionTracking() {
        val config = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 1, // Small batch to test multiple calls
            maxQueueDepth = 5,
            bookmarkName = "position-tracker",
        )

        val allStreamedEvents = mutableListOf<StreamedEvent>()
        val consumer = EventConsumer { streamedEvents ->
            allStreamedEvents.addAll(streamedEvents)
        }

        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            csvStream,
            config,
        )

        coordinator.start()
        Thread.sleep(1000)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: Positions are sequential
        allStreamedEvents.forEachIndexed { index, streamedEvent ->
            assertEquals(index.toLong(), streamedEvent.offset.position, "Position should match index")
        }

        // VERIFY: All events consumed
        assertEquals(5, allStreamedEvents.size, "Should consume all 5 events")

        // VERIFY: Final bookmark is at end
        runBlocking {
            val bookmark = csvStream.getBookmark("position-tracker")
            assertNotNull(bookmark)
            assertEquals(5L, bookmark.position, "Bookmark should be at position 5 (after all events)")
        }
    }

    @DisplayName("Multiple coordinators with different bookmarks")
    fun testMultipleConsumers() {
        val config1 = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 2,
            maxQueueDepth = 5,
            bookmarkName = "consumer-1",
        )

        val config2 = SpscConfig(
            producerBatchSize = 2,
            consumerBatchSize = 2,
            maxQueueDepth = 5,
            bookmarkName = "consumer-2",
        )

        val consumer1Events = mutableListOf<StreamedEvent>()
        val consumer1 = EventConsumer { streamedEvents ->
            consumer1Events.addAll(streamedEvents)
        }

        val consumer2Events = mutableListOf<StreamedEvent>()
        val consumer2 = EventConsumer { streamedEvents ->
            consumer2Events.addAll(streamedEvents)
        }

        // Run first coordinator
        val coordinator1 = SpscCoordinator(
            DefaultEventProducer(),
            consumer1,
            csvStream,
            config1,
        )

        coordinator1.start()
        Thread.sleep(800)
        coordinator1.stop()
        coordinator1.await(5000)

        // Run second coordinator with same event source
        val coordinator2 = SpscCoordinator(
            DefaultEventProducer(),
            consumer2,
            csvStream,
            config2,
        )

        coordinator2.start()
        Thread.sleep(800)
        coordinator2.stop()
        coordinator2.await(5000)

        // VERIFY: Both consumed all events
        assertEquals(5, consumer1Events.size)
        assertEquals(5, consumer2Events.size)

        // VERIFY: Bookmarks are independent
        runBlocking {
            val bookmark1 = csvStream.getBookmark("consumer-1")
            val bookmark2 = csvStream.getBookmark("consumer-2")

            assertNotNull(bookmark1)
            assertNotNull(bookmark2)
            assertEquals(5L, bookmark1.position)
            assertEquals(5L, bookmark2.position)
        }
    }

    @DisplayName("Large batch processing")
    fun testLargeBatchProcessing() {
        val config = SpscConfig(
            producerBatchSize = 10, // Larger than available events
            consumerBatchSize = 5, // Still split into batches
            maxQueueDepth = 10,
            bookmarkName = "large-batch-consumer",
        )

        val allEvents = mutableListOf<StreamedEvent>()
        val consumer = EventConsumer { streamedEvents ->
            allEvents.addAll(streamedEvents)
        }

        val coordinator = SpscCoordinator(
            DefaultEventProducer(),
            consumer,
            csvStream,
            config,
        )

        coordinator.start()
        Thread.sleep(800)
        coordinator.stop()
        coordinator.await(5000)

        // VERIFY: All events consumed despite large batch request
        assertEquals(5, allEvents.size)

        // VERIFY: Positions still correct
        allEvents.forEachIndexed { index, event ->
            assertEquals(index.toLong(), event.offset.position)
        }
    }
}
