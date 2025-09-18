package org.example.sudoko

/** Internal state of a Sudoku puzzle */
class State(val initial: Array<Int> = Array(CELLS) { EMPTY }, val working: Array<Int> = Array(CELLS) { EMPTY }) {
    /** Pretty print the state as a multi-line formatted string */
    fun toPrettyString(): String {
        val sb = StringBuilder()
        for (i in working.indices) {
            if (i > 0) {
                if (i % 27 == 0) {
                    sb.appendLine("\n- - - + - - - + - - -")
                } else if (i % 9 == 0) {
                    sb.appendLine()
                } else if (i % 3 == 0) {
                    sb.append(" | ")
                } else {
                    sb.append(' ')
                }
            }

            sb.append(working[i])
        }
        return sb.toString()
    }

    override fun toString() = working.joinToString("")

    companion object {
        const val CELLS = 81
        const val EMPTY = 0
        val EMPTY_STATE_STRING = "$EMPTY".repeat(CELLS)

        /** Create State from a string of 81 characters (0-9) */
        fun fromString(state: String): State {
            val digits = state.filter { it.isDigit() }.map { it.toString().toInt() }.toTypedArray()
            require(digits.size == CELLS) { "State must contain exactly $CELLS digits (0-9)" }
            return State(digits, digits.copyOf())
        }
    }
}
