package org.example.sudoko

interface Solver {
    /** Solve the given puzzle, returning a solved Puzzle or null if unsolvable */
    fun solve(puzzle: Puzzle): Puzzle?
}
