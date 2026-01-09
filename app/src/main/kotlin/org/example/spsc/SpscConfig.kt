package org.example.spsc

/** Configuration for SPSC (Single Producer, Single Consumer) component */
data class SpscConfig(
    /** Number of events fetched per producer iteration */
    val producerBatchSize: Int = 10,

    /** Number of events passed to consumer per call */
    val consumerBatchSize: Int = 5,

    /** Maximum items in internal queue */
    val maxQueueDepth: Int = 100,

    /** Consumer identifier for bookmark tracking */
    val bookmarkName: String,
)
