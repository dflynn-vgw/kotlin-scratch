package org.example.domn.wallet.types

import org.example.core.types.Id

/** Represents a financial transaction with id, amount, and reason */
data class Transaction(
    val id: Id = Id.randomUUID(),
    val amount: TransactionAmount,
    val reason: String,
) {

    data class TransactionAmount(
        val gcAmount: Coin,
        val scAmount: Coin,
    )

}
