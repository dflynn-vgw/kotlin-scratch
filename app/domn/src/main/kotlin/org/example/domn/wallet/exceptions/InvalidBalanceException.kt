package org.example.domn.wallet.exceptions

/** Exception thrown when an operation would result in an invalid balance */
class InvalidBalanceException(message: String): Exception(message)