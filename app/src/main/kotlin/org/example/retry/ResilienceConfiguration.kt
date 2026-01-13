package org.example.retry

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Spring configuration for resilience components (Retry + DLQ). */
@Configuration
class ResilienceConfiguration {
    @Bean
    fun deadLetterQueue(): DeadLetterQueue = DeadLetterQueue(
        DeadLetterQueue.Options(
            enabled = true,
            type = DeadLetterQueue.Options.StorageType.FILE,
            filePath = "dlq/failed-events.jsonl",
        ),
    )

    @Bean
    fun resilientExecutor(
        dlqService: DeadLetterQueue,
    ): ResilientExecutor = ResilientExecutor(
        ResilientExecutor.Options(
            retryStrategy = RetryStrategy.DEFAULT,
            useDlq = true,
        ),
        dlqService,
    )
}
