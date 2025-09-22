package org.example.sudoko

/** Result of an operation, either Success or Failure with a reason */
sealed class Outcome {
    object Success : Outcome()
    data class Failure(val reason: String) : Outcome()
}
