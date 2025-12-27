package org.example.domn.wallet.aggregates

import org.example.core.types.Id
import org.example.domn.common.Identifiable
import org.example.domn.common.Snapshotable
import org.example.domn.wallet.events.Event
import org.example.domn.wallet.events.WalletEvent
import org.example.domn.wallet.types.Balance
import org.example.domn.wallet.types.Meta
import org.example.domn.wallet.types.Transaction
import org.example.domn.wallet.types.Version

/** Represents a Wallet aggregate with an ID, associated transaction ID, version, and balance */
class Wallet(
  /** Unique identifier for the wallet */
  override val id: Id = Id.randomUUID(),
  /** Version number for concurrency control */
  val version: Version,
  /** Current balance of the wallet */
  val balance: Balance,

  ) : Identifiable, Aggregate<Wallet>() {

  /** Credits the wallet with the specified amount */
  fun credit(transaction: Transaction, meta: Meta): Result<Wallet> {
    if (this.version == Version.ZERO) {
      return Result.failure(IllegalStateException("Wallet with id ${this.id} has not been created yet"))
    }

    val (gc, sc) = transaction.amount
    val wallet = Wallet(
      id = this.id,
      version = this.version.increment(),
      balance = this.balance
        .credit(gc)
        .credit(sc)
    )

    apply(
      event = WalletEvent.WalletCredited(
        version = wallet.version,
        snapshot = wallet,
        metadata = meta,
        transaction = transaction,
      ), isNew = true
    )

    // Business logic to credit the wallet
    return Result.success(wallet)
  }

  /** Debits the wallet by the specified amount (unless insufficient balance) */
  fun debit(transaction: Transaction, meta: Meta): Result<Wallet> {
    if (this.version == Version.ZERO) {
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
                version = this.version.increment(),
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
            snapshot = wallet,
            metadata = meta,
            transaction = transaction,
          ), isNew = true
        )

        // Business logic to debit the wallet
        Result.success(wallet)
      }
    )
  }


  /* ### Event Application (State Changes) ### */

  /** Applies an event to the Wallet aggregate, updating its state */
  override fun apply(event: Event<Wallet>, isNew: Boolean): Wallet {
    val wallet = when (event) {
      is WalletEvent.WalletCreated -> create(event.id) /* New wallet creation */
      is WalletEvent.WalletCredited -> this.credit(event.transaction, event.metadata).getOrThrow()
      is WalletEvent.WalletDebited -> this.debit(event.transaction, event.metadata).getOrThrow()
      else -> {
        throw IllegalArgumentException("Unsupported event type: ${event.type}")
      }
    }

    if (isNew) {
      stage(event)
    }

    return wallet

  }

  /** Saves a list of events to the event store (not yet implemented, called from commit()) */
  override fun save(events: List<Event<Wallet>>): Result<Wallet> {
    TODO("Not yet implemented")
  }

  companion object {
    /** Factory method to create a new Wallet instance */
    fun create(
      id: Id = Id.randomUUID(),
      balance: Balance = Balance.ZERO,
    ): Wallet = Wallet(
      id = id,
      version = Version(1),
      balance = balance
    )
  }
}
