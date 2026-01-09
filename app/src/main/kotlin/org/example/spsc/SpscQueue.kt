package org.example.spsc

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Thread-safe bounded queue for SPSC pattern */
internal class SpscQueue<T>(
    private val maxCapacity: Int = 100,
) {
    private val queue = LinkedBlockingQueue<T>(maxCapacity)

    /**
     * Put an item into the queue, blocking if queue is full.
     * @return true if successfully added, false if interrupted
     */
    fun put(item: T): Boolean = try {
        queue.put(item)
        true
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    /**
     * Poll an item from the queue with timeout.
     * @param timeoutMs timeout in milliseconds
     * @return the item or null if timeout occurred
     */
    fun poll(timeoutMs: Long = 1000): T? = try {
        queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    }

    /** Get current queue size */
    fun size(): Int = queue.size

    /** Check if queue is empty */
    fun isEmpty(): Boolean = queue.isEmpty()

    /** Check if queue is full */
    fun isFull(): Boolean = queue.size >= maxCapacity

    /** Clear all items from queue */
    fun clear() {
        queue.clear()
    }
}
