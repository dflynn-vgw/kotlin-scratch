# Sudoku Solver

A **Sudoku solver** is an algorithm or program that fills a 9x9 grid so that every row, column, and 3x3 subgrid contains
the numbers 1-9 exactly once, following the classic Sudoku rules.

This project implements a command-line Sudoku solver written in Kotlin using a backtracking algorithm.

## Quick Start

```bash
# Run with no arguments to see usage
./gradlew run

# Solve a single puzzle
./gradlew run --args="530070000600195000098000060800060003400803001700020006060000280000419005000080079"

# Solve multiple puzzles
./gradlew run --args="530070000... 005300000..."

# Run tests
./gradlew test
```

## Features

- ✅ **Backtracking algorithm** - Efficient solver using recursive backtracking
- ✅ **Multiple puzzle support** - Solve one or more puzzles in a single run
- ✅ **Statistics tracking** - View steps, backtracks, and solving time
- ✅ **Error handling** - Graceful handling of invalid or unsolvable puzzles
- ✅ **Immutable solving** - Original puzzle is never modified
- ✅ **Comprehensive tests** - Full test coverage for solver and CLI

## Usage

### Input Format

Puzzles must be provided as 81-character strings:
- Use digits `1-9` for filled cells
- Use `0` for empty cells
- Characters are read left-to-right, top-to-bottom

**Example puzzle:**
```
5 3 0 | 0 7 0 | 0 0 0
6 0 0 | 1 9 5 | 0 0 0
...
```
Becomes: `530070000600195000...`

### CLI Examples

**Show help:**
```bash
./gradlew run
```

**Solve an easy puzzle:**
```bash
./gradlew run --args="530070000600195000098000060800060003400803001700020006060000280000419005000080079"
```

**Output:**
```
Original Puzzle:
5 3 0 | 0 7 0 | 0 0 0
6 0 0 | 1 9 5 | 0 0 0
...

✓ Solved!

Solution:
5 3 4 | 6 7 8 | 9 1 2
6 7 2 | 1 9 5 | 3 4 8
...

Stats:
  Empty cells: 51
  Steps taken: 8365
  Backtracks:  4157
  Time:        29ms
```

**Solve multiple puzzles:**
```bash
./gradlew run --args="530070000... 005300000... 200080300..."
```

## Architecture

### Core Components

```
org.example.sudoko/
├── Puzzle.kt              # Main puzzle representation (data class)
├── Cell.kt                # Cell with row, col, value
├── State.kt               # Internal puzzle state management
├── Solver.kt              # Solver interface with Outcome and Stats
└── solvers/
    └── BacktrackingSolver.kt  # Backtracking implementation
```

### Key Design Decisions

**Immutability:**
- `Puzzle` is a data class with immutable properties
- `solve()` creates a copy before solving, preserving the original
- State is recreated from string representation on copy

**Outcome Pattern:**
```kotlin
sealed class Outcome {
    data class Success(val solved: Puzzle, val stats: Stats) : Outcome()
    data class Failure(val reason: String) : Outcome()
}
```

**Statistics:**
```kotlin
data class Stats(
    val empties: Int,
    val steps: Int,
    val backtracks: Int,
    val durationMs: Long
)
```

## Sudoku Solving Basics

Sudoku is a constraint satisfaction problem, where you must assign values to empty cells under strict constraints:

- Each row must have all digits from 1 to 9 once only.
- Each column must have all digits from 1 to 9 once only.
- Each 3x3 subgrid (called a box) must have all digits from 1 to 9 once only.

Most simple Sudoku solvers follow this approach:

- Scan the grid and record possible candidates for each empty cell that do not violate any constraints.
- Fill in cells where only one candidate is possible (“forced entry” or “naked singles”).
- For more difficult cases, apply logic to eliminate possible candidates from cells, sometimes using strategies such as
  scanning for pairs/triples.

## Implementation: Backtracking Algorithm

This solver uses **backtracking**, the most common algorithm for Sudoku:

### Algorithm Steps

1. **Find empty cells** - Pre-compute all empty cells at the start
2. **Try numbers 1-9** - For each empty cell, try placing valid numbers
3. **Validate placement** - Check if the number is valid in the current row, column, and 3x3 box
4. **Move forward** - If valid, move to the next empty cell
5. **Backtrack** - If no valid numbers exist, backtrack to the previous cell and try the next number
6. **Repeat** - Continue until all cells are filled or no solution exists

### Key Implementation Details

