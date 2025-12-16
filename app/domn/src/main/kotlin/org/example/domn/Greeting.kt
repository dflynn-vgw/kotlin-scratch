package org.example.domn

/**
 * Domain model representing a greeting message.
 * Pure domain logic with no infrastructure concerns.
 */
data class Greeting(
    val message: String,
    val status: String = "ok"
) {
    init {
        require(message.isNotBlank()) { "Greeting message cannot be blank" }
    }

    companion object {
        fun hello(name: String = "World"): Greeting =
            Greeting(message = "Hello, $name!", status = "ok")
    }
}
