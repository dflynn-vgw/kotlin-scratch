package org.example.domn.wallet.types

/** Represents a Coin of type GC or SC with a cents value */
data class Coin(val type: CoinType, val value: Cents) {

    /** Gold Coin (GC) or Sweeps Coin (SC) */
    enum class CoinType { GC, SC }

    companion object {
        val ZERO_GC = GC()
        val ZERO_SC = SC()
        @Suppress("FunctionName")
        fun GC(value: Cents = Cents.ZERO) = Coin(CoinType.GC, value)
        @Suppress("FunctionName")
        fun SC(value: Cents = Cents.ZERO) = Coin(CoinType.SC, value)
    }

}
