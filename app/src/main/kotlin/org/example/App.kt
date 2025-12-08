package org.example

import org.example.sudoko.Puzzle
import org.example.sudoko.Solver
import org.example.sudoko.State

/**
 * Sudoku Solver CLI
 *
 * Usage: ./gradlew run --args="<puzzle1> <puzzle2> ..."
 *
 * Each puzzle should be an 81-character string (0 for empty cells).
 */
object App {
    fun run(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        args.forEachIndexed { index, puzzleStr ->
            if (args.size > 1) {
                println("\n${"-".repeat(60)}")
                println("Puzzle ${index + 1} of ${args.size}")
                println("-".repeat(60))
            }
            solvePuzzle(puzzleStr)
        }
    }

    private fun solvePuzzle(puzzleStr: String) {
        try {
            // Validate the puzzle string
            State.validateStateString(puzzleStr)

            val puzzle = Puzzle(puzzleStr)

            println("\nOriginal Puzzle:")
            println(puzzle)
            println()

            // Solve the puzzle
            when (val outcome = puzzle.solve()) {
                is Solver.Outcome.Success -> {
                    println("✓ Solved!")
                    println("\nSolution:")
                    println(outcome.solved)
                    println()
                    printStats(outcome.stats)
                }
                is Solver.Outcome.Failure -> {
                    println("✗ Failed: ${outcome.reason}")
                }
            }
        } catch (e: IllegalArgumentException) {
            println("✗ Invalid puzzle: ${e.message}")
        } catch (e: Exception) {
            println("✗ Error: ${e.message}")
        }
    }

    private fun printStats(stats: Solver.Stats) {
        println("Stats:")
        println("  Empty cells: ${stats.empties}")
        println("  Steps taken: ${stats.steps}")
        println("  Backtracks:  ${stats.backtracks}")
        println("  Time:        ${stats.durationMs}ms")
    }

    private fun printUsage() {
        println(
            """
            Sudoku Solver CLI

            Usage:
              ./gradlew run --args="<puzzle1> [puzzle2] ..."

            Each puzzle must be an 81-character string with digits 0-9.
            Use 0 for empty cells.

            Example:
              ./gradlew run --args="530070000600195000098000060800060003400803001700020006060000280000419005000080079"

            Multiple puzzles:
              ./gradlew run --args="530070000... 005300000..."
            """.trimIndent(),
        )
    }
}

/** Main entry point */
fun main(args: Array<String>) {
    App.run(args)
}
