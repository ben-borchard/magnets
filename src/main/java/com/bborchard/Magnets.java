package com.bborchard;

import static com.bborchard.Magnets.Charge.NEG;
import static com.bborchard.Magnets.Charge.NONE;
import static com.bborchard.Magnets.Charge.POS;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.stream.Stream;

public class Magnets {

  private static boolean debug = false;

  private int totalRows;
  private int totalCols;
  private int totalBlocks;

  private Puzzle puzzle;

  public Magnets(char[][] grid, int[][] requirements) {

    puzzle = new Puzzle(requirements);

    // flags to help with proper magnet creation
    boolean skipNext = false;
    boolean[] skipArray = new boolean[grid.length];

    int i = 0;
    for (char[] row : grid) {
      int j = 0;

      for (char c : row) {
        if (!skipNext && !skipArray[j]) {

          Block b = puzzle.rows[i].blocks[j];
          Block o;
          b.row = puzzle.rows[i];
          b.col = puzzle.cols[j];
          if (c == '#') {
            addAdjacents(b, i, j, false, false);
            o = puzzle.rows[i].blocks[j + 1];
            o.row = puzzle.rows[i];
            o.col = puzzle.cols[j + 1];
            addAdjacents(o, i, j + 1, true, false);
            skipNext = true;
            skipArray[j] = false;
          } else {
            addAdjacents(b, i, j, false, true);
            o = puzzle.rows[i + 1].blocks[j];
            o.row = puzzle.rows[i + 1];
            o.col = puzzle.cols[j];
            addAdjacents(o, i + 1, j, true, true);
            skipArray[j] = true;
            skipNext = false;
          }
          b.other = o;
          o.other = b;
        } else {
          skipArray[j] = false;
          skipNext = false;
        }
        j++;
      }
      i++;
    }
  }

  private void addAdjacents(Block block, int row, int col, boolean other, boolean vertical) {
    List<Block> adjacents = new ArrayList<>();
    if (row != 0 && (!other || !vertical)) {
      adjacents.add(puzzle.rows[row - 1].blocks[col]);
    }
    if (col != 0 && (!other || vertical)) {
      adjacents.add(puzzle.rows[row].blocks[col - 1]);
    }
    if (row != puzzle.rows.length - 1 && (other || !vertical)) {
      adjacents.add(puzzle.rows[row + 1].blocks[col]);
    }
    if (col != puzzle.rows.length - 1 && (other || vertical)) {
      adjacents.add(puzzle.rows[row].blocks[col + 1]);
    }
    block.adjacents = adjacents.toArray(new Block[adjacents.size()]);
  }

  public void solve() {

    Stack<Guess> guessStack = new Stack<>();
    Charge currentCharge = POS;
    boolean solved;
    int currentRowIndex = 0;
    int currentColIndex = 0;
    boolean fillingRows = true;
    while (true) {
      Section currentSection =
          fillingRows ? puzzle.rows[currentRowIndex] : puzzle.cols[currentColIndex];
      debug(
          "Working on %s %s with charge %s; %s left to place",
          fillingRows ? "row" : "col",
          fillingRows ? currentRowIndex : currentColIndex,
          currentCharge,
          currentSection.blocksLeft(currentCharge));
      while (currentSection.blocksLeft(currentCharge) > 0
          && (fillingRows ? currentColIndex : currentRowIndex) != currentSection.blocks.length) {
        Block currBlock = currentSection.blocks[fillingRows ? currentColIndex : currentRowIndex];
        if (currBlock.trySet(currentCharge)) {
          debug(
              "Successfully added block at %s, %s with charge %s",
              currentColIndex, currentRowIndex, currentCharge);
          guessStack.push(new Guess(currBlock, currentRowIndex, currentColIndex, fillingRows));
        }
        if (fillingRows) {
          currentColIndex++;
        } else {
          currentRowIndex++;
        }
      }
      if (currentSection.blocksLeft(currentCharge) <= 0) {
        debug(
            "%s %s is satisfied with charge %s",
            fillingRows ? "Row" : "Col",
            fillingRows ? currentRowIndex : currentColIndex,
            currentCharge);
        // row was satisfied - continue
        if (fillingRows) {
          currentColIndex = 0;
        } else {
          currentRowIndex = 0;
        }
        if (currentCharge == POS) {
          currentCharge = NEG;
        } else {
          currentCharge = POS;
          if (fillingRows) {
            currentRowIndex++;
          } else {
            currentColIndex++;
          }
          if ((fillingRows ? currentRowIndex : currentColIndex) == puzzle.rows.length) {
            if (fillingRows) {
              fillingRows = false;
              currentRowIndex = 0;
              currentColIndex = 0;
              currentCharge = POS;
            } else {
              // fully solved
              solved = true;
              break;
            }
          }
        }
      } else {
        do {
          // didn't work :(
          if (guessStack.isEmpty()) {
            System.err.println("unsolvable");
            System.exit(1);
          }
          Guess lastGuess = guessStack.pop();
          debug(
              "%s %s failed with charge %s",
              fillingRows ? "Row" : "Col",
              fillingRows ? currentRowIndex : currentColIndex,
              currentCharge);
          currentCharge = lastGuess.block.charge;
          lastGuess.block.fullUnset();
          fillingRows = lastGuess.fillingRows;
          currentRowIndex = fillingRows ? lastGuess.rowIndex : lastGuess.rowIndex + 1;
          currentColIndex = fillingRows ? lastGuess.colIndex + 1 : lastGuess.colIndex;
        } while ((fillingRows ? currentColIndex : currentRowIndex) == currentSection.blocks.length);
      }
    }

    if (solved) {
      System.out.println("solved:");
      System.out.println(puzzle);
    } else {
      System.out.println("unsolvable :(");
    }
  }

