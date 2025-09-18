package org.example.sudoko

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class PuzzleTests {

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | STATE  
            Puzzle: 01, Empty (default) | 
            Puzzle: 02, Easy            | 530070000600195000098000060800060003400803001700020006060000280000419005000080079
            Puzzle: 03, Medium          | 005300000800000020070010500400005300010070006003200080060500009004000030000009700
            Puzzle: 04, Hard            | 200080300060070084030500209000105408000000000402706000301007040720040060004010003""",
    )
    @DisplayName("Scenarios for Puzzle toString")
    fun scenarios(name: String, state: String?) {
        val puzzle = if (state.isNullOrBlank()) Puzzle() else Puzzle(state)
        val expect = prettyState(state ?: EMPTY_STATE)
        val actual = puzzle.toString()
        assertEquals(expect, actual)
    }

    private companion object {
        val EMPTY_STATE = "0".repeat(81)

        /**
         * Pretty print a state string into a multi-line formatted string
         * with rows, columns and boxes separated.
         *
         * Example:
         *
         * Input: 530070000600195000098000060800060003400803001700020006060000280000419005000080079
         * Output:
         * 5 3 0 | 0 7 0 | 0 0 0
         * 6 0 0 | 1 9 5 | 0 0 0
         * 0 9 8 | 0 0 0 | 0 6 0
         * - - - + - - - + - - -
         * 8 0 0 | 0 6 0 | 0 0 3
         * 4 0 0 | 8 0 3 | 0 0 1
         * 7 0 0 | 0 2 0 | 0 0 6
         * - - - + - - - + - - -
         * 0 6 0 | 0 0 0 | 2 8 0
         * 0 0 0 | 4 1 9 | 0 0 5
         * 0 0 0 | 0 8 0 | 0 7 9
         */
        fun prettyState(state: String): String {
            val sb = StringBuilder()
            for (i in state.indices) {
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

                sb.append(state[i])
            }
            return sb.toString()
        }
    }
}
