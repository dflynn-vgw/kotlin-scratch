package org.example.sudoko

import org.example.sudoko.State.Companion.validateStateString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class PuzzleTests {
    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                     | STATE                                                                             | OUTCOME
            Valid (all empty / zero)     | 000000000000000000000000000000000000000000000000000000000000000000000000000000000 | Success                                                                                  | Success 
            Valid (easy puzzle)          | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | Success
            Valid (medium puzzle)        | 005300000800000020070010500400005300001070060003200080060500009004000030000009700 | Success
            Valid (hard puzzle)          | 200080300060070084030500209000105408000000000402706000301007040720040060004010003 | Success
            Invalid (row duplicate)      | 530570000600195000098000060800060003400803001700020006060000280000419005000080079 | Row 0 has duplicates!
            Invalid (column duplicate)   | 530070009600195000098000060800060003400803001700020006060000280000419005000030079 | Col 8 has duplicates!
            Invalid (box duplicate)      | 530070000300195000098000060800060003400803001700020006060000280000419005000080079 | Box 0 has duplicates!       
            Puzzle: 02, Easy (valid)     | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | Success """,
    )
    @DisplayName("Scenarios for Puzzle State Validation")
    fun scenarios_for_puzzle_state_validation(name: String, stateStr: String, outcome: String) {
        validateStateString(stateStr)
        val puzzle = Puzzle(stateStr)
        val expect: Outcome = when (outcome) {
            "Success" -> Outcome.Success
            else -> Outcome.Failure(outcome)
        }
        assertEquals(expect, puzzle.validate())
    }

    @ParameterizedTest(name = "Scenario: {index} - {0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
        """#SCENARIO                     | STATE                                                                             | SOLVED
            Not Solved (all empty)       | 000000000000000000000000000000000000000000000000000000000000000000000000000000000 | false 
            Not Solved (partially filled) | 530070000600195000098000060800060003400803001700020006060000280000419005000080079 | false
            Solved (valid)               | 534678912672195348198342567859761423426853791713924856961537284287419635345286179 | true
            Not Solved (partial,invalid) | 530570000600195000098000060800060003400803001700020006060000280000419005000080079 | false 
            Not Solved (invalid)         | 534678912672195348198342567859761423426853791713924856961537284287419635345286178 | false """,
    )
    @DisplayName("Scenarios Puzzle Solved Check")
    fun scenarios_for_puzzle_solved_check(name: String, stateStr: String, solved: String) {
        validateStateString(stateStr)
        val puzzle = Puzzle(stateStr)
        val expect = when (solved) {
            "true" -> true else -> false
        }
        assertEquals(expect, puzzle.isSolved())
    }
}
