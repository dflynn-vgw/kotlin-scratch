package org.example.sudoko

import kotlin.test.Test
import kotlin.test.assertEquals

class PuzzleTests {

    @Test
    fun scenario1_puzzle_empty() {
        val puzzle = Puzzle()
        val expect = """
            0 0 0 | 0 0 0 | 0 0 0
            0 0 0 | 0 0 0 | 0 0 0
            0 0 0 | 0 0 0 | 0 0 0
            - - - + - - - + - - -
            0 0 0 | 0 0 0 | 0 0 0
            0 0 0 | 0 0 0 | 0 0 0
            0 0 0 | 0 0 0 | 0 0 0
            - - - + - - - + - - -
            0 0 0 | 0 0 0 | 0 0 0
            0 0 0 | 0 0 0 | 0 0 0
            0 0 0 | 0 0 0 | 0 0 0
        """.trimIndent()
        val actual = puzzle.toString()
        assertEquals(expect, actual)
    }

    @Test
    fun scenario2_puzzle_easy() {
        val state = """
            5 3 0 | 0 7 0 | 0 0 0
            6 0 0 | 1 9 5 | 0 0 0
            0 9 8 | 0 0 0 | 0 6 0
            - - - + - - - + - - -
            8 0 0 | 0 6 0 | 0 0 3
            4 0 0 | 8 0 3 | 0 0 1
            7 0 0 | 0 2 0 | 0 0 6
            - - - + - - - + - - -
            0 6 0 | 0 0 0 | 2 8 0
            0 0 0 | 4 1 9 | 0 0 5
            0 0 0 | 0 8 0 | 0 7 9
        """.trimIndent()
        val puzzle = Puzzle(state)
        assertEquals(state, puzzle.toString())
    }

    @Test
    fun scenario3_from_simple_state() {
        val state = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        val puzzle = Puzzle(state)
        val expect = """
            5 3 0 | 0 7 0 | 0 0 0
            6 0 0 | 1 9 5 | 0 0 0
            0 9 8 | 0 0 0 | 0 6 0
            - - - + - - - + - - -
            8 0 0 | 0 6 0 | 0 0 3
            4 0 0 | 8 0 3 | 0 0 1
            7 0 0 | 0 2 0 | 0 0 6
            - - - + - - - + - - -
            0 6 0 | 0 0 0 | 2 8 0
            0 0 0 | 4 1 9 | 0 0 5
            0 0 0 | 0 8 0 | 0 7 9
        """.trimIndent()

        assertEquals(expect, puzzle.toString())
    }
}