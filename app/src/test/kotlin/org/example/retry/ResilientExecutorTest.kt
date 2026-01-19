package org.example.retry

import org.example.common.OrderEventBuilder
import org.example.events.Event
import org.example.events.storage.StreamOffset
import org.example.events.storage.StreamedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class ResilientExecutorTest {
    private val source = this.javaClass.simpleName
    private val mockLogger = object : Logger by org.slf4j.LoggerFactory.getLogger(ResilientExecutorTest::class.java) {}

    @Test
    fun `should succeed on first attempt`() {
        val executor = createExecutor()
        val event = createTestEvent(0)
        var callCount = 0

        val outcome = executor.execute(source, event) {
            callCount++
            // Success immediately
        }

        assertTrue(outcome is ResilientExecutor.Outcome.Success)
        assertEquals(1, (outcome as ResilientExecutor.Outcome.Success).attemptCount)
        assertEquals(1, callCount)
    }

    @Test
    fun `should retry transient failures and succeed`() {
        val executor = createExecutor(maxAttempts = 3)
        val event = createTestEvent(1)
        var callCount = 0

        val outcome = executor.execute(this.javaClass.simpleName, event) {
            callCount++
            if (callCount < 3) {
                throw IOException("Transient failure")
            }
            // Succeed on third attempt
        }

        assertTrue(outcome is ResilientExecutor.Outcome.Success)
        assertEquals(3, (outcome as ResilientExecutor.Outcome.Success).attemptCount)
        assertEquals(3, callCount)
    }

    @Test
    fun `should fail after exhausting all retries`() {
        val executor = createExecutor(maxAttempts = 3)
        val event = createTestEvent(2)
        var callCount = 0

        val outcome = executor.execute(source, event) {
            callCount++
            throw IOException("Persistent failure")
        }

        assertTrue(outcome is ResilientExecutor.Outcome.Failure)
        val failure = outcome as ResilientExecutor.Outcome.Failure
        assertEquals(3, failure.attemptCount)
        assertEquals("Persistent failure", failure.lastException.message)
        assertEquals(3, callCount)
    }

    @Test
    fun `should not retry non-retriable exceptions`() {
        val executor = createExecutor(maxAttempts = 3)
        val event = createTestEvent(3)
        var callCount = 0

        val outcome = executor.execute(source, event) {
            callCount++
            throw NullPointerException("Programming error")
        }

        assertTrue(outcome is ResilientExecutor.Outcome.Failure)
        val failure = outcome as ResilientExecutor.Outcome.Failure
        assertEquals(1, failure.attemptCount) // No retries
        assertEquals("Programming error", failure.lastException.message)
        assertEquals(1, callCount)
    }

    @Test
    fun `should only retry specific exceptions when configured`() {
        val executor = createExecutor(
            maxAttempts = 3,
            retryableExceptions = setOf(IOException::class.java),
        )
        val event = createTestEvent(4)
        var callCount = 0

        val outcome = executor.execute(source, event) {
            callCount++
            throw RuntimeException("Not in retriable list")
        }

        assertTrue(outcome is ResilientExecutor.Outcome.Failure)
        val failure = outcome as ResilientExecutor.Outcome.Failure
        assertEquals(1, failure.attemptCount) // No retries
        assertEquals(1, callCount)
    }

    @Test
    fun `should send to DLQ when enabled and action fails`() {
        val dlq = MockDeadLetterQueue()
        val executor = createExecutor(maxAttempts = 2, dlq = dlq, useDlq = true)
        val event = createTestEvent(5)

        val outcome = executor.execute(source, event) {
            throw IOException("Failed")
        }

        assertTrue(outcome is ResilientExecutor.Outcome.Failure)
        assertEquals(1, dlq.entries.size)
        assertEquals(event, dlq.entries[0].streamedEvent)
        assertEquals("Failed", dlq.entries[0].failureReason)
        assertEquals(2, dlq.entries[0].attemptCount)
    }

    @Test
    fun `should not send to DLQ when disabled`() {
        val dlq = MockDeadLetterQueue()
        val executor = createExecutor(maxAttempts = 2, dlq = dlq, useDlq = false)
        val event = createTestEvent(6)

        val outcome = executor.execute(source, event) {
            throw IOException("Failed")
        }

        assertTrue(outcome is ResilientExecutor.Outcome.Failure)
        assertEquals(0, dlq.entries.size) // DLQ not used
    }

    @Test
    fun `should respect retry strategy with custom delays`() {
        val startTime = System.currentTimeMillis()
        val executor = createExecutor(
            maxAttempts = 3,
            initialDelay = 50.milliseconds,
            maxDelay = 200.milliseconds,
            backoffMultiplier = 2.0,
        )
        val event = createTestEvent(7)
        var callCount = 0

        executor.execute(source, event) {
            callCount++
            throw IOException("Fail")
        }

        val elapsed = System.currentTimeMillis() - startTime
        // Should have delays: 50ms + 100ms = 150ms minimum
        assertTrue(elapsed >= 150, "Expected at least 150ms delay, got ${elapsed}ms")
        assertEquals(3, callCount)
    }

    @Test
    fun `should handle non-retriable exceptions and send to DLQ with retriable=false`() {
        val dlq = MockDeadLetterQueue()
        val executor = createExecutor(maxAttempts = 3, dlq = dlq, useDlq = true)
        val event = createTestEvent(8)

        executor.execute(source, event) {
            throw NullPointerException("Bug")
        }

        assertEquals(1, dlq.entries.size)
        assertEquals(false, dlq.entries[0].retriable) // Marked as non-retriable
        assertEquals(1, dlq.entries[0].attemptCount) // Only one attempt
    }

    @Test
    fun `should create DLQ entries with PENDING status in MANUAL_REVIEW mode`() {
        val dlq = MockDeadLetterQueue()
        val dlqOptions = DeadLetterQueue.Options(
            enabled = true,
            type = DeadLetterQueue.Options.StorageType.LOG,
            replayMode = DeadLetterQueue.Options.ReplayMode.MANUAL_REVIEW,
        )
        val executor = createExecutor(maxAttempts = 2, dlq = dlq, useDlq = true, dlqOptions = dlqOptions)
        val event = createTestEvent(9)

        executor.execute(source, event) {
            throw IOException("Failed")
        }

        assertEquals(1, dlq.entries.size)
        assertEquals(DeadLetterQueue.Entry.Status.PENDING, dlq.entries[0].status)
    }

    @Test
    fun `should create DLQ entries with REPLAY status in AUTOMATIC_REPLAY mode`() {
        val dlq = MockDeadLetterQueue()
        val dlqOptions = DeadLetterQueue.Options(
            enabled = true,
            type = DeadLetterQueue.Options.StorageType.LOG,
            replayMode = DeadLetterQueue.Options.ReplayMode.AUTOMATIC_REPLAY,
        )
        val executor = createExecutor(maxAttempts = 2, dlq = dlq, useDlq = true, dlqOptions = dlqOptions)
        val event = createTestEvent(10)

        executor.execute(source, event) {
            throw IOException("Failed")
        }

        assertEquals(1, dlq.entries.size)
        assertEquals(DeadLetterQueue.Entry.Status.REPLAY, dlq.entries[0].status)
    }

    // Helper functions

    private fun createExecutor(
        maxAttempts: Int = 3,
        initialDelay: kotlin.time.Duration = 10.milliseconds,
        maxDelay: kotlin.time.Duration = 100.milliseconds,
        backoffMultiplier: Double = 2.0,
        retryableExceptions: Set<Class<out Throwable>> = emptySet(),
        dlq: DeadLetterQueue = MockDeadLetterQueue(),
        useDlq: Boolean = false,
        dlqOptions: DeadLetterQueue.Options = DeadLetterQueue.Options(),
    ): ResilientExecutor {
        val strategy = RetryStrategy(
            maxAttempts = maxAttempts,
            initialDelay = initialDelay,
            maxDelay = maxDelay,
            backoffMultiplier = backoffMultiplier,
            retryableExceptions = retryableExceptions,
        )

        return ResilientExecutor(
            ResilientExecutor.Options(strategy, useDlq, dlqOptions),
            dlq,
            mockLogger,
        )
    }

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

    // Mock DLQ for testing
    private class MockDeadLetterQueue :
        DeadLetterQueue(
            Options(enabled = true, type = Options.StorageType.LOG),
        ) {
        val entries = mutableListOf<Entry>()

        override fun enqueue(entry: Entry) {
            entries.add(entry)
        }
    }
}
