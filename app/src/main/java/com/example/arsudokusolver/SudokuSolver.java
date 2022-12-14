package com.example.arsudokusolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SudokuSolver {
    public static boolean solve(List<List<Integer>> grid) {
        //Check if sudoku solved is correct
        if (isSudokuValid(grid)) {
            //Sudoku is Correct.
            sudokuSolverHelper(grid);//Solving Sudoku Solver using Helper;
            return true;
        } else {
            //Sudoku is InCorrect.
            return false;
        }
    }


    private static boolean isValid(List<List<Integer>> board, int x, int y, int num) {
        for (int i = 0; i < 9; i++)
            if (board.get(i).get(y) == num)
                return false;
        for (int i = 0; i < 9; i++)
            if (board.get(x).get(i) == num)
                return false;
        x /= 3;
        y /= 3;
        for (int i = 3 * x; i < 3 * (x + 1); i++) {
            for (int j = 3 * y; j < 3 * (y + 1); j++) {
                if (board.get(i).get(j) == num)
                    return false;
            }
        }
        return true;
    }

    private static boolean sudokuSolverHelper(List<List<Integer>> board) {
        int _max = 3 * 3;

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board.get(i).get(j) == 0) {
                    for (int num = 1; num <= _max; num++) {
                        if (isValid(board, i, j, num)) {
                            board.get(i).set(j, num);
                            if (sudokuSolverHelper(board)) {
                                return true;
                            }
                            board.get(i).set(j, 0);
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private static Boolean isSudokuValid(List<List<Integer>> sudoku) {
        int _max = 9;
        //Checking rows for invalid input
        for (int i = 0; i < 9; i++) {
            HashMap<Integer, Integer> map = new HashMap<>();
            for (int j = 0; j < 9; j++) {
                int key = sudoku.get(i).get(j);
                Integer count = map.get(key);
                if (count == null) {
                    map.put(key, 1);
                } else {
                    map.put(key, count + 1);
                }
            }
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                if (entry.getKey() != 0 && (entry.getKey() > _max || entry.getValue() > 1)) {
                    return false;
                }
            }
        }
        //Checking columns for invalid input
        for (int j = 0; j < 9; j++) {
            HashMap<Integer, Integer> map = new HashMap<>();
            for (int i = 0; i < 9; i++) {
                int key = sudoku.get(i).get(j);
                Integer count = map.get(key);
                if (count == null) {
                    map.put(key, 1);
                } else {
                    map.put(key, count + 1);
                }
            }
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                if (entry.getKey() != 0 && (entry.getKey() > _max || entry.getValue() > 1)) {
                    return false;
                }
            }
        }
        //Checking boxes for invalid input
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                HashMap<Integer, Integer> map = new HashMap<>();
                for (int i = 3 * x; i < 3 * (x + 1); i++) {
                    for (int j = 3 * y; j < 3 * (y + 1); j++) {
                        int key = sudoku.get(i).get(j);
                        Integer count = map.get(key);
                        if (count == null) {
                            map.put(key, 1);
                        } else {
                            map.put(key, count + 1);
                        }
                    }
                }
                for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                    if (entry.getKey() != 0 && (entry.getKey() > _max || entry.getValue() > 1)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}