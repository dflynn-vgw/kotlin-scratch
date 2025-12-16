package org.example.http

import org.example.domn.Greeting

/**
 * Response DTO for greeting endpoints.
 * Anti-corruption layer between domain and API contract.
 */
data class GreetingResponse(
    val message: String,
    val status: String,
) {
    companion object {
        fun from(greeting: Greeting): GreetingResponse = GreetingResponse(
            message = greeting.message,
            status = greeting.status,
        )
    }
}
