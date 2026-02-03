package org.example.events.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.example.events.Event
import org.example.events.OrderEvents
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * CSV-based event stream implementation.
 * Reads events from a CSV file and persists bookmarks to separate CSV files.
 *
 * This implementation uses buffered reading to efficiently stream events from the CSV.
 * On each stream() call, it seeks to the requested position and reads only the
 * requested batch size, enabling real-time detection of appended events without
 * loading the entire file into memory.
 *
 * Bookmarks are cached in memory and persisted to separate CSV files for durability.
 *
 * Event CSV Format: ORDER_ID,CUSTOMER_ID,EVENT_TYPE,TIMESTAMP
 * Bookmark CSV Format: POSITION,TIMESTAMP
 */
class CSVEventStream(
    private val eventsCsvPath: String,
    private val bookmarksDir: String = ".",
) : EventStream {

    private val bookmarksLock = ReentrantReadWriteLock()
    private val bookmarksCache = mutableMapOf<String, Bookmark>()

    override fun stream(fromPosition: Long, batchSize: Int): Flow<Event<*>> = flow {
        require(fromPosition >= 0) { "fromPosition must be non-negative" }
        require(batchSize > 0) { "batchSize must be positive" }

        // Read only the events we need from the CSV
        val events = readEventsFromCsv(fromPosition.toInt(), batchSize)

        events.forEach { event ->
            emit(event)
        }
    }

    override suspend fun saveBookmark(name: String, position: Long) {
        val bookmark = Bookmark(name, position)

        bookmarksLock.write {
            bookmarksCache[name] = bookmark
        }

        // Write to file
        val bookmarkFile = File(bookmarksDir, "$name.csv")
        bookmarkFile.writeText("POSITION,TIMESTAMP\n${bookmark.position},${bookmark.updatedAt}\n")
    }

    override suspend fun getBookmark(name: String): Bookmark? {
        // Check cache first
        bookmarksLock.read {
            bookmarksCache[name]?.let { return it }
        }

        // Try to load from file
        val bookmarkFile = File(bookmarksDir, "$name.csv")
        return if (bookmarkFile.exists()) {
            try {
                val lines = bookmarkFile.readLines()
                if (lines.size < 2) return null // Need header + data

                val parts = lines[1].split(",")
                if (parts.size < 2) return null

                val position = parts[0].toLongOrNull() ?: return null
                val updatedAt = parts[1].toLongOrNull() ?: System.currentTimeMillis()

                val bookmark = Bookmark(name, position, updatedAt)

                bookmarksLock.write {
                    bookmarksCache[name] = bookmark
                }

                bookmark
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Read events from CSV starting at a specific line.
     *
     * @param startLine 1-indexed line number to start reading from (1 = first data line after header)
     * @param maxEvents Maximum number of events to read
     * @param skipHeader Whether to skip the header line (default: true)
     * @return List of events read from the CSV
     */
    private fun readEventsFromCsv(startLine: Int, maxEvents: Int, skipHeader: Boolean = true): List<Event<*>> {
        val csvFile = File(eventsCsvPath)

        if (!csvFile.exists()) {
            throw IllegalArgumentException("Events CSV file not found: $eventsCsvPath")
        }

        val events = mutableListOf<Event<*>>()

        // Use bufferedReader and skip to the line we need
        csvFile.bufferedReader().use { reader ->
            // Skip header line (line 0)
            reader.readLine()

            // Skip lines until we reach startLine
            var currentLine = 1
            val readFrom = if (skipHeader) startLine + 1 else startLine

            while (currentLine < readFrom) {
                reader.readLine() ?: return events // End of file
                currentLine++
            }

            // Read up to maxEvents
            var eventsRead = 0
            while (eventsRead < maxEvents) {
                val line = reader.readLine() ?: break // End of file
                val trimmedLine = line.trim()

                if (trimmedLine.isEmpty()) {
                    currentLine++
                    continue
                }

                try {
                    val parts = trimmedLine.split(",").map { it.trim() }
                    if (parts.size < 4) {
                        currentLine++
                        continue
                    }

                    val orderId = parts[0]
                    val customerId = parts[1]
                    val eventTypeStr = parts[2]
                    val timestamp = parts[3].toLongOrNull()

                    if (timestamp == null) {
                        currentLine++
                        continue
                    }

                    val eventType = try {
                        Event.EventType.valueOf(eventTypeStr)
                    } catch (e: IllegalArgumentException) {
                        currentLine++
                        continue
                    }

                    val event = buildEvent(orderId, customerId, eventType, timestamp, currentLine)
                    events.add(event)
                    eventsRead++
                } catch (e: Exception) {
                    // Skip malformed lines
                }

                currentLine++
            }
        }

        return events
    }

    private fun buildEvent(
        orderId: String,
        customerId: String,
        eventType: Event.EventType,
        timestamp: Long,
        lineNumber: Int,
    ): Event<*> {
        // Use version based on line number (1-indexed becomes version)
        val version = lineNumber

        return when (eventType) {
            Event.EventType.ORDER_PLACED -> OrderEvents.OrderPlacedEvent(
                version = version,
                payload = OrderEvents.OrderPlacedEvent.Payload(
                    orderId = orderId,
                    customerId = customerId,
                    items = listOf("Item1", "Item2"),
                    totalAmount = 100.0,
                    receivedAt = timestamp,
                ),
                timestamp = timestamp,
            )

            Event.EventType.ORDER_CONFIRMED -> OrderEvents.OrderConfirmedEvent(
                version = version,
                payload = OrderEvents.OrderConfirmedEvent.Payload(
                    orderId = orderId,
                    confirmedAt = timestamp,
                ),
                timestamp = timestamp,
            )

            Event.EventType.ORDER_CANCELLED -> OrderEvents.OrderCancelledEvent(
                version = version,
                payload = OrderEvents.OrderCancelledEvent.Payload(
                    orderId = orderId,
                    cancelledAt = timestamp,
                    reason = "From CSV",
                ),
                timestamp = timestamp,
            )

            Event.EventType.ORDER_MODIFIED -> OrderEvents.OrderModifiedEvent(
                version = version,
                payload = OrderEvents.OrderModifiedEvent.Payload(
                    orderId = orderId,
                    modifiedAt = timestamp,
                    items = listOf("Item1", "Item2", "Item3"),
                    totalAmount = 150.0,
                ),
                timestamp = timestamp,
            )
        }
    }
}
