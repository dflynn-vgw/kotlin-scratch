package org.example.sudoko

interface Solver {
    /** Solve the given puzzle, returning a solved Puzzle or null if unsolvable */
    fun solve(puzzle: Puzzle): Outcome

    /** Solver Outcome representing success with stats or failure with reason */
    sealed class Outcome {
        /** Successful solve with the solved puzzle and statistics */
        data class Success(val solved: Puzzle, val stats: Stats) : Outcome()

        /** Failed solve with a reason for failure */
        data class Failure(val reason: String) : Outcome()
    }

    /** Statistics about the solving process */
    data class Stats(
        /** Number of empty cells in the puzzle */
        val empties: Int = 0,
        /** Time taken to solve the puzzle in milliseconds */
        val startMs: Long = System.currentTimeMillis(),
        /** Duration of the solving process in milliseconds */
        val durationMs: Long = 0,
        /** Number of steps taken to solve the puzzle */
        var steps: Int = 0,
        /** Number of backtracks performed during solving */
        var backtracks: Int = 0,
    ) {
        fun final(): Stats = this.copy(durationMs = System.currentTimeMillis() - startMs)
    }
}
