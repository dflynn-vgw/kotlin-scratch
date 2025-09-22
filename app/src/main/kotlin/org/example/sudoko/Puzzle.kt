package org.example.sudoko

/** A 9x9 Sudoku puzzle. Numbers 1-9 only*/
class Puzzle(
    /** Initial state of the puzzle as a string of 81 characters (0 for empty cells) */
    initialState: String = State.EMPTY_STATE_STRING,
) {
    /** Internal state of the puzzle */
    private val state: State = State.fromString(initialState)

    /** Validate the current puzzle state, returning Success or Failure with a reason */
    fun validate(): Outcome {
        // Validate rows, columns, and boxes
        return checkNoDuplicates()
    }

    /** Check if the puzzle is completely solved (all cells filled and valid) */
    fun isSolved(): Boolean = if (state.working.contains(State.EMPTY)) {
        false
    } else {
        validate() is Outcome.Success
    }

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
