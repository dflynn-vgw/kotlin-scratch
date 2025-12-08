# Sudoku Solver

A **Sudoku solver** is an algorithm or program that fills a 9x9 grid so that every row, column, and 3x3 subgrid contains
the numbers 1-9 exactly once, following the classic Sudoku rules.

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

## Popular Algorithm: Backtracking

The most common algorithm for a simple Sudoku solver is **backtracking**:

- Find an empty cell.
- Try placing numbers 1–9 one by one, checking if the placement is valid with respect to rows, columns, and sub-grids.
- If a number fits, recur for the next empty cell.
- If none fit, backtrack and change previous choices.
- Continue until the puzzle is solved or no solution remains.

## Advanced Concepts

If you wish to go beyond a simple brute-force approach, consider:

- Constraint propagation: Update candidates for entire rows/columns/grids whenever a choice is made.
- Deduction strategies: Identify unique patterns (e.g., naked pairs, hidden singles) to reduce possibilities without
  guessing.
- For very tough puzzles, SAT solvers, dancing links, or graph data structures may be used, but are more advanced.

A basic Sudoku solver in Kotlin typically employs recursive backtracking and validity checks for placement in each cell.
This is an excellent coding interview exercise for demonstrating algorithms, recursion, and clean code design.[14][10]

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