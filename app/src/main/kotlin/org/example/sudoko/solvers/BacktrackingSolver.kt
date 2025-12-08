package org.example.sudoko.solvers

import org.example.sudoko.Cell
import org.example.sudoko.Outcome
import org.example.sudoko.Puzzle
import org.example.sudoko.Solver

/**
 * Solver implementation using the backtracking algorithm
 *
 * Algorithm Steps:
 * 1. Find an empty cell in the puzzle
 * 2. Try placing numbers 1-9 in the empty cell
 * 3. After placing a number, validate the puzzle state
 * 4. If valid, recursively attempt to solve the rest of the puzzle
 * 5. If the puzzle is solved, return the solved puzzle
 * 6. If placing a number, leads to an invalid state or unsolvable puzzle, backtrack and try the next number
 * 7. If all numbers 1-9 have been tried and none lead to a solution, return null (unsolvable)
 *  */
class BacktrackingSolver : Solver {
    override fun solve(puzzle: Puzzle): Puzzle? {
        val emptyCells = puzzle.getEmptyCells()
        var index = 0

        while (index >= 0 && index < emptyCells.size) {
            val cell = emptyCells[index]
            if (tryNumbers(puzzle, cell)) {
                index++ // move to next empty cell
            } else {
                index-- // backtrack to previous cell
            }
        }

        // If all cells are filled and valid, return the solved puzzle (otherwise null, meaning unsolvable)
        return if (index == emptyCells.size && puzzle.isSolved()) puzzle else null
    }

    /** Try placing numbers in the given cell, starting from the next possible number */
    private fun tryNumbers(puzzle: Puzzle, cell: Cell): Boolean {
        val nextValue = puzzle.getCell(cell).value + 1 // start from next number
        for (num in nextValue..9) {
            puzzle.setCell(cell.copyOf(num))
            if (puzzle.isCellValid(cell)) return true
        }

        puzzle.resetCell(cell) // reset cell on backtrack
        return false // dead end
    }
}
