package Automata.FA;

import Main.Logging;
import OTF.NFATrim;
import OTF.OTFDeterminization;
import OTF.Model.DeterminizeRecord;
import OTF.Model.Threshold;
import OTF.Registry.AntichainForestRegistry;
import OTF.Registry.Registry;
import OTF.Simulation.ParallelSimulation;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.ts.AcceptorPowersetViewTS;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;

/**
 * The on-the-fly (CCL / CCLS) determinization strategies, implemented with the OTF library.
 * Moved unchanged from DeterminizationStrategies so that this is the only class referencing OTF;
 * the browser build substitutes it, since OTF relies on thread pools that browsers do not provide.
 */
final class OtfDeterminizer {
  private OtfDeterminizer() {}

  static void determinize(FA fa, IntSet initialState, boolean doSimulation) {
    long timeBefore = System.currentTimeMillis();

    CompactNFA<Integer> compactNFA = fa.FAtoCompactNFA(initialState);
    CompactNFA<Integer> reduced = NFATrim.bisim(compactNFA);
    if (reduced.size() < fa.getQ()) {
      Logging.logMessage("Bisimulation reduced to " + reduced.size() + " states");
    }
    ArrayList<BitSet> simRels = new ArrayList<>();
    if (doSimulation) {
      Logging.logMessage("Calculating simulation relations; this can be resource-intensive");
      int prevSize = reduced.size();
      reduced = ParallelSimulation.fullyComputeRels(reduced, simRels, true);
      if (reduced.size() != prevSize) {
        Logging.logMessage("Simulation altered to " + reduced.size() + " states");
      }
      if (!simRels.isEmpty()) {
        int simCount = 0;
        for (BitSet b : simRels) {
          if (b != null) {
            simCount += b.cardinality();
          }
        }
        Logging.logMessage("Found " + simCount + " simulation relations");
      }
    }
    final Threshold threshold = Threshold.adaptiveSteps(4000);
    Registry registry = new AntichainForestRegistry<>(reduced, simRels.toArray(new BitSet[0]));
    simRels.clear(); // help GC
    Alphabet<Integer> inputs = reduced.getInputAlphabet();
    AcceptorPowersetViewTS<BitSet, Integer, Integer> nfa = reduced.powersetView();
    Deque<DeterminizeRecord<BitSet>> stack = new ArrayDeque<>();

    BitSet init = nfa.getInitialState();
    boolean initAcc = nfa.isAccepting(init);
    CompactDFA<Integer> out = new CompactDFA<>(inputs);
    int initOut = out.addInitialState(initAcc);

    registry.put(init, initOut);

    stack.push(new DeterminizeRecord<>(init, initOut));
    BitSet finishedStates = new BitSet();
    Deque<Integer> stateBuffer = new ArrayDeque<>();

    int numInputs = fa.getAlphabetSize();

    long statesExplored = 0;

    while (!stack.isEmpty()) {
      if (Logging.shouldPrintDetails()) {
        if (statesExplored == 1e2 || statesExplored == 1e3 || statesExplored % 1e4 == 0) {
          int statesSoFar = out.size() - stateBuffer.size();
          int queueSize = stack.size();
          long timeAfter = System.currentTimeMillis();
          Logging.logMessage(true,
              "  Progress: Explored " + statesExplored + " states - "
                  + queueSize + " states left in queue - " + statesSoFar + " states added - "
                  + (timeAfter - timeBefore) + "ms");
        }
      }
      DeterminizeRecord<BitSet> curr = stack.pop();
      BitSet inState = curr.inputState();
      int outState = curr.outputAddress();
      boolean complete = true;
      for (int i = 0; i < numInputs; ++i) {
        BitSet succ = nfa.getSuccessor(inState, i);
        int outSucc = registry.get(succ);
        if (outSucc == Registry.MISSING_ELEMENT) {
          complete = false;
          final boolean succAcc = nfa.isAccepting(succ);
          // add new state to DFA and to stack
          if (stateBuffer.isEmpty()) {
            outSucc = out.addState(succAcc);
          } else {
            outSucc = stateBuffer.pop();
            out.setAccepting(outSucc, succAcc);
          }
          registry.put(succ, outSucc);
          stack.push(new DeterminizeRecord<>(succ, outSucc));
        }
        out.setTransition(outState, inputs.getSymbolIndex(i), outSucc);
      }
      statesExplored++;

      finishedStates.set(outState);

      if (complete && threshold.test(out)) {
        int oldStatesSoFar = out.size() - stateBuffer.size();
        OTFDeterminization.otfMinimization(inputs, out, finishedStates, stateBuffer, registry);
        int statesSoFar = out.size() - stateBuffer.size();
        threshold.update(statesSoFar);
        long timeAfter = System.currentTimeMillis();
        Logging.logMessage(
            "  Progress: Periodic minimization: " + oldStatesSoFar + " -> " + statesSoFar + " states added - " + (timeAfter - timeBefore) + "ms");
      }
    }
    fa.setFromAutomataLibDFA(out, out.getInputAlphabet());
  }
}
