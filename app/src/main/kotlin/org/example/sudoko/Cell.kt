package org.example.sudoko

/** A Cell represents a position in a Sudoku grid, identified by its row and column indices, with an optional value. */
data class Cell(val row: Int, val col: Int, val value: Int = 0) {
    init {
        require(row in 0..8) { "Row must be between 0 and 8" }
        require(col in 0..8) { "Column must be between 0 and 8" }
        require(value in 0..9) { "Value must be between 0 and 9 (0 for empty)" }
    }

    /** Returns the index of the 3x3 box (0-8) that this cell belongs to in the Sudoku grid. */
    val box: Int
        get() {
            val boxRow = row / 3
            val boxCol = col / 3
            return boxRow * 3 + boxCol
        }

    /** Checks if the cell is empty (value is 0). */
    fun isEmpty(): Boolean = value == State.EMPTY

    /** Creates a copy of this Cell with a new value. */
    fun copyOf(newValue: Int): Cell = Cell(row, col, newValue)
}
