package org.example.sudoko

import org.example.sudoko.solvers.BacktrackingSolver

/** A 9x9 Sudoku puzzle. Numbers 1-9 only*/
class Puzzle(
    /** Initial state of the puzzle as a string of 81 characters (0 for empty cells) */
    initialState: String = State.EMPTY_STATE_STRING,
    /** Solver to use for solving the puzzle (default: BacktrackingSolver) */
    private val solver: Solver = BacktrackingSolver(),
) {
    /** Internal state of the puzzle */
    private val state: State = State.fromString(initialState)

    /** Validate the current puzzle state, returning Success or Failure with a reason */
    fun validate(): Outcome = checkNoDuplicates()

    /** Check if the current puzzle state is valid (no duplicates) */
    fun isValid(): Boolean = validate() is Outcome.Success

    /** Attempt to solve the puzzle using the provided solver */
    fun solve(): Puzzle? = solver.solve(this)

    /** Check if the puzzle is completely solved (all cells filled and valid) */
    fun isSolved() = !state.hasEmptyCells() && validate() is Outcome.Success

    /** Get a list of all empty cells in the puzzle */
    fun getEmptyCells(): Array<Cell> {
        val emptyCells = ArrayList<Cell>()

        for (row in 0 until 9) {
            for (col in 0 until 9) {
                val cell = state.getCell(row, col)
                if (cell.isEmpty()) {
                    emptyCells.add(Cell(row, col))
                }
            }
        }

        return emptyCells.toTypedArray()
    }

    /** Get the value at a specific cell (row 0-8, col 0-8) */
    fun getCell(cell: Cell): Cell = state.getCell(cell)

    /** Set the value at a specific cell (row 0-8, col 0-8) */
    fun setCell(cell: Cell) = state.setCell(cell)

    /** Check that no rows, columns, or boxes contain duplicates */
    private fun checkNoDuplicates(): Outcome {
        for (i in 0 until 9) {
            if (!isValidGroup(state.getRow(i))) return Outcome.Failure("Row $i has duplicates!")
            if (!isValidGroup(state.getCol(i))) return Outcome.Failure("Col $i has duplicates!")
            if (!isValidGroup(state.getBox(i))) return Outcome.Failure("Box $i has duplicates!")
        }
        return Outcome.Success
    }

    /** Check if a group (row, column, or box) contains no duplicates excluding EMPTY cells */
    private fun isValidGroup(group: Array<Int>): Boolean {
        val nums = group.filter { it != State.EMPTY }
        return nums.size == nums.toSet().size
    }

    /** Pretty print the puzzle state as a multi-line formatted string */
    override fun toString() = state.toPrettyString()
}
