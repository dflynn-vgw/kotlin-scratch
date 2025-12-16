package org.example.http

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpApplicationTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `hello endpoint returns greeting`() {
        webTestClient.get().uri("/hello")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Hello, Spring WebFlux!")
            .jsonPath("$.status").isEqualTo("ok")
    }

    @Test
    fun `hello endpoint with name parameter`() {
        webTestClient.get().uri("/hello?name=Kotlin")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Hello, Kotlin!")
            .jsonPath("$.status").isEqualTo("ok")
    }
}
