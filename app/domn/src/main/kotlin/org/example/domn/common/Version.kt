package org.example.domn.common

/** Value class representing a Version (positive integer) */
@JvmInline
value class Version(val value: Int = ZERO.value) {
  init {
    require(value >= 0) { "Verison must be non-negative" }
  }

  /** Increments the version by one */
  fun increment(): Version = Version(this.value + 1)
  operator fun plus(other: Version): Version = Version(this.value + other.value)
  operator fun minus(other: Version): Version {
    val result = this.value - other.value
    require(result >= 0) { "Resulting Version value must be non-negative" }
    return Version(result)
  }

  operator fun compareTo(other: Version): Int = this.value.compareTo(other.value)

  companion object {
    val ZERO = Version(0)
  }
}