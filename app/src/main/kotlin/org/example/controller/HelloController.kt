package org.example.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class HelloController {
    @GetMapping("/hello")
    fun hello(): Mono<Map<String, Any>> = Mono.just(
        mapOf(
            "message" to "Hello, Spring WebFlux!",
            "status" to "ok",
        ),
    )
}
