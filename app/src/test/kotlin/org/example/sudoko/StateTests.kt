package org.example.sudoko

import org.example.sudoko.State.Companion.validateStateString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class StateTests {

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
    @DisplayName("Scenarios for Puzzle Initialisation and Pretty Print")
    fun scenarios_for_puzzle_initialisation_and_pretty_print(name: String, stateStr: String?) {
        validateStateString(stateStr ?: State.EMPTY_STATE_STRING)
        val state = if (stateStr.isNullOrBlank()) State() else State.fromString(stateStr)
        val expect = prettyState(stateStr ?: State.EMPTY_STATE_STRING)
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
        validateStateString(stateStr ?: State.EMPTY_STATE_STRING)
        validateSelectorAndExpectation(row, expect)
        val state = if (stateStr.isNullOrBlank()) State() else State.fromString(stateStr)
        val actual = state.getRow(row).joinToString("")
        assertEquals(expect, actual)
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | STATE                                                                             | COL | EXPECT
            Puzzle: 01, Empty (col 0)   |                                                                                   | 0   | 000000000
            Puzzle: 02, Easy (col 0)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 560847000
            Puzzle: 02, Easy (col 4)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 4   | 790602018
            Puzzle: 03, Medium (col 1)  | 005300000800000020070010500400005300010070006003200080060500009004000030000009700 | 1   | 007010600
            Puzzle: 04, Hard (col 6)    | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 6   | 302400000
            Puzzle: 04, Hard (col 8)    | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 8   | 049800003""",
    )
    @DisplayName("Scenarios for Column Selection")
    fun scenarios_for_col_selection(name: String, stateStr: String?, col: Int, expect: String) {
        validateStateString(stateStr ?: State.EMPTY_STATE_STRING)
        validateSelectorAndExpectation(col, expect)
        val state = if (stateStr.isNullOrBlank()) State() else State.fromString(stateStr)
        val actual = state.getCol(col).joinToString("")
        assertEquals(expect, actual)
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | STATE                                                                             | BOX | EXPECT
            Puzzle: 01, Empty (box 0)   |                                                                                   | 0   | 000000000
            Puzzle: 02, Easy (box 0)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 530600098
            Puzzle: 02, Easy (box 4)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 4   | 060803020
            Puzzle: 03, Medium (box 1)  | 005300000800000020070010500400005300010070006003200080060500009004000030000009700 | 1   | 300000010
            Puzzle: 04, Hard (box 6)    | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 6   | 301720004
            Puzzle: 04, Hard (box 8)    | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 8   | 040060003""",
    )
    @DisplayName("Scenarios for Box Selection")
    fun scenarios_for_box_selection(name: String, stateStr: String?, box: Int, expect: String) {
        validateStateString(stateStr ?: State.EMPTY_STATE_STRING)
        validateSelectorAndExpectation(box, expect)
        val state = if (stateStr.isNullOrBlank()) State() else State.fromString(stateStr)
        val actual = state.getBox(box).joinToString("")
        assertEquals(expect, actual)
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                     | STATE                                                                             | ROW | COL | EXPECT
            Puzzle: 01, Empty (cell 0,0) |                                                                                   | 0   | 0   | 0
            Puzzle: 02, Easy (cell 0,0)  | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 0   | 5
            Puzzle: 02, Easy (cell 4,4)  | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 4   | 4   | 0
            Puzzle: 03, Medium (cell 1,1)| 005300000800000020070010500400005300010070006003200080060500009004000030000009700 | 1   | 1   | 0
            Puzzle: 04, Hard (cell 6,3)  | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 6   | 3   | 0
            Puzzle: 04, Hard (cell 8,8)  | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | 8   | 8   | 3""",
    )
    @DisplayName("Scenarios for Cell Selection")
    fun scenarios_for_cell_selection(name: String, stateStr: String?, row: Int, col: Int, expect: Int) {
        validateStateString(stateStr ?: State.EMPTY_STATE_STRING)
        require(row in 0..8) { "Row must be between 0 and 8" }
        require(col in 0..8) { "Column must be between 0 and 8" }
        require(expect in 0..9) { "Expect must be between 0 and 9" }
        val state = if (stateStr.isNullOrBlank()) State() else State.fromString(stateStr)
        val actual = state.getCell(row, col)
        assertEquals(expect, actual)
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | STATE                                                                                 | MESSAGE
            Invalid (too short)         | 53007000060019500009800006080006000340080300170002000606000028000041900500008007      | State must contain exactly 81 digits (0-9)
            Invalid (too long)          | 5300700006001950000980000608000600034008030017000200060600002800004190050000800799    | State must contain exactly 81 digits (0-9)
            Invalid (non-digit)         | 53007000060019500009800006080006000340080300170002000606000A280000419005000080079     | State must contain exactly 81 digits (0-9)
            Invalid (non-digit)         | 53007000060019500009800006080006000340080300170002000606000-280000419005000080079     | State must contain exactly 81 digits (0-9)""",
    )
    @DisplayName("Scenarios for Invalid State String")
    fun scenarios_for_invalid_state_string(name: String, stateStr: String, message: String) {
        val exception = assertThrows<IllegalArgumentException>(message) { State.fromString(stateStr) }
        assertEquals(message, exception.message)
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                    | STATE                                                                             | ROW | COL | VALUE | MESSAGE
            Invalid (row out of range)  | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 9   | 0   | 5     | Row must be between 0 and 8
            Invalid (row out of range)  | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | -1  | 0   | 5     | Row must be between 0 and 8
            Invalid (col out of range)  | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 9   | 5     | Column must be between 0 and 8
            Invalid (col out of range)  | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | -1  | 5     | Column must be between 0 and 8
            Invalid (value too low)     | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 0   | -1    | Value must be between 0 and 9 (0 for empty)
            Invalid (value too high)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 0   | 10    | Value must be between 0 and 9 (0 for empty)
            Invalid (change initial)    | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 0   | 1     | Cannot change initial puzzle values
            Valid (change empty cell)   | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | 0   | 2   | 4     | Success""",
    )
    @DisplayName("Scenarios for Setting Cell Values")
    fun scenarios_for_setting_cell_values(name: String, stateStr: String, row: Int, col: Int, value: Int, message: String) {
        val state = State.fromString(stateStr)
        if (message == "Success") {
            state.setCell(row, col, value)
            assertEquals(value, state.getCell(row, col))
            return
        }
        val exception = assertThrows<IllegalArgumentException>(message) { state.setCell(row, col, value) }
        assertEquals(message, exception.message)
    }

    @Test
    @DisplayName("All Rows Combined Equals State String")
    fun all_rows_combined_equals_state_string() {
        val stateStr = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        val state = State.fromString(stateStr)
        val rowStrings = (0..8).map { state.getRow(it).joinToString("") }
        val combined = rowStrings.joinToString("")

        assertEquals(stateStr, combined)
    }

    @Test
    @DisplayName("All Columns Combined Equals State String")
    fun all_columns_combined_equals_state_string() {
        val stateStr = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        val state = State.fromString(stateStr)
        val colStrings = (0..8).map { state.getCol(it).joinToString("") }
        val combined = (0..8).flatMap { colStrings.map { col -> col[it] } }.joinToString("")
        assertEquals(stateStr, combined)
    }

    @Test
    @DisplayName("All Boxes Combined Equals State String")
    fun all_boxes_combined_equals_state_string() {
        val stateStr = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        val state = State.fromString(stateStr)
        val boxStrings = (0..8).map { state.getBox(it).joinToString("") }

        // Reconstruct rows from boxes
        val rows = (0..8).map { row ->
            val boxRow = row / 3
            val withinBoxRow = row % 3
            (0..2).joinToString("") { boxCol ->
                boxStrings[boxRow * 3 + boxCol].substring(
                    withinBoxRow * 3,
                    withinBoxRow * 3 + 3,
                )
            }
        }

        val combined = rows.joinToString("")
        assertEquals(stateStr, combined)
    }

    @Test
    @DisplayName("All Cells Combined Equals State String")
    fun all_cells_combined_equals_state_string() {
        val stateStr = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        val state = State.fromString(stateStr)
        val combined = (0..8).flatMap { row -> (0..8).map { col -> state.getCell(row, col) } }.joinToString("")
        assertEquals(stateStr, combined)
    }

    private companion object {

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
