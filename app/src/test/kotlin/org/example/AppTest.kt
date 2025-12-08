package org.example

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Tests for Sudoku Solver CLI */
class AppTest {
    private val originalOut = System.out
    private val outputStream = ByteArrayOutputStream()

    @BeforeEach
    fun setUp() {
        System.setOut(PrintStream(outputStream))
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
    }

    @Test
    fun `app shows usage when no arguments provided`() {
        App.run(emptyArray())

        val output = outputStream.toString()
        assertContains(output, "Sudoku Solver CLI")
        assertContains(output, "Usage:")
        assertContains(output, "--args=")
    }

    @Test
    fun `app solves a valid puzzle`() {
        App.run(arrayOf("530070000600195000098000060800060003400803001700020006060000280000419005000080079"))

        val output = outputStream.toString()
        assertContains(output, "Original Puzzle:")
        assertContains(output, "✓ Solved!")
        assertContains(output, "Solution:")
        assertContains(output, "Stats:")
        assertContains(output, "Empty cells:")
        assertContains(output, "Steps taken:")
        assertContains(output, "Backtracks:")
        assertContains(output, "Time:")
    }

    @Test
    fun `app handles multiple puzzles`() {
        App.run(
            arrayOf(
                "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
                "005300000800000020070010500400005300010070006003200080060500009004000030000009700",
            ),
        )

        val output = outputStream.toString()
        assertContains(output, "Puzzle 1 of 2")
        assertContains(output, "Puzzle 2 of 2")
        // Both should solve successfully
        assertEquals(output.split("✓ Solved!").size, 3) // split creates 3 parts for 2 occurrences
    }

    @Test
    fun `app handles invalid puzzle string gracefully`() {
        assertDoesNotThrow { App.run(arrayOf("123")) } // Too short

        val output = outputStream.toString()
        assertContains(output, "✗ Invalid puzzle:")
        assertContains(output, "State must be exactly 81 characters")
    }

    @Test
    fun `app handles puzzle with invalid characters gracefully`() {
        assertDoesNotThrow { App.run(arrayOf("x".repeat(81))) } // Invalid characters
        assertContains(outputStream.toString(), "✗ Invalid puzzle:")
    }

    @Test
    fun `app handles unsolvable puzzle gracefully`() {
        // Puzzle with duplicate 5s in first row (unsolvable)
        assertDoesNotThrow {
            App.run(arrayOf("555555555000000000000000000000000000000000000000000000000000000000000000000000000"))
        }

        assertContains(outputStream.toString(), "✗ Failed:")
    }
}
