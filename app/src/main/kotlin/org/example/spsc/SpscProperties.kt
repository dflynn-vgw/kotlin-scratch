package org.example.spsc

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SPSC (Single Producer, Single Consumer) configuration properties.
 * Binds to app.spsc.* configuration in application.yml.
 *
 * All properties support environment variable override:
 * - APP_SPSC_CSV_PATH
 * - APP_SPSC_BOOKMARK_NAME
 * - APP_SPSC_PRODUCER_BATCH_SIZE
 * - APP_SPSC_CONSUMER_BATCH_SIZE
 * - APP_SPSC_MAX_QUEUE_DEPTH
 * - APP_SPSC_PRODUCER_EMPTY_BATCH_THRESHOLD
 * - APP_SPSC_ENABLED
 */
@ConfigurationProperties(prefix = "app.spsc")
data class SpscProperties(
    /** Path to the CSV file containing events */
    var csvPath: String = "",

    /** Name for the bookmark (consumer progress checkpoint) */
    var bookmarkName: String = "default-consumer",

    /** Batch size for event production */
    var producerBatchSize: Int = 2,

    /** Batch size for event consumption */
    var consumerBatchSize: Int = 2,

    /** Maximum queue depth between producer and consumer */
    var maxQueueDepth: Int = 5,

    /** Number of consecutive empty batches before producer exits (0 = indefinite) */
    var producerEmptyBatchThreshold: Int = 0,

    /** Whether to enable SPSC processing on startup */
    var enabled: Boolean = true,
)
