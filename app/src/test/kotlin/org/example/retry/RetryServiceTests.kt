package org.example.retry

import kotlinx.coroutines.runBlocking
import org.example.common.OrderEventBuilder
import org.example.events.Event
import org.example.events.storage.StreamOffset
import org.example.events.storage.StreamedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class RetryServiceTests {
    private val retryService = RetryService()

    @Test
    fun `should succeed on first attempt`() = runBlocking {
        val event = createTestEvent(0)
        var callCount = 0

        val outcome = retryService.executeWithRetry(
            streamedEvent = event,
            strategy = RetryStrategy.DEFAULT,
        ) {
            callCount++
            // Success immediately
        }

        assertTrue(outcome is RetryOutcome.Success)
        assertEquals(1, (outcome as RetryOutcome.Success).attemptCount)
        assertEquals(1, callCount)
    }

    @Test
    fun `should retry transient failures and succeed`() = runBlocking {
        val event = createTestEvent(1)
        var callCount = 0

        val outcome = retryService.executeWithRetry(
            streamedEvent = event,
            strategy = RetryStrategy(
                maxAttempts = 3,
                initialDelay = 10.milliseconds,
                maxDelay = 100.milliseconds,
                backoffMultiplier = 2.0,
            ),
        ) {
            callCount++
            if (callCount < 3) {
                throw IOException("Transient failure")
            }
            // Succeed on third attempt
        }

        assertTrue(outcome is RetryOutcome.Success)
        assertEquals(3, (outcome as RetryOutcome.Success).attemptCount)
        assertEquals(3, callCount)
    }

    @Test
    fun `should fail after exhausting all retries`() = runBlocking {
        val event = createTestEvent(2)
        var callCount = 0

        val outcome = retryService.executeWithRetry(
            streamedEvent = event,
            strategy = RetryStrategy(
                maxAttempts = 3,
                initialDelay = 10.milliseconds,
                maxDelay = 100.milliseconds,
                backoffMultiplier = 2.0,
            ),
        ) {
            callCount++
            throw IOException("Persistent failure")
        }

        assertTrue(outcome is RetryOutcome.Failure)
        val failure = outcome as RetryOutcome.Failure
        assertEquals(3, failure.attemptCount)
        assertTrue(failure.retriable)
        assertEquals("Persistent failure", failure.lastException.message)
        assertEquals(3, callCount)
    }

    @Test
    fun `should not retry non-retriable exceptions`() = runBlocking {
        val event = createTestEvent(3)
        var callCount = 0

        val outcome = retryService.executeWithRetry(
            streamedEvent = event,
            strategy = RetryStrategy.DEFAULT,
        ) {
            callCount++
            throw NullPointerException("Programming error")
        }

        assertTrue(outcome is RetryOutcome.Failure)
        val failure = outcome as RetryOutcome.Failure
        assertEquals(1, failure.attemptCount) // No retries
        assertTrue(!failure.retriable)
        assertEquals("Programming error", failure.lastException.message)
        assertEquals(1, callCount)
    }

    @Test
    fun `should process batch with mixed success and failure`() = runBlocking {
        val events = listOf(
            createTestEvent(0),
            createTestEvent(1),
            createTestEvent(2),
            createTestEvent(3),
        )

        val result = retryService.executeWithRetryBatch(
            streamedEvents = events,
            strategy = RetryStrategy(
                maxAttempts = 2,
                initialDelay = 10.milliseconds,
                maxDelay = 100.milliseconds,
                backoffMultiplier = 2.0,
            ),
        ) { event ->
            // Fail on even positions
            if (event.offset.position % 2 == 0L) {
                throw IOException("Simulated failure")
            }
        }

        assertEquals(2, result.successCount) // positions 1 and 3
        assertEquals(2, result.failures.size) // positions 0 and 2
        assertTrue(result.hasFailures)
    }

    @Test
    fun `should only retry specific exceptions when configured`() = runBlocking {
        val event = createTestEvent(4)
        var callCount = 0

        // Configure to only retry IOException
        val outcome = retryService.executeWithRetry(
            streamedEvent = event,
            strategy = RetryStrategy(
                maxAttempts = 3,
                initialDelay = 10.milliseconds,
                retryableExceptions = setOf(IOException::class.java),
            ),
        ) {
            callCount++
            // Throw a different exception
            throw RuntimeException("Not in retriable list")
        }

        assertTrue(outcome is RetryOutcome.Failure)
        val failure = outcome as RetryOutcome.Failure
        assertEquals(1, failure.attemptCount) // No retries
        assertTrue(!failure.retriable)
        assertEquals(1, callCount)
    }

    @Test
    fun `should respect NO_RETRY strategy`() = runBlocking {
        val event = createTestEvent(5)
        var callCount = 0

        val outcome = retryService.executeWithRetry(
            streamedEvent = event,
            strategy = RetryStrategy.NO_RETRY,
        ) {
            callCount++
            throw IOException("Should not retry")
        }

        assertTrue(outcome is RetryOutcome.Failure)
        val failure = outcome as RetryOutcome.Failure
        assertEquals(1, failure.attemptCount)
        assertEquals(1, callCount)
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
}
