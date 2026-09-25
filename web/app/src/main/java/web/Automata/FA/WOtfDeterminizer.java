/*	 Copyright 2026 Aseem Baranwal
 *
 * 	 This file is part of Walnut.
 *
 *   Walnut is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Walnut is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Walnut.  If not, see <http://www.gnu.org/licenses/>.
 */

package web.Automata.FA;

import Automata.FA.FA;
import Main.WalnutException;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Browser substitute for Automata.FA.OtfDeterminizer (see WalnutSubstitutionPolicy). The OTF library
 * behind the CCL and CCLS strategies uses thread pools, which browsers do not provide, so those
 * strategies are reported as unavailable. Every other strategy is unaffected.
 */
public final class WOtfDeterminizer {
  private WOtfDeterminizer() {}

  public static void determinize(FA fa, IntSet initialState, boolean doSimulation) {
    throw new WalnutException(
        "The CCL and CCLS determinization strategies are not available in the browser version of Walnut. "
            + "Use the default strategy, or run this command in the desktop version.");
  }
}
