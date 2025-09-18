package org.example.sudoko

/** A 9x9 Sudoku puzzle. Numbers 1-9 only*/
class Puzzle(
    /** Initial state of the puzzle as a string of 81 characters (0 for empty cells) */
    initialState: String = State.EMPTY_STATE_STRING,
) {
    /** Internal state of the puzzle */
    private val state: State = State.fromString(initialState)

    /** Pretty print the puzzle state as a multi-line formatted string */
    override fun toString() = state.toPrettyString()
}