  protected class Guess {
    public Block block;
    public int colIndex;
    public int rowIndex;
    public boolean fillingRows;

    public Guess(Block block, int rowIndex, int colIndex, boolean fillingRows) {
      this.block = block;
      this.rowIndex = rowIndex;
      this.colIndex = colIndex;
      this.fillingRows = fillingRows;
    }
  }

  protected class Block {
    public Block[] adjacents;
    public Block other;
    public Charge charge;
    public Section row;
    public Section col;

    public Block() {
      charge = NONE;
    }

    /** returns whether it's possible to put this charge here */
    public boolean possible(Charge charge) {
      boolean possible = true;

      // is it empty
      if (this.charge != NONE) {
        return false;
      }

      // check adjacency
      for (Block block : adjacents) {
        if (block.charge == charge) {
          return false;
        }
      }

      // check section requirements
      if (row.blocksLeft(charge) == 0) {
        return false;
      }
      if (col.blocksLeft(charge) == 0) {
        return false;
      }
      return true;
    }

    public void set(Charge charge) {
      this.charge = charge;
      row.placeBlock(charge);
      col.placeBlock(charge);
    }

    public boolean trySet(Charge charge) {
      Charge oppositeCharge = Charge.opposite(charge);
      if (possible(charge) && other.possible(oppositeCharge)) {
        set(charge);
        other.set(oppositeCharge);
        return true;
      }
      return false;
    }

    public void fullUnset() {
      unset();
      other.unset();
    }

    public void unset() {
      row.removeBlock(charge);
      col.removeBlock(charge);
      this.charge = NONE;
    }

    @Override
    public String toString() {
      return charge.toString();
    }
  }

  protected class Section {
    public Block[] blocks;
    public int posNum;
    public int negNum;

    public Section(int size, int posNum, int negNum) {
      this.posNum = posNum;
      this.negNum = negNum;
      blocks = new Block[size];
    }

    public void placeBlock(Charge charge) {
      if (charge == POS) {
        posNum--;
      } else if (charge == NEG) {
        negNum--;
      }
    }

    public void removeBlock(Charge charge) {
      if (charge == POS) {
        posNum++;
      } else if (charge == NEG) {
        negNum++;
      }
    }

    public int blocksLeft(Charge charge) {
      if (charge == POS) {
        return posNum;
      } else if (charge == NEG) {
        return negNum;
      }
      return Integer.MAX_VALUE;
    }

    @Override
    public String toString() {
      return Arrays.asList(blocks).toString();
    }
  }

  protected class Puzzle {
    public Section[] rows;
    public Section[] cols;

    public Puzzle(int[][] requirements) {

      int puzzleSize = requirements[0].length;

      rows = new Section[puzzleSize];
      cols = new Section[puzzleSize];

      for (int i = 0; i < puzzleSize; i++) {
        rows[i] = new Section(puzzleSize, requirements[0][i], requirements[1][i]);
        cols[i] = new Section(puzzleSize, requirements[2][i], requirements[3][i]);
      }

      // instantiate the the blocks
      for (int i = 0; i < puzzleSize; i++) {
        for (int j = 0; j < puzzleSize; j++) {
          Block b = new Block();
          rows[i].blocks[j] = b;
          cols[j].blocks[i] = b;
        }
      }
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Section s : rows) {
        sb.append(s.toString());
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  protected enum Charge {
    POS("+"),
    NEG("-"),
    NONE(" ");

    private String printVal;

    public static Charge opposite(Charge charge) {
      if (charge == POS) {
        return NEG;
      } else if (charge == NEG) {
        return POS;
      }
      throw new RuntimeException("NONE has no opposite");
    }

    Charge(String printVal) {
      this.printVal = printVal;
    }

    @Override
    public String toString() {
      return printVal;
    }
  }

  public static void debug(String message, Object... params) {
    if (debug) {
      System.out.println(String.format(message, params));
    }
  }

  public static void main(String[] args) throws Exception {
    debug = true;

    if (args.length != 1) {
      System.err.println("usage: Magnets <puzzle_file>");
      System.exit(1);
    }
    Iterator<String> lines = Files.readAllLines(Paths.get(args[0])).iterator();

    List<char[]> grid = new ArrayList<>();
    for (String line = lines.next();
        line != null && !"".equals(line) && lines.hasNext();
        line = lines.next()) {
      grid.add(line.toCharArray());
    }

    List<int[]> requirements = new ArrayList<>();
    for (String line = lines.next();
        line != null && !"".equals(line) && lines.hasNext();
        line = lines.next()) {
      requirements.add(
          Stream.of(line.split(",")).map(s -> s.trim()).mapToInt(Integer::parseInt).toArray());
    }

    // read all left over lines
    while (lines.hasNext()) {
      lines.next();
    }

    Magnets m = new Magnets(grid.toArray(new char[0][0]), requirements.toArray(new int[0][0]));
    m.solve();
  }
}
