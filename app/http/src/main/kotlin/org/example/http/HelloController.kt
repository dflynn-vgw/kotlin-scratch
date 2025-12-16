package org.example.http

import org.example.domn.Greeting
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class HelloController {
    @GetMapping("/hello")
    fun hello(@RequestParam(defaultValue = "Spring WebFlux") name: String): Mono<Greeting> = Mono.just(Greeting.hello(name))
}
