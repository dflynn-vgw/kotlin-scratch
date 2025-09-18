package org.example.sudoko

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class StateTests {

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                | STATE  
            Puzzle: 01, Empty (default) | 
            Puzzle: 02, Easy            | 530070000600195000098000060800060003400803001700020006060000280000419005000080079
            Puzzle: 03, Medium          | 005300000800000020070010500400005300010070006003200080060500009004000030000009700
            Puzzle: 04, Hard            | 200080300060070084030500209000105408000000000402706000301007040720040060004010003""",
    )
    @DisplayName("Scenarios for Puzzle Initialisation and Pretty Print")
    fun scenarios_for_puzzle_initialisation_and_pretty_print(name: String, stateStr: String?) {
        validateStateString(stateStr ?: EMPTY_STATE)
        val state = if (stateStr.isNullOrBlank()) State() else State.fromString(stateStr)
        val expect = prettyState(stateStr ?: EMPTY_STATE)
        val actual = state.toPrettyString()
        assertEquals(expect, actual)
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | STATE                                                                             | ROW | EXPECT
            Puzzle: 01, Empty (row 0)   |                                                                                   | 0   | 000000000
            Puzzle: 02, Easy (row 0)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 530070000
            Puzzle: 02, Easy (row 4)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 4   | 400803001
            Puzzle: 03, Medium (row 1)  | 005300000800000020070010500400005300010070006003200080060500009004000030000009700 | 1   | 800000020
            Puzzle: 04, Hard (row 6)    | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 6   | 301007040
            Puzzle: 04, Hard (row 8)    | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 8   | 004010003""",
    )
    @DisplayName("Scenarios for Row Selection")
    fun scenarios_for_row_selection(name: String, stateStr: String?, row: Int, expect: String) {
        validateStateString(stateStr ?: EMPTY_STATE)
        validateSelectorAndExpectation(row, expect)
        val state = if (stateStr.isNullOrBlank()) State() else State.fromString(stateStr)
        val actual = state.getRow(row).joinToString("")
        assertEquals(expect, actual)
    }

    private companion object {
        val EMPTY_STATE = "0".repeat(81)

        fun validateStateString(state: String) {
            require(state.length == 81) { "State must be exactly 81 characters" }
            require(state.all { it.isDigit() }) { "State must contain only digits" }
        }

        fun validateSelectorAndExpectation(selector: Int, expect: String) {
            require(selector in 0..8) { "Selector must be between 0 and 8" }
            require(expect.length == 9) { "Expect must be exactly 9 characters" }
            require(expect.all { it.isDigit() }) { "Expect must contain only digits" }
        }

        /** Pretty print a state string into a multi-line formatted string */
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
