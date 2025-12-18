package org.example.domn.wallet.aggregates

import org.example.core.types.Id
import org.example.domn.common.Identifiable
import org.example.domn.common.Snapshotable
import org.example.domn.wallet.events.WalletEvent
import org.example.domn.wallet.types.Balance
import org.example.domn.wallet.types.Meta
import org.example.domn.wallet.types.Transaction

/** Represents a Wallet aggregate with an ID, associated transaction ID, version, and balance */
class Wallet(
    /** Unique identifier for the wallet */
    override val id: Id = Id.randomUUID(),
    /** Version number for concurrency control */
    val version: Int,
    /** Current balance of the wallet */
    val balance: Balance,

    ) : Identifiable, Snapshotable {
    init {
        require(version >= 0) { "Version must be non-negative" }
    }

    /** Credits the wallet with the specified amount */
    fun credit(transaction: Transaction, meta: Meta): Result<Wallet> {
        if (this.version == 0) {
            return Result.failure(IllegalStateException("Wallet with id ${this.id} has not been created yet"))
        }

        val (gc, sc) = transaction.amount
        val wallet = Wallet(
            id = this.id,
            version = this.version + 1,
            balance = this.balance
                .credit(gc)
                .credit(sc)
        )

        apply(
            event = WalletEvent.WalletCredited(
                version = wallet.version,
                snapshot = wallet.snapshot(),
                metadata = meta,
                transaction = transaction,
            ), isNew = true
        )

        // Business logic to credit the wallet
        return Result.success(wallet)
    }

    /** Debits the wallet by the specified amount (unless insufficient balance) */
    fun debit(transaction: Transaction, meta: Meta): Result<Wallet> {
        if (this.version == 0) {
            return Result.failure(IllegalStateException("Wallet with id ${this.id} has not been created yet"))
        }

        val (gc, sc) = transaction.amount

        val result = this.balance.debit(gc).fold(
            onFailure = { Result.failure(it) },
            onSuccess = {
                this.balance.debit(sc).fold(
                    onFailure = { Result.failure(it) },
                    onSuccess = { updatedBalance ->
                        Result.success(
                            Wallet(
                                id = this.id,
                                version = this.version + 1,
                                balance = updatedBalance
                            )
                        )
                    }
                )
            }
        )

        return result.fold(
            onFailure = { Result.failure(it) },
            onSuccess = { wallet ->
                apply(
                    event = WalletEvent.WalletDebited(
                        version = wallet.version,
                        snapshot = wallet.snapshot(),
                        metadata = meta,
                        transaction = transaction,
                    ), isNew = true
                )

                // Business logic to debit the wallet
                Result.success(wallet)
            }
        )
    }

    /** Creates a snapshot of the current state of the Wallet */
    override fun snapshot(): Snapshot = Snapshot(
        id = this.id,
        version = this.version,
        balance = this.balance,
    )

    /** Data class representing a snapshot of the Wallet's state */
    data class Snapshot(
        override val id: Id,
        val version: Int,
        val balance: Balance,
    ) : Identifiable

    /* ### Event Application (State Changes) ### */
    private val uncommittedEvents = mutableListOf<WalletEvent>()

    private fun apply(event: WalletEvent, isNew: Boolean = false): Wallet {
        val wallet = when (event) {
            is WalletEvent.WalletCreated -> create(event.id) /* New wallet creation */
            is WalletEvent.WalletCredited -> this.credit(event.transaction, event.metadata).getOrThrow()
            is WalletEvent.WalletDebited -> this.debit(event.transaction, event.metadata).getOrThrow()
        }

        if (isNew) {
            uncommittedEvents.add(event)
        }

        return wallet

    }

    companion object {
        /** Factory method to create a Wallet instance from a Snapshot */
        fun fromSnapshot(snapshot: Snapshot): Wallet = Wallet(
            id = snapshot.id,
            version = snapshot.version,
            balance = snapshot.balance
        )

        /** Factory method to create a new Wallet instance */
        fun create(
            id: Id = Id.randomUUID(),
            balance: Balance = Balance.ZERO,
        ): Wallet = Wallet(
            id = id,
            version = 1,
            balance = balance
        )
    }
}
