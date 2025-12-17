package org.example.domn.wallet.types

import org.example.core.types.Cents

/** Represents a Coin of type GC or SC with a decimal value */
data class Coin(val type: CoinType, val value: Cents) {

    init {
        require(value > 0) { "Coin value must be positive" }
    }

    /** Gold Coin (GC) or Sweeps Coin (SC) */
    enum class CoinType { GC, SC }

    companion object {
        val ZERO_GC = Coin(CoinType.GC, 0)
        val ZERO_SC = Coin(CoinType.SC, 0)
    }

}
