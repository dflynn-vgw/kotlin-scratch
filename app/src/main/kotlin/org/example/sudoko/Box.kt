package org.example.sudoko

/** A 3x3 sub-grid (box) in a Sudoku puzzle. Numbers 1-9 only*/
class Box(val cells: Array<Array<Int>> = Array(3) { Array(3) { 0 } })
