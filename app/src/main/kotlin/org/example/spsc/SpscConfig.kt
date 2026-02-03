package org.example.spsc

/**
 * Configuration for SPSC (Single Producer, Single Consumer) component.
 *
 * Controls producer/consumer behavior and batch sizing.
 */
data class SpscConfig(
    /** Number of events fetched per producer iteration */
    val producerBatchSize: Int = 10,

    /** Number of events passed to consumer per call */
    val consumerBatchSize: Int = 5,

    /** Maximum items in internal queue */
    val maxQueueDepth: Int = 100,

    /** Consumer identifier for bookmark tracking */
    val bookmarkName: String,

    /**
     * Number of consecutive empty batches before producer exits.
     * Set to 0 to disable exit behavior (producer runs indefinitely).
     * Set to > 0 to exit after N consecutive empty batches (useful for testing finite streams).
     * Default: 0 (production mode - runs indefinitely)
     */
    val producerEmptyBatchThreshold: Int = 0,

    /**
     * Sleep duration in milliseconds between empty producer batches.
     * Default: 100ms
     */
    val producerEmptyBatchSleepMs: Long = 100,
)
