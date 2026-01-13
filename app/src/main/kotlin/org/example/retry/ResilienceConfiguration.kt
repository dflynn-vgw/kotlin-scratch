package org.example.retry

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Spring configuration for resilience components (Retry + DLQ). */
@Configuration
class ResilienceConfiguration {
    @Bean
    fun deadLetterQueueService(): DeadLetterQueueService = DeadLetterQueueService(
        DeadLetterQueueOptions(
            enabled = true,
            type = DeadLetterQueueOptions.StorageType.FILE,
            filePath = "dlq/failed-events.jsonl",
        ),
    )

    @Bean
    fun resilientExecutor(
        dlqService: DeadLetterQueueService,
    ): ResilientExecutor = DefaultResilientExecutor(RetryStrategy.DEFAULT, dlqService, useDlq = true)
}
