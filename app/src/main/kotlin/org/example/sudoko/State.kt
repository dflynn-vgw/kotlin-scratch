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

    /** Get a specific row (0-8) as an array of 9 integers */
    fun getRow(row: Int): Array<Int> {
        require(row in 0..8) { "Row must be between 0 and 8" }
        return working.sliceArray(row * 9 until (row + 1) * 9) // Get 9 elements starting from row*9
    }

    /** Get a specific column (0-8) as an array of 9 integers */
    fun getCol(col: Int): Array<Int> {
        require(col in 0..8) { "Column must be between 0 and 8" }
        return Array(9) { working[it * 9 + col] } // Collect every 9th element starting from col
    }

    /** Get a specific 3x3 box (0-8) as an array of 9 integers */
    fun getBox(box: Int): Array<Int> {
        require(box in 0..8) { "Box must be between 0 and 8" }
        return Array(9) { i ->
            val row = box / 3 * 3 + i / 3 // Row within the box
            val col = box % 3 * 3 + i % 3 // Column within the box
            working[row * 9 + col] // Convert to 1D index
        }
    }

    /** Get the value at a specific cell (row 0-8, col 0-8) */
    fun getCell(row: Int, col: Int): Int {
        require(row in 0..8) { "Row must be between 0 and 8" }
        require(col in 0..8) { "Column must be between 0 and 8" }
        return working[row * 9 + col]
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
