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
