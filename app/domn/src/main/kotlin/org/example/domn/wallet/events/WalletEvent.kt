package org.example.domn.wallet.events

import org.example.core.types.Id
import org.example.domn.wallet.aggregates.Wallet
import org.example.domn.wallet.types.Coin
import org.example.domn.wallet.types.Meta
import org.example.domn.wallet.types.Transaction

/** Sealed class representing events related to Wallet aggregates */
sealed class WalletEvent(
    override val id: Id,
    override val type: String,
    override val stream: String,
    override val version: Int,
    override val snapshot: Wallet.Snapshot,
    override val metadata: Meta,
    open val transaction: Transaction,
) : Event<Wallet.Snapshot> {
    init {
        require(version >= 0) { "Event version must be non-negative" }
    }

    /** Event representing the creation of a Wallet */
    data class WalletCreated(
        override val id: Id,
        override val snapshot: Wallet.Snapshot,
        override val metadata: Meta = Meta.DEFAULT,
        override val transaction: Transaction,
    ) : WalletEvent(
        id = id,
        type = "WalletCreated",
        stream = "Wallet-${snapshot.id}",
        version = 1,
        snapshot = snapshot,
        metadata = metadata,
        transaction = transaction
    )

    /** Event representing a credit to the Wallet */
    data class WalletCredited(
        override val id: Id = Id.randomUUID(),
        override val version: Int,
        override val snapshot: Wallet.Snapshot,
        override val metadata: Meta = Meta.DEFAULT,
        override val transaction: Transaction,
    ) : WalletEvent(
        id = id,
        type = "WalletCredited",
        stream = "Wallet-${snapshot.id}",
        version = version,
        snapshot = snapshot,
        metadata = metadata,
        transaction = transaction
    )

    /** Event representing a debit from the Wallet */
    data class WalletDebited(
        override val id: Id = Id.randomUUID(),
        override val version: Int,
        override val snapshot: Wallet.Snapshot,
        override val metadata: Meta = Meta.DEFAULT,
        override val transaction: Transaction,
    ) : WalletEvent(
        id = id,
        type = "WalletDebited",
        stream = "Wallet-${snapshot.id}",
        version = version,
        snapshot = snapshot,
        metadata = metadata,
        transaction = transaction
    )
}