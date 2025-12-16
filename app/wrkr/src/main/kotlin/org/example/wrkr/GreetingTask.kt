package org.example.wrkr

import org.example.domn.Greeting
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Sample scheduled task that runs in the background.
 */
@Component
class GreetingTask {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    fun logGreeting() {
        val greeting = Greeting.hello("Worker")
        logger.info("Scheduled task: ${greeting.message} [${greeting.status}]")
    }
}