**Iterative approach with index tracking:**
```kotlin
while (index >= 0 && index < cells.size) {
    stats.steps++
    if (tryNumbers(puzzle, cells[index])) {
        index++  // move forward
    } else {
        stats.backtracks++
        index--  // backtrack
    }
}
```

**Smart number trying:**
- Starts from the current cell value + 1 (not always from 1)
- Enables proper backtracking by remembering where we left off
- Resets cell to 0 when all numbers exhausted

**Validation:**
- Checks entire row, column, and 3x3 box after each placement
- Uses Set-based duplicate detection
- Excludes empty cells (0) from validation

### Performance Characteristics

**Time Complexity:** O(9^n) where n is the number of empty cells (worst case)  
**Space Complexity:** O(n) for the empty cells array and recursion stack  
**Typical Performance:**
- Easy puzzles: ~8,000 steps, ~4,000 backtracks, <30ms
- Medium puzzles: ~15,000 steps, ~7,000 backtracks, <50ms
- Hard puzzles: ~50,000+ steps, ~25,000+ backtracks, <200ms

## Testing

The project includes comprehensive test coverage:

### Solver Tests (`SolverTests.kt`)

- **Parameterized tests** for Easy, Medium, and Hard puzzles
- **Immutability test** verifying original puzzle is not mutated
- **Stats validation** ensuring accurate tracking of steps, backtracks, and time

```bash
./gradlew test --tests "SolverTests"
```

### CLI Tests (`AppTest.kt`)

- **Usage display** when no arguments provided
- **Valid puzzle solving** with output verification
- **Multiple puzzle handling**
- **Error handling** for invalid inputs
- **Unsolvable puzzle detection**

```bash
./gradlew test --tests "AppTest"
```

### Running All Tests

```bash
./gradlew test
```

## Future Enhancements

Possible improvements beyond the current backtracking implementation:

**Optimization Techniques:**
- **Constraint propagation** - Update candidates for entire rows/columns/grids whenever a choice is made
- **Cell ordering** - Solve cells with fewest candidates first (Most Constrained Variable heuristic)
- **Naked pairs/triples** - Eliminate candidates based on pattern recognition

**Advanced Algorithms:**
- **Dancing Links (DLX)** - Knuth's Algorithm X for exact cover problems
- **SAT solvers** - Convert Sudoku to Boolean satisfiability problem
- **Genetic algorithms** - Evolutionary approach for very hard puzzles

**Features:**
- Puzzle generation with difficulty ratings
- Step-by-step solution visualization
- Hint system for interactive solving
- Support for variant Sudoku types (Killer, Samurai, etc.)

## Resources

[How To Solve Sudoku (NY Times)](https://www.nytimes.com/2023/03/02/crosswords/how-to-solve-sudoku.html)
[Sudoku Rules](https://sudoku.com/sudoku-rules/)
[Sudoku Solutions](https://www.sudoku-solutions.com)
[Sudoku Techniques](https://www.conceptispuzzles.com/index.aspx?uri=puzzle%2Fsudoku%2Ftechniques)
[The Math Behind Sudoku](https://pi.math.cornell.edu/~mec/Summer2009/Mahmood/Solve.html)
[9 Solving Sudoku With Graph Data Structures & Algorithms | Android Studio Tutorial Kotlin (YouTube)](https://www.youtube.com/watch?v=g6wLjN5VOx4)
[How to Solve Sudoku (Penny Dell Puzzles)](https://www.pennydellpuzzles.com/wp-content/uploads/2019/03/How-to-Solve-Sudoku.pdf)
[Pass Your Next Tech Interview With Valid Sudoku (YouTube)](https://www.youtube.com/watch?v=qPLYKr7HdKU)
[Sudoku Wiki](https://www.sudokuwiki.org/sudoku.htm)
[How I Finally Wrote a Sudoku Solver](https://dev.to/aspittel/how-i-finally-wrote-a-sudoku-solver-177g)
[How to Solve Sudoku Puzzles - Sudoku Beginner Tutorial #1 (YouTube)](https://www.youtube.com/watch?v=IHGNMobRnJE)
[I've Made A Sudoku Solver](https://www.reddit.com/r/Kotlin/comments/1k35r9r/ive_made_a_sudoku_solvergenerator_written_in/)
[The Basics Of Killer Sudoku](https://artisanalsudoku.substack.com/p/the-basics-of-killer-sudoku)
[14](https://www.youtube.com/watch?v=rONf7HVgMeo)
[SWE Technical Interviews, Making The Case For Leetcode](https://blog.devgenius.io/swe-technical-interviews-making-the-case-for-leetcode-f4fec488281b)