package org.example.retry

/** Configuration for DLQ behavior. */
data class DeadLetterQueueOptions(
    val enabled: Boolean = true,
    val type: StorageType = StorageType.FILE,
    val filePath: String = "dlq/failed-events.jsonl",
) {
    enum class StorageType {
        FILE,
        LOG,
        // DATABASE, // Future implementation for production use
        // KAFKA,   // Future implementation for production use
    }
}
