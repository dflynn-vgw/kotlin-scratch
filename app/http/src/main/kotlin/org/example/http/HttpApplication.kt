package org.example.http

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * HTTP application entrypoint - runs reactive WebFlux API server.
 */
@SpringBootApplication(scanBasePackages = ["org.example"])
class HttpApplication

fun main(args: Array<String>) {
    runApplication<HttpApplication>(*args)
}
