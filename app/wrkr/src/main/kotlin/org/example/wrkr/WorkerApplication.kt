package org.example.wrkr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Worker application entrypoint - runs background services without HTTP server.
 */
@SpringBootApplication(scanBasePackages = ["org.example"])
@EnableScheduling
class WorkerApplication

fun main(args: Array<String>) {
    runApplication<WorkerApplication>(*args)
}
