package org.example.domn.wallet.types

import org.example.domn.wallet.exceptions.InvalidBalanceException

/** Data class representing the balance of the Wallet in different coin types */
data class Balance(
    val goldCoin: Coin,
    val sweepCoin: Coin,
) {
    /** Credits the specified amount to the balance */
    fun credit(amount: Coin): Balance {
        return when (amount.type) {
            Coin.CoinType.GC -> this.copy(goldCoin = Coin(Coin.CoinType.GC, this.goldCoin.value + amount.value))
            Coin.CoinType.SC -> this.copy(sweepCoin = Coin(Coin.CoinType.SC, this.sweepCoin.value + amount.value))
        }
    }

    /** Debits the specified amount from the balance (fails if insufficient balance for CoinType) */
    fun debit(amount: Coin): Result<Balance> {
        return when (amount.type) {
            Coin.CoinType.GC -> {
                if (amount.value > this.goldCoin.value) {
                    Result.failure(InvalidBalanceException("Insufficient Gold Coin balance"))
                } else {
                    Result.success(this.copy(goldCoin = Coin(Coin.CoinType.GC, this.goldCoin.value - amount.value)))
                }
            }

            Coin.CoinType.SC -> {
                if (amount.value > this.sweepCoin.value) {
                    Result.failure(InvalidBalanceException("Insufficient Sweep Coin balance"))
                } else {
                    Result.success(this.copy(sweepCoin = Coin(Coin.CoinType.SC, this.sweepCoin.value - amount.value)))
                }

            }
        }
    }

    companion object {
        /** A Balance instance with zero amounts for both coin types */
        val ZERO = Balance(Coin.ZERO_GC, Coin.ZERO_SC)
    }
}