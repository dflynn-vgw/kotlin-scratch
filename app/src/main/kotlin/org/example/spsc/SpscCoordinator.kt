package org.example.spsc

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.example.events.storage.EventStream
import org.example.events.storage.StreamedEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    private val eventStream: EventStream,
    private val config: SpscConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val isRunning = AtomicBoolean(false)
    private val queue = SpscQueue<StreamedEvent>(config.maxQueueDepth)
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
            // Get initial position from bookmark
            var producerPosition = runBlocking {
                val bookmark = eventStream.getBookmark(config.bookmarkName)
                bookmark?.position ?: 0L
            }
            logger.debug("Producer starting from position: {}", producerPosition)
            var totalEventCount = 0
            var consecutiveEmptyBatches = 0

            while (isRunning.get()) {
                // Produce a batch of events starting from current position
                var batchEventCount = 0

                runBlocking {
                    producer.produce(eventStream, producerPosition, config.producerBatchSize)
                        .collect { streamedEvent ->
                            logger.debug("Producer emitted event at position {}", streamedEvent.offset.position)
                            batchEventCount++

                            // Check if we should stop
                            if (!isRunning.get()) {
                                logger.debug("Producer stopping")
                                return@collect
                            }

                            // Put streamed event in queue, blocking if full
                            // queue.put() blocks until there's space
                            queue.put(streamedEvent)
                            logger.debug("Producer queued event at position {}", streamedEvent.offset.position)
                            totalEventCount++
                            producerPosition = streamedEvent.offset.position + 1
                        }
                }

                // If no events were produced in this batch
                if (batchEventCount == 0) {
                    consecutiveEmptyBatches++
                    logger.debug("Producer: empty batch #{}, position: {}", consecutiveEmptyBatches, producerPosition)

                    // Check if we should exit based on threshold
                    // threshold=0 means run indefinitely; threshold>0 means exit after N empty batches
                    if (config.producerEmptyBatchThreshold > 0 && consecutiveEmptyBatches >= config.producerEmptyBatchThreshold) {
                        logger.info("Producer: no events available, exiting after {} empty batches. Total produced: {}", consecutiveEmptyBatches, totalEventCount)
                        break
                    }

                    // Sleep briefly before retrying
                    Thread.sleep(100)
                } else {
                    // Reset empty batch counter when we produce events
                    consecutiveEmptyBatches = 0
                }
            }
            logger.info("Producer finished - total events produced: {}", totalEventCount)
        } catch (e: Exception) {
            logger.error("Producer error", e)
            if (isRunning.get()) {
                e.printStackTrace()
            }
        }
    }

    private fun runConsumer() {
        try {
            while (isRunning.get()) {
                // Poll streamed events from queue in batches
                val batch = mutableListOf<StreamedEvent>()

                // Try to collect a batch
                for (i in 0 until config.consumerBatchSize) {
                    val streamedEvent = queue.poll(timeoutMs = 100)
                    if (streamedEvent != null) {
                        logger.debug("Consumer polled event at position {}", streamedEvent.offset.position)
                        batch.add(streamedEvent)
                    } else if (batch.isNotEmpty()) {
                        break // Got some events, process them
                    } else if (!isRunning.get()) {
                        logger.debug("Consumer stopping")
                        return // Coordinator stopping
                    }
                    // If queue empty and we haven't collected any events yet, loop again
                }

                if (batch.isNotEmpty()) {
                    logger.debug("Consumer processing batch of {} events", batch.size)
                    try {
                        // Consume the batch of streamed events
                        runBlocking {
                            consumer.consume(batch)
                        }
                        logger.debug("Consumer batch processed successfully")

                        // On success, advance bookmark to position after last event
                        val lastEventPosition = batch.maxOf { it.offset.position } + 1L
                        runBlocking {
                            eventStream.saveBookmark(config.bookmarkName, lastEventPosition)
                        }
                        logger.debug("Bookmark advanced to {}", lastEventPosition)
                    } catch (e: Exception) {
                        // On failure, bookmark is NOT advanced
                        logger.error("Consumer batch failed", e)
                        if (isRunning.get()) {
                            e.printStackTrace() // Log error - replace with proper logging
                        }
                        // Could implement retry logic here
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Consumer error", e)
            if (isRunning.get()) {
                e.printStackTrace() // Log error - replace with proper logging
            }
        }
    }
}
