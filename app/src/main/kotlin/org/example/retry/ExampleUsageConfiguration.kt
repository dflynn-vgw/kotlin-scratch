
@file:Suppress("ktlint:standard:no-wildcard-imports")

package org.example.retry

import org.example.events.Event
import org.example.events.storage.EventStream
import org.example.events.storage.StreamedEvent
import org.example.spsc.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Example configuration demonstrating how to integrate retry and DLQ
 * into your Process Manager SPSC setup using ResilientExecutor.
 */
// @Configuration  // Uncomment to use this configuration
class ExampleUsageConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Example 1: Simple per-event processing with ResilientExecutor
     *
     * The executor handles retry and DLQ logic, keeping your consumer code clean.
     */
    // @Bean
    fun simpleResilientConsumer(
        resilientExecutor: ResilientExecutor,
    ): EventConsumer = DefaultEventConsumer { events ->
        events.forEach { event ->
            resilientExecutor.execute("simpleResilientConsumer", event) { processOrderEvent(event) }
        }
    }

    /**
     * Example 4: Custom ResilientExecutor with specific retry strategy
     */
    // @Bean
    fun customResilientExecutor(
        dlqService: DeadLetterQueue,
    ): ResilientExecutor = ResilientExecutor(

        ResilientExecutor.Options(
            retryStrategy = RetryStrategy(
                maxAttempts = 5,
                initialDelay = 200.milliseconds,
                maxDelay = 5.seconds,
                backoffMultiplier = 2.0,
                retryableExceptions = setOf(
                    // Specify exception types that should trigger a retry
                    // e.g., TransientDatabaseException::class.java,
                ),
            ),
            useDlq = true,
        ),
        dlqService = dlqService,
    )

    // @Bean
    fun resilientSpscCoordinator(
        resilientConsumer: EventConsumer, // Use one of the consumers above
        producer: EventProducer,
        eventStream: EventStream,
        config: SpscConfig,
    ): SpscCoordinator =
        SpscCoordinator(
            producer = producer,
            consumer = resilientConsumer,
            eventStream = eventStream,
            config = config,
        )

    /** Example business logic - replace with your actual processing. */
    private fun processOrderEvent(event: StreamedEvent) {
        // Simulate processing that might fail
        when (event.event.type) {
            Event.EventType.ORDER_PLACED -> {
                logger.info("Processing ORDER_PLACED")
                // Update read model, send notifications, etc.
            }

            Event.EventType.ORDER_CONFIRMED -> {
                logger.info("Processing ORDER_CONFIRMED")
                // Update read model
            }

            Event.EventType.ORDER_CANCELLED -> {
                logger.info("Processing ORDER_CANCELLED")
                // Update read model
            }

            Event.EventType.ORDER_MODIFIED -> {
                logger.info("Processing ORDER_MODIFIED")
                // Update read model
            }
        }
    }
}
