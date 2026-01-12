package org.example.events.storage

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.example.events.OrderEvents
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("CSVEventStream Tests")
class CSVEventStreamTests {

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

    @ParameterizedTest(name = "Stream from position {0} with batch size {1}")
    @CsvSource(
        "0, 5, 5", // From start, large batch → all 5 events
        "0, 2, 2", // From start, batch 2 → first 2 events
        "2, 5, 3", // From position 2, large batch → events 2,3,4
        "4, 10, 1", // From position 4, large batch → last event
        "5, 5, 0", // From position 5 (end), any batch → no events
    )
    @DisplayName("Stream events from position with batch size limit")
    fun testStreamingWithBatchSize(fromPosition: Int, batchSize: Int, expectedCount: Int) {
        runBlocking {
            val events = csvStream.stream(fromPosition.toLong(), batchSize).toList()

            assertEquals(
                expectedCount,
                events.size,
                "Expected $expectedCount events from position $fromPosition with batch size $batchSize",
            )
        }
    }

    @ParameterizedTest(name = "Stream from {0}")
    @CsvSource(
        "0", // From start
        "1", // From middle
        "2", // From middle
        "4", // From end
    )
    @DisplayName("Streamed events have correct event types")
    fun testStreamedEventTypes(fromPosition: Int) {
        runBlocking {
            val expectedTypes = listOf(
                "ORDER_PLACED",
                "ORDER_MODIFIED",
                "ORDER_CONFIRMED",
                "ORDER_PLACED",
                "ORDER_CANCELLED",
            )

            val events = csvStream.stream(fromPosition.toLong(), 10).toList()

            events.forEachIndexed { index, event ->
                val expectedType = expectedTypes[fromPosition + index]
                assertEquals(expectedType, event.type.name)
            }
        }
    }

    @ParameterizedTest(name = "Bookmark {0} persists correctly")
    @CsvSource(
        "consumer-1, 3",
        "consumer-2, 0",
        "processor-a, 5",
    )
    @DisplayName("Bookmark save and retrieve")
    fun testBookmarkPersistence(bookmarkName: String, position: Long) {
        runBlocking {
            // WHEN: Save bookmark
            csvStream.saveBookmark(bookmarkName, position)

            // THEN: Can retrieve it
            val retrieved = csvStream.getBookmark(bookmarkName)

            assertNotNull(retrieved)
            assertEquals(bookmarkName, retrieved.name)
            assertEquals(position, retrieved.position)
            assertTrue(retrieved.updatedAt > 0)
        }
    }

    @ParameterizedTest(name = "Bookmark {0} persists to file")
    @CsvSource(
        "test-consumer, 2",
        "another-consumer, 4",
    )
    @DisplayName("Bookmark file creation and reading")
    fun testBookmarkFileI_O(bookmarkName: String, position: Long) {
        runBlocking {
            // WHEN: Save bookmark
            csvStream.saveBookmark(bookmarkName, position)

            // THEN: File exists with correct content
            val bookmarkFile = File(tempDir, "$bookmarkName.csv")
            assertTrue(bookmarkFile.exists())

            val lines = bookmarkFile.readLines()
            assertEquals(2, lines.size, "Bookmark file should have header + data")
            assertEquals("POSITION,TIMESTAMP", lines[0])
            assertTrue(lines[1].startsWith("$position,"))

            // WHEN: Create new stream instance (cache miss)
            val newStream = CSVEventStream(eventsFile.absolutePath, tempDir.absolutePath)

            // THEN: Can still retrieve the bookmark
            val retrieved = newStream.getBookmark(bookmarkName)
            assertNotNull(retrieved)
            assertEquals(position, retrieved.position)
        }
    }

    @DisplayName("Multiple bookmarks don't interfere")
    fun testMultipleBookmarks() {
        runBlocking {
            // WHEN: Save multiple bookmarks
            csvStream.saveBookmark("consumer-1", 1L)
            csvStream.saveBookmark("consumer-2", 3L)
            csvStream.saveBookmark("consumer-3", 4L)

            // THEN: Each can be retrieved independently
            assertEquals(1L, csvStream.getBookmark("consumer-1")?.position)
            assertEquals(3L, csvStream.getBookmark("consumer-2")?.position)
            assertEquals(4L, csvStream.getBookmark("consumer-3")?.position)
        }
    }

    @DisplayName("Non-existent bookmark returns null")
    fun testNonExistentBookmark() {
        runBlocking {
            val bookmark = csvStream.getBookmark("does-not-exist")
            assertNull(bookmark)
        }
    }

    @DisplayName("Streamed events have correct payload data")
    fun testStreamedEventPayloads() {
        runBlocking {
            val events = csvStream.stream(0, 2).toList()

            assertEquals(2, events.size)

            // First event should be ORDER_PLACED for order-001
            val firstEvent = events[0]
            assertEquals("ORDER_PLACED", firstEvent.type.name)
            assertTrue(firstEvent.payload is OrderEvents.OrderPlacedEvent.Payload)
            val firstPayload = (firstEvent as OrderEvents.OrderPlacedEvent).payload
            assertEquals("order-001", firstPayload.orderId)
            assertEquals("alice@example.com", firstPayload.customerId)

            // Second event should be ORDER_MODIFIED for order-001
            val secondEvent = events[1]
            assertEquals("ORDER_MODIFIED", secondEvent.type.name)
            assertTrue(secondEvent.payload is OrderEvents.OrderModifiedEvent.Payload)
            val secondPayload = (secondEvent as OrderEvents.OrderModifiedEvent).payload
            assertEquals("order-001", secondPayload.orderId)
        }
    }

    @DisplayName("Event versions correspond to CSV line numbers")
    fun testEventVersions() {
        runBlocking {
            val events = csvStream.stream(0, 5).toList()

            assertEquals(5, events.size)

            // Versions should be 1-based (line number in CSV)
            events.forEachIndexed { index, event ->
                assertEquals(index + 1, event.version, "Event at index $index should have version ${index + 1}")
            }
        }
    }

    @DisplayName("Malformed CSV lines are skipped")
    fun testMalformedCsvHandling() {
        // Create a CSV with malformed lines
        val badCsv = File(tempDir, "bad-events.csv")
        badCsv.writeText(
            """ORDER_ID,CUSTOMER_ID,EVENT_TYPE,TIMESTAMP
order-001,alice@example.com,ORDER_PLACED,1630886400000
invalid,line,not,enough,fields
order-002,bob@example.com,ORDER_PLACED,1630886403000
order-003,charlie@example.com,INVALID_EVENT_TYPE,1630886405000
order-004,diana@example.com,ORDER_CONFIRMED,1630886406000
""",
        )

        val badStream = CSVEventStream(badCsv.absolutePath, tempDir.absolutePath)

        runBlocking {
            val events = badStream.stream(0, 10).toList()

            // Should have 3 valid events (malformed lines skipped)
            assertEquals(3, events.size)

            // Verify the valid events are the right ones
            assertEquals("order-001", (events[0] as OrderEvents.OrderPlacedEvent).payload.orderId)
            assertEquals("order-002", (events[1] as OrderEvents.OrderPlacedEvent).payload.orderId)
            assertEquals("order-004", (events[2] as OrderEvents.OrderConfirmedEvent).payload.orderId)
        }
    }

    @DisplayName("Bookmark position constraint")
    fun testBookmarkPositionConstraint() {
        runBlocking {
            // Save bookmark at last event position
            csvStream.saveBookmark("last", 4L)

            // Verify it persists
            val bookmark = csvStream.getBookmark("last")
            assertNotNull(bookmark)
            assertEquals(4L, bookmark.position)
        }
    }
}
