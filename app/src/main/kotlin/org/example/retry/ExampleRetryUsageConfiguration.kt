package org.example.retry

import org.example.events.storage.EventStream
import org.example.events.storage.StreamedEvent
import org.example.spsc.DefaultEventConsumer
import org.example.spsc.EventConsumer
import org.example.spsc.EventProducer
import org.example.spsc.SpscConfig
import org.example.spsc.SpscCoordinator
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Example configuration demonstrating how to integrate retry and DLQ
 * into your Process Manager SPSC setup.
 */
// @Configuration  // Uncomment to use this configuration
class ExampleRetryUsageConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Example 1: Simple wrapper approach
     *
     * Wrap your existing consumer with ResilienceEventConsumer for automatic
     * retry and DLQ handling with default settings.
     */
    // @Bean
    fun simpleResilientConsumer(
        retryService: RetryService,
        dlqService: DeadLetterQueueService,
    ): EventConsumer {
        // Your business logic
        val businessConsumer =
            DefaultEventConsumer { events ->
                events.forEach { event ->
                    logger.info("Processing event at position ${event.offset.position}")
                    // Your processing logic here
                    processOrderEvent(event)
                }
            }

        // Wrap with resilience features
        return ResilienceEventConsumer(
            delegate = businessConsumer,
            retryService = retryService,
            dlqService = dlqService,
            strategy = RetryStrategy.DEFAULT, // 3 attempts, exponential backoff
            failurePolicy = FailurePolicy.CONTINUE_ON_FAILURE, // Failed events go to DLQ
        )
    }

    /**
     * Example 2: Custom retry strategy
     *
     * Configure custom retry behavior for your specific needs.
     */
    // @Bean
    fun customStrategyResilientConsumer(
        retryService: RetryService,
        dlqService: DeadLetterQueueService,
    ): EventConsumer {
        val businessConsumer =
            DefaultEventConsumer { events ->
                events.forEach { processOrderEvent(it) }
            }

        return ResilienceEventConsumer(
            delegate = businessConsumer,
            retryService = retryService,
            dlqService = dlqService,
            strategy =
            RetryStrategy(
                maxAttempts = 5,
                initialDelay = 200.milliseconds,
                maxDelay = 30.seconds,
                backoffMultiplier = 1.8,
            ),
            failurePolicy = FailurePolicy.CONTINUE_ON_FAILURE,
        )
    }

    /**
     * Example 3: Direct usage of RetryService
     *
     * For fine-grained control, use RetryService directly within your consumer.
     */
    // @Bean
    fun manualRetryConsumer(
        retryService: RetryService,
        dlqService: DeadLetterQueueService,
    ): EventConsumer = object : EventConsumer {
        override suspend fun consume(streamedEvents: List<StreamedEvent>) {
            for (event in streamedEvents) {
                val outcome =
                    retryService.executeWithRetry(
                        streamedEvent = event,
                        strategy = RetryStrategy.AGGRESSIVE,
                    ) { streamedEvent ->
                        // Your processing logic
                        processOrderEvent(streamedEvent)
                    }

                when (outcome) {
                    is RetryOutcome.Success -> {
                        if (outcome.attemptCount > 1) {
                            logger.info(
                                "Event at ${event.offset.position} succeeded after ${outcome.attemptCount} attempts",
                            )
                        }
                    }

                    is RetryOutcome.Failure -> {
                        logger.error(
                            "Event at ${event.offset.position} failed after ${outcome.attemptCount} attempts",
                        )
                        dlqService.enqueue(outcome)
                    }
                }
            }
        }
    }

    /**
     * Example 4: Complete SPSC setup with resilience
     */
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

    /**
     * Example business logic - replace with your actual processing
     */
    private fun processOrderEvent(event: StreamedEvent) {
        // Simulate processing that might fail
        when (event.event.type) {
            org.example.events.Event.EventType.ORDER_PLACED -> {
                logger.info("Processing ORDER_PLACED")
                // Update read model, send notifications, etc.
            }

            org.example.events.Event.EventType.ORDER_CONFIRMED -> {
                logger.info("Processing ORDER_CONFIRMED")
                // Update read model
            }

            org.example.events.Event.EventType.ORDER_CANCELLED -> {
                logger.info("Processing ORDER_CANCELLED")
                // Update read model
            }

            org.example.events.Event.EventType.ORDER_MODIFIED -> {
                logger.info("Processing ORDER_MODIFIED")
                // Update read model
            }
        }
    }
}
