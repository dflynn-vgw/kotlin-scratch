package org.example.retry

import org.example.common.OrderEventBuilder
import org.example.events.Event
import org.example.events.storage.StreamOffset
import org.example.events.storage.StreamedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class DeadLetterQueueTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should write entry to file in JSONL format`() {
        val filePath = tempDir.resolve("test-dlq.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
            ),
        )

        val entry = createTestEntry(position = 1)
        dlq.enqueue(entry)

        // Verify file was created and contains the entry
        assertTrue(Files.exists(filePath))
        val lines = Files.readAllLines(filePath)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("\"streamedEvent\""))
        assertTrue(lines[0].contains("\"failureReason\":\"Test failure\""))
        assertTrue(lines[0].contains("\"attemptCount\":3"))
    }

    @Test
    fun `should append multiple entries to file`() {
        val filePath = tempDir.resolve("multi-dlq.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
            ),
        )

        dlq.enqueue(createTestEntry(position = 1))
        dlq.enqueue(createTestEntry(position = 2))
        dlq.enqueue(createTestEntry(position = 3))

        val lines = Files.readAllLines(filePath)
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("\"position\":1"))
        assertTrue(lines[1].contains("\"position\":2"))
        assertTrue(lines[2].contains("\"position\":3"))
    }

    @Test
    fun `should not create file when DLQ is disabled`() {
        val filePath = tempDir.resolve("disabled-dlq.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = false,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
            ),
        )

        val entry = createTestEntry(position = 1)
        dlq.enqueue(entry)

        // Verify file was not created
        assertFalse(Files.exists(filePath))
    }

    @Test
    fun `should create parent directories automatically`() {
        val filePath = tempDir.resolve("nested").resolve("dir").resolve("dlq.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
            ),
        )

        dlq.enqueue(createTestEntry(position = 1))

        // Verify file and parent directories were created
        assertTrue(Files.exists(filePath))
        assertTrue(Files.exists(filePath.parent))
    }

    @Test
    fun `should include all required fields in JSON entry`() {
        val filePath = tempDir.resolve("full-entry.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
            ),
        )

        val exception = IOException("Connection timeout")
        val entry = DeadLetterQueue.Entry(
            streamedEvent = createTestEvent(100),
            failureReason = exception.message ?: "Unknown",
            exceptionType = exception.javaClass.name,
            stackTrace = exception.stackTraceToString(),
            attemptCount = 5,
            retriable = true,
            enqueuedAt = Instant.now(),
        )

        dlq.enqueue(entry)

        val json = Files.readString(filePath)
        assertTrue(json.contains("\"failureReason\":\"Connection timeout\""))
        assertTrue(json.contains("\"exceptionType\":\"java.io.IOException\""))
        assertTrue(json.contains("\"attemptCount\":5"))
        assertTrue(json.contains("\"retriable\":true"))
        assertTrue(json.contains("\"enqueuedAt\""))
        assertTrue(json.contains("\"stackTrace\""))
    }

    @Test
    fun `should update enqueuedAt timestamp when enqueuing`() {
        val filePath = tempDir.resolve("timestamp.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
            ),
        )

        val oldTimestamp = Instant.now().minusSeconds(100)
        val entry = createTestEntry(position = 1, enqueuedAt = oldTimestamp)

        dlq.enqueue(entry)

        // Verify the timestamp was updated (newer than the old one)
        val json = Files.readString(filePath)
        // The new timestamp should be recent (within last few seconds)
        assertTrue(json.contains("\"enqueuedAt\""))
        assertFalse(json.contains(oldTimestamp.toString())) // Old timestamp shouldn't be there
    }

    // Helper functions

    private fun createTestEvent(position: Long): StreamedEvent {
        val builder = OrderEventBuilder(
            id = "00000000-0000-0000-0000-000000000000",
            timestamp = System.currentTimeMillis(),
        )
        val event = builder.build(
            orderId = "order-$position",
            customerId = "customer-1",
            eventTypes = listOf(Event.EventType.ORDER_PLACED),
            startingVersion = 1,
        ).first()

        return StreamedEvent(
            event = event,
            offset = StreamOffset(position = position),
        )
    }

    private fun createTestEntry(
        position: Long,
        enqueuedAt: Instant = Instant.now(),
    ): DeadLetterQueue.Entry = DeadLetterQueue.Entry(
        streamedEvent = createTestEvent(position),
        failureReason = "Test failure",
        exceptionType = "java.io.IOException",
        stackTrace = "Stack trace...",
        attemptCount = 3,
        retriable = true,
        enqueuedAt = enqueuedAt,
    )
}
