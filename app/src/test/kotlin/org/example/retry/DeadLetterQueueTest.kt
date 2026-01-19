package org.example.retry

import org.example.common.OrderEventBuilder
import org.example.common.extensions.fromJSON
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
        assertEquals(entry, lines[0].fromJSON<DeadLetterQueue.Entry>())
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

        val entry = createTestEntry(position = 1, exception = IOException("Connection timeout"))

        dlq.enqueue(entry)

        val json = Files.readString(filePath)
        assertEquals(entry, json.fromJSON<DeadLetterQueue.Entry>())
    }

    @Test
    fun `should return Success outcome when enqueue succeeds`() {
        val filePath = tempDir.resolve("success-dlq.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
            ),
        )

        val outcome = dlq.enqueue(createTestEntry(position = 1))

        assertTrue(outcome is DeadLetterQueue.EnqueueOutcome.Success)
        // Rate should be 1 event / 60 seconds = ~0.0167 events/sec
        val rate = (outcome as DeadLetterQueue.EnqueueOutcome.Success).currentRate
        assertTrue(rate >= 0.01 && rate <= 0.02, "Expected rate around 0.0167/sec, got $rate")
    }

    @Test
    fun `should track enqueue rate accurately`() {
        val filePath = tempDir.resolve("rate-tracking.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
                circuitBreaker = DeadLetterQueue.Options.CircuitBreakerOptions(
                    enabled = false,
                    windowMillis = 1000, // 1 second window
                ),
            ),
        )

        // Enqueue 5 entries quickly
        repeat(5) {
            dlq.enqueue(createTestEntry(position = it.toLong()))
        }

        val rate = dlq.getEnqueueRate()
        // Rate should be ~5 events per second (5 events in 1 second window)
        assertTrue(rate >= 4.0 && rate <= 6.0, "Expected rate around 5/sec, got $rate")
    }

    @Test
    fun `should open circuit breaker when rate exceeds threshold`() {
        val filePath = tempDir.resolve("circuit-breaker.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
                circuitBreaker = DeadLetterQueue.Options.CircuitBreakerOptions(
                    enabled = true,
                    rateThreshold = 2.0, // 2 events per second
                    windowMillis = 1000, // 1 second window
                ),
            ),
        )

        // Enqueue 3 entries (exceeds threshold of 2/sec)
        repeat(3) {
            dlq.enqueue(createTestEntry(position = it.toLong()))
        }

        // Next enqueue should trip circuit breaker
        val outcome = dlq.enqueue(createTestEntry(position = 100))

        assertTrue(outcome is DeadLetterQueue.EnqueueOutcome.Failure)
        val failure = outcome as DeadLetterQueue.EnqueueOutcome.Failure
        assertTrue(failure.isCircuitBreakerOpen())
        assertTrue(failure.exception is DlqThresholdExceededException)
        val ex = failure.exception as DlqThresholdExceededException
        assertTrue(ex.currentRate >= 2.0)
        assertEquals(2.0, ex.threshold)
    }

    @Test
    fun `should allow enqueues when circuit breaker is disabled`() {
        val filePath = tempDir.resolve("no-circuit-breaker.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
                circuitBreaker = DeadLetterQueue.Options.CircuitBreakerOptions(
                    enabled = false, // Disabled
                    rateThreshold = 1.0,
                    windowMillis = 1000,
                ),
            ),
        )

        // Enqueue many entries - should all succeed
        repeat(10) {
            val outcome = dlq.enqueue(createTestEntry(position = it.toLong()))
            assertTrue(outcome is DeadLetterQueue.EnqueueOutcome.Success)
        }

        val lines = Files.readAllLines(filePath)
        assertEquals(10, lines.size)
    }

    @Test
    fun `should expire old timestamps from rate tracking window`() {
        val filePath = tempDir.resolve("window-expiry.jsonl")
        val dlq = DeadLetterQueue(
            DeadLetterQueue.Options(
                enabled = true,
                type = DeadLetterQueue.Options.StorageType.FILE,
                filePath = filePath.toString(),
                circuitBreaker = DeadLetterQueue.Options.CircuitBreakerOptions(
                    enabled = false,
                    windowMillis = 100, // 100ms window
                ),
            ),
        )

        // Enqueue 5 entries
        repeat(5) {
            dlq.enqueue(createTestEntry(position = it.toLong()))
        }

        // Wait for window to expire
        Thread.sleep(150)

        // Rate should be close to 0 now (all timestamps expired)
        val rate = dlq.getEnqueueRate()
        assertTrue(rate < 1.0, "Expected rate < 1/sec after window expiry, got $rate")
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
        attempts: Int = 3,
        retriable: Boolean = true,
        exception: Exception = Exception("Test failure"),
        enqueuedAt: Long = FIXED_TIMESTAMP,
        enqueuedBy: String = "DeadLetterQueueTest",
    ): DeadLetterQueue.Entry = DeadLetterQueue.Entry(
        streamedEvent = createTestEvent(position),
        failureReason = exception.message ?: "Unknown",
        exceptionType = exception.javaClass.name,
        stackTrace = exception.stackTraceToString(),
        attemptCount = attempts,
        retriable = retriable,
        enqueuedAt = enqueuedAt,
        enqueuedBy = enqueuedBy,
    )

    private companion object {
        const val FIXED_TIMESTAMP = 1625158800000L // July 1, 2021 10:00:00 AM UTC
    }
}
