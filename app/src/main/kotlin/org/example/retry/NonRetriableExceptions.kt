package org.example.retry

/** Exceptions that should NOT be retried as they indicate programming errors or unrecoverable states. */
object NonRetriableExceptions {
    /** Set of exception types that should never be retried. */
    val TYPES: Set<Class<out Throwable>> = setOf<Class<out Throwable>>(
        // Kotlin exceptions
        NullPointerException::class.java,
        ClassCastException::class.java,
        IndexOutOfBoundsException::class.java,
        IllegalArgumentException::class.java,
        IllegalStateException::class.java,
        NoSuchElementException::class.java,
        NumberFormatException::class.java,
        UninitializedPropertyAccessException::class.java,
        TypeCastException::class.java,

        // Security exceptions - should never retry
        SecurityException::class.java,

        // Validation failures - data is wrong, retry won't help
        // Add your custom validation exceptions here if needed
    )

    /**
     * Check if an exception is retriable based on its type.
     * @param exception The exception to check
     * @param retryableExceptions Optional set of explicitly retriable exceptions
     * @return true if the exception should be retried, false otherwise
     */
    fun isRetriable(
        exception: Throwable,
        retryableExceptions: Set<Class<out Throwable>> = emptySet(),
    ): Boolean {
        // If specific retriable exceptions are provided, only those are retriable
        if (retryableExceptions.isNotEmpty()) {
            return retryableExceptions.any { it.isInstance(exception) }
        }

        // Otherwise, everything except non-retriable exceptions is retriable
        return TYPES.none { it.isInstance(exception) }
    }
}
