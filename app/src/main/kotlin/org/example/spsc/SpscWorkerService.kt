package org.example.spsc

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Service

/**
 * Worker service that orchestrates the SPSC event processing pipeline.
 * Starts the coordinator on application startup and handles graceful shutdown.
 */
@Service
class SpscWorkerService(
    private val coordinator: SpscCoordinator,
    private val properties: SpscProperties,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        if (!properties.enabled) {
            logger.info("SPSC processing is disabled")
            return
        }

        logger.info("Starting SPSC event processing...")
        coordinator.start()
        logger.info("SPSC coordinator started successfully")

        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info("Shutting down SPSC coordinator...")
                coordinator.stop()
                val success = coordinator.await(10_000) // 10 seconds in milliseconds
                if (success) {
                    logger.info("SPSC coordinator shut down gracefully")
                } else {
                    logger.warn("SPSC coordinator shutdown timed out")
                }
            },
        )

        // Wait for the coordinator to finish if in test mode
        // If producerEmptyBatchThreshold > 0, coordinator will finish when all events are processed
        // If producerEmptyBatchThreshold == 0, coordinator runs indefinitely
        if (properties.producerEmptyBatchThreshold > 0) {
            logger.info("SPSC coordinator will stop after processing all events")
            // Calculate timeout based on threshold and sleep time per empty batch
            // Add extra buffer for processing time
            val estimatedTimeMs = (properties.producerEmptyBatchThreshold * properties.producerEmptyBatchSleepMs) + 30_000L
            logger.info(
                "Waiting for coordinator to finish (timeout: {}ms, based on {} batches x {}ms)...",
                estimatedTimeMs,
                properties.producerEmptyBatchThreshold,
                properties.producerEmptyBatchSleepMs,
            )
            val finished = coordinator.await(estimatedTimeMs)
            if (finished) {
                logger.info("SPSC coordinator finished, exiting application")
                System.exit(0)
            } else {
                logger.warn("SPSC coordinator await timed out after {}ms", estimatedTimeMs)
                System.exit(1)
            }
        }
        // In production mode (threshold == 0), coordinator runs indefinitely
        // Spring Boot keeps the app alive on its own
    }

    /**
     * Convenience method to manually stop the coordinator.
     * Useful for testing or programmatic shutdown.
     */
    fun stop() {
        coordinator.stop()
        coordinator.await(10_000) // 10 seconds in milliseconds
    }
}
