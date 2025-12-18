package org.example.domn.wallet.types

/** Value class representing an amount in cents */
@JvmInline
value class Cents(val value: Long) {
    init {
        require(value >= 0) { "Cents value must be non-negative" }
    }

    operator fun plus(other: Cents): Cents = Cents(this.value + other.value)
    operator fun minus(other: Cents): Cents {
        val result = this.value - other.value
        require(result >= 0) { "Resulting Cents value must be non-negative" }
        return Cents(result)
    }

    operator fun compareTo(other: Cents): Int = this.value.compareTo(other.value)

    companion object {
        val ZERO = Cents(0)
    }
}