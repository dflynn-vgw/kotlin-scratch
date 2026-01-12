package org.example.spsc

import org.example.events.storage.CSVEventStream
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

/**
 * Spring configuration for SPSC (Single Producer, Single Consumer) event processing.
 * Creates and wires up the EventProducer, EventConsumer, EventStream, and SpscCoordinator beans.
 */
@Configuration
@EnableConfigurationProperties(SpscProperties::class)
class SpscConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Create the EventStream bean from the configured CSV file.
     */
    @Bean
    fun eventStream(properties: SpscProperties): CSVEventStream {
        logger.info("Creating EventStream from CSV: {}", properties.csvPath)
        val csvFile = File(properties.csvPath)
        val bookmarkDir = csvFile.parent ?: "."
        return CSVEventStream(csvFile.absolutePath, bookmarkDir)
    }

    /**
     * Create the EventProducer bean.
     */
    @Bean
    fun eventProducer(): EventProducer = DefaultEventProducer

    /**
     * Create the EventConsumer bean with logging.
     * Each consumed event is logged with its position.
     */
    @Bean
    fun eventConsumer(): EventConsumer = DefaultEventConsumer { streamedEvents ->
        streamedEvents.forEach { streamedEvent ->
            logger.info(
                "Consumed event at position {}: type={}, streamId={}",
                streamedEvent.offset.position,
                streamedEvent.event.type,
                streamedEvent.event.streamId,
            )
        }
    }

    /**
     * Create the SpscCoordinator bean.
     * This is the main orchestrator that ties everything together.
     */
    @Bean
    fun spscCoordinator(
        producer: EventProducer,
        consumer: EventConsumer,
        eventStream: CSVEventStream,
        properties: SpscProperties,
    ): SpscCoordinator {
        logger.info("Creating SpscCoordinator with config: {}", properties)
        val config = SpscConfig(
            producerBatchSize = properties.producerBatchSize,
            consumerBatchSize = properties.consumerBatchSize,
            maxQueueDepth = properties.maxQueueDepth,
            bookmarkName = properties.bookmarkName,
            producerEmptyBatchThreshold = properties.producerEmptyBatchThreshold,
        )
        return SpscCoordinator(producer, consumer, eventStream, config)
    }
}
