package org.example.sudoko

/** A 9x9 Sudoku puzzle. Numbers 1-9 only*/
class Puzzle(
    /** Initial state of the puzzle as a string of 81 characters (0 for empty cells) */
    state: String = """
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
){
    private val boxes: Array<Array<Box>> = Array(3) { Array(3) { Box() } }

    init {
        val digits = state.filter { it.isDigit() }.map { it.toString().toInt() }
        require(digits.size == 81) { "State must contain exactly 81 digits (0-9)" }
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                setCell(row, col, digits[row * 9 + col])
            }
        }
    }

    fun getRow(row: Int): List<Int> {
        val result = mutableListOf<Int>()
        val boxRow = row / 3
        val cellRow = row % 3
        for (boxCol in 0 until 3) {
            for (cellCol in 0 until 3) {
                result.add(boxes[boxRow][boxCol].cells[cellRow][cellCol])
            }
        }
        return result
    }

    fun getColumn(col: Int): List<Int> {
        val result = mutableListOf<Int>()
        val boxCol = col / 3
        val cellCol = col % 3
        for (boxRow in 0 until 3) {
            for (cellRow in 0 until 3) {
                result.add(boxes[boxRow][boxCol].cells[cellRow][cellCol])
            }
        }
        return result
    }

    fun getBox(boxRow: Int, boxCol: Int): Box {
        return boxes[boxRow][boxCol]
    }

    fun setCell(row: Int, col: Int, value: Int) {
        val boxRow = row / 3
        val boxCol = col / 3
        val cellRow = row % 3
        val cellCol = col % 3
        boxes[boxRow][boxCol].cells[cellRow][cellCol] = value
    }

    fun getCell(row: Int, col: Int): Int {
        val boxRow = row / 3
        val boxCol = col / 3
        val cellRow = row % 3
        val cellCol = col % 3
        return boxes[boxRow][boxCol].cells[cellRow][cellCol]
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in 0 until 9) {
            if (i % 3 == 0 && i != 0) {
                sb.append("- - - + - - - + - - -\n")
            }
            for (j in 0 until 9) {
                if (j % 3 == 0 && j != 0) {
                    sb.append("| ")
                }
                sb.append(getCell(i, j)).append(" ")
            }
            /* Trim trailing space */
            sb.setLength(sb.length - 1)
            sb.append("\n")
        }
        /* Trim trailing newline */
        sb.setLength(sb.length - 1)
        return sb.toString()
    }
}