package Automata;

import Main.EvalComputations.Token.ArithmeticOperator;
import Main.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class BonacciNumberSystemTest {
  private static final String TETRA = "msd_tetranacci";
  private static final String PENTA = "msd_pentanacci";

  @BeforeEach
  void setUp() {
    Session.setPathsAndNamesIntegrationTests();
    Session.cleanPathsAndNamesIntegrationTest();
  }

  @Test
  void testTetranacciRepresentations() {
    NumberSystem ns = new NumberSystem(TETRA);

    Assertions.assertTrue(ns.isMsd());
    Assertions.assertTrue(ns.useAllRepresentations());
    Assertions.assertEquals(List.of(0, 1), ns.getAlphabet());

    Automaton reps = ns.getAllRepresentations();

    // Tetranacci representations may contain at most three consecutive 1s.
    Assertions.assertTrue(accepts(reps, "111"));
    Assertions.assertTrue(accepts(reps, "1110111"));
    Assertions.assertFalse(accepts(reps, "1111"));
  }

  @Test
  void testPentanacciRepresentations() {
    NumberSystem ns = new NumberSystem(PENTA);

    Assertions.assertTrue(ns.isMsd());
    Assertions.assertTrue(ns.useAllRepresentations());
    Assertions.assertEquals(List.of(0, 1), ns.getAlphabet());

    Automaton reps = ns.getAllRepresentations();

    // Pentanacci representations may contain at most four consecutive 1s.
    Assertions.assertTrue(accepts(reps, "1111"));
    Assertions.assertTrue(accepts(reps, "111101111"));
    Assertions.assertFalse(accepts(reps, "11111"));
  }

  @Test
  void testTetranacciAddition() {
    NumberSystem ns = new NumberSystem(TETRA);
    Automaton add =
        ns.arithmetic("x", "y", "z", ArithmeticOperator.Ops.PLUS);

    // Weights are ..., 15, 8, 4, 2, 1.
    // 7 + 8 = 15.
    Assertions.assertTrue(
        accepts(add, "00111", "01000", "10000"));

    // 7 + 8 != 16.
    Assertions.assertFalse(
        accepts(add, "00111", "01000", "10001"));

    // 01111 also has value 15, but is not a legal Tetranacci representation.
    Assertions.assertFalse(
        accepts(add, "00111", "01000", "01111"));
  }

  @Test
  void testPentanacciAddition() {
    NumberSystem ns = new NumberSystem(PENTA);
    Automaton add =
        ns.arithmetic("x", "y", "z", ArithmeticOperator.Ops.PLUS);

    // Weights are ..., 31, 16, 8, 4, 2, 1.
    // 15 + 16 = 31.
    Assertions.assertTrue(
        accepts(add, "001111", "010000", "100000"));

    // 15 + 16 != 32.
    Assertions.assertFalse(
        accepts(add, "001111", "010000", "100001"));

    // 011111 also has value 31, but is not a legal Pentanacci representation.
    Assertions.assertFalse(
        accepts(add, "001111", "010000", "011111"));
  }

  private static boolean accepts(Automaton automaton, String... tracks) {
    int length = tracks[0].length();
    for (String track : tracks) {
      Assertions.assertEquals(length, track.length());
    }

    var dfa = automaton.fa.FAtoCompactDFA();
    int state = dfa.getIntInitialState();

    for (int i = 0; i < length; i++) {
      List<Integer> tuple = new ArrayList<>(tracks.length);
      for (String track : tracks) {
        tuple.add(track.charAt(i) - '0');
      }

      int symbol = automaton.richAlphabet.encode(tuple);
      state = dfa.getSuccessor(state, symbol);
      if (state < 0) {
        return false;
      }
    }

    return dfa.isAccepting(state);
  }
}