package org.example.retry

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Spring configuration for resilience components (Retry + DLQ). */
@Configuration
class ResilienceConfiguration {
    @Bean
    fun deadLetterQueueOptions(): DeadLetterQueueOptions = DeadLetterQueueOptions(
        enabled = true,
        type = DeadLetterQueueOptions.StorageType.FILE,
        filePath = "dlq/failed-events.jsonl",
    )

    @Bean
    fun deadLetterQueueService(
        options: DeadLetterQueueOptions,
    ): DeadLetterQueueService = DeadLetterQueueService(options)

    @Bean
    fun retryService(): RetryService = RetryService()
}

// Example configuration usage - see README.md for complete examples
