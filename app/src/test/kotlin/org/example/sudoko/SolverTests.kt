package org.example.sudoko

import org.example.sudoko.solvers.BacktrackingSolver
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolverTests {
    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | PUZZLE                                                                             | SOLVED
            Solved (Easy Puzzle)        | 530070000600195000098000060800060003400803001700020006060000280000419005000080079  | 534678912672195348198342567859761423426853791713924856961537284287419635345286179""",
    )
    fun scenarios_for_solver(name: String, puzzleStr: String, solvedStr: String) {
        State.validateStateString(puzzleStr)
        State.validateStateString(solvedStr)

        val puzzle = Puzzle(puzzleStr, BacktrackingSolver())
        val solved = puzzle.solve()
        val expect = State.fromString(solvedStr).toPrettyString()

        assertTrue(solved?.isSolved() ?: false)
        assertEquals(expect, solved.toString())
    }
}
