package org.example.spsc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.events.Event
import org.example.events.storage.Bookmark
import org.example.events.storage.StreamingEventStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * SPSC (Single Producer, Single Consumer) Coordinator.
 * Orchestrates a producer and consumer processing events through a bounded queue.
 *
 * The producer fetches events from StreamingEventStore and puts them in the queue.
 * The consumer polls events from the queue in batches and processes them.
 * The coordinator manages both on separate threads and handles bookmark advancement.
 */
class SpscCoordinator(
    private val producer: EventProducer,
    private val consumer: EventConsumer,
    private val eventStore: StreamingEventStore,
    private val config: SpscConfig,
) {
    private val isRunning = AtomicBoolean(false)
    private val queue = SpscQueue<Event<Any>>(config.maxQueueDepth)
    private val executor = Executors.newFixedThreadPool(2)
    private var producerFuture: java.util.concurrent.Future<*>? = null
    private var consumerFuture: java.util.concurrent.Future<*>? = null
    private val shutdownLatch = CountDownLatch(2)

    /**
     * Start the SPSC coordinator.
     * Spawns producer and consumer on separate threads.
     */
    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            throw IllegalStateException("Coordinator is already running")
        }

        producerFuture = executor.submit {
            try {
                runProducer()
            } finally {
                shutdownLatch.countDown()
            }
        }

        consumerFuture = executor.submit {
            try {
                runConsumer()
            } finally {
                shutdownLatch.countDown()
            }
        }
    }

    /**
     * Stop the SPSC coordinator gracefully.
     * Waits for both producer and consumer to finish.
     */
    fun stop() {
        isRunning.set(false)
    }

    /**
     * Await coordinator shutdown.
     * @param timeoutMs timeout in milliseconds
     * @return true if shutdown completed, false if timeout
     */
    fun await(timeoutMs: Long = 30000): Boolean = try {
        shutdownLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun runProducer() {
        try {
            // Get starting position from bookmark
            var currentPosition = runBlocking {
                val bookmark = eventStore.getBookmark(config.bookmarkName)
                bookmark?.position ?: 0L
            }

            // Produce events continuously
            runBlocking {
                producer.produce(eventStore, currentPosition, config.producerBatchSize)
                    .collect { event ->
                        // Check if we should stop
                        if (!isRunning.get()) {
                            return@collect
                        }

                        // Put event in queue, blocking if full
                        queue.put(event)
                        currentPosition++
                    }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                e.printStackTrace() // Log error - replace with proper logging
            }
        }
    }

    private fun runConsumer() {
        try {
            while (isRunning.get()) {
                // Poll events from queue in batches
                val batch = mutableListOf<Event<Any>>()

                // Try to collect a batch
                for (i in 0 until config.consumerBatchSize) {
                    val event = queue.poll(timeoutMs = 100)
                    if (event != null) {
                        batch.add(event)
                    } else if (batch.isNotEmpty()) {
                        break // Got some events, process them
                    } else if (!isRunning.get()) {
                        return // Coordinator stopping
                    }
                    // If queue empty and we haven't collected any events yet, loop again
                }

                if (batch.isNotEmpty()) {
                    try {
                        // Get current bookmark
                        val currentBookmark = runBlocking {
                            eventStore.getBookmark(config.bookmarkName)
                                ?: Bookmark(config.bookmarkName, 0L)
                        }

                        // Consume the batch
                        runBlocking {
                            consumer.consume(batch, currentBookmark)
                        }

                        // On success, advance bookmark to position after last event
                        val lastEventPosition = currentBookmark.position + batch.size.toLong()
                        runBlocking {
                            eventStore.saveBookmark(config.bookmarkName, lastEventPosition)
                        }
                    } catch (e: Exception) {
                        // On failure, bookmark is NOT advanced
                        if (isRunning.get()) {
                            e.printStackTrace() // Log error - replace with proper logging
                        }
                        // Could implement retry logic here
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                e.printStackTrace() // Log error - replace with proper logging
            }
        }
    }
}
