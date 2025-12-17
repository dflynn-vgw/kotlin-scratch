package org.example.domn.wallet.aggregates

import org.example.core.types.Epoch
import org.example.core.types.Id
import org.example.core.types.now
import org.example.domn.common.Identifiable
import org.example.domn.common.Snapshotable
import org.example.domn.wallet.types.Balance
import org.example.domn.wallet.types.Coin

/** Represents a Wallet aggregate with an ID, associated transaction ID, version, and balance */
class Wallet(
    /** Unique identifier for the wallet */
    override val id: Id = Id.randomUUID(),
    /** Timestamp of the wallet's last update */
    val timestamp: Epoch = now(),
    /** Associated transaction identifier */
    val transactionId: Id,
    /** Version number for concurrency control */
    val version: Int,
    /** Current balance of the wallet */
    val balance: Balance,

    ) : Identifiable, Snapshotable {
    init {
        require(version >= 0) { "Version must be non-negative" }
    }

    /** Credits the wallet with the specified amount */
    fun credit(amount: Coin, transactionId: Id): Result<Wallet> {
        if (this.version == 0) {
            return Result.failure(IllegalStateException("Wallet with id ${this.id} has not been created yet"))
        }

        // Business logic to credit the wallet
        return Result.success(
            Wallet(
                id = this.id,
                transactionId = this.transactionId,
                version = this.version + 1,
                balance = this.balance.credit(amount)
            )
        )
    }

    /** Debits the wallet by the specified amount (unless insufficient balance) */
    fun debit(amount: Coin, transactionId: Id): Result<Wallet> {
        if (this.version == 0) {
            return Result.failure(IllegalStateException("Wallet with id ${this.id} has not been created yet"))
        }

        return this.balance.debit(amount).fold(
            onFailure = { Result.failure(it) },
            onSuccess = {
                Result.success(
                    Wallet(
                        id = this.id,
                        transactionId = this.transactionId,
                        version = this.version + 1,
                        balance = it
                    )
                )
            }
        )
    }

    /** Creates a snapshot of the current state of the Wallet */
    override fun snapshot(): Snapshot = Snapshot(
        id = this.id,
        timestamp = this.timestamp,
        transactionId = this.transactionId,
        version = this.version,
        balance = this.balance,
    )

    /** Data class representing a snapshot of the Wallet's state */
    data class Snapshot(
        override val id: Id,
        val timestamp: Epoch,
        val transactionId: Id,
        val version: Int,
        val balance: Balance,
    ) : Identifiable

    companion object {
        /** Factory method to create a Wallet instance from a Snapshot */
        fun fromSnapshot(snapshot: Snapshot): Wallet = Wallet(
            id = snapshot.id,
            transactionId = snapshot.transactionId,
            version = snapshot.version,
            balance = snapshot.balance
        )

        /** Factory method to create a new Wallet instance */
        fun create(
            id: Id = Id.randomUUID(),
            transactionId: Id = Id.randomUUID(),
            balance: Balance = Balance.ZERO,
        ): Wallet = Wallet(
            id = id,
            transactionId = transactionId,
            version = 1,
            balance = balance
        )
    }
}
