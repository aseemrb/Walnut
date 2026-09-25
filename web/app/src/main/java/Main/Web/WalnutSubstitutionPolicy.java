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

package Main.Web;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Tells TeaVM which classes the browser build replaces, and with what. A class named a.b.C listed
 * here is compiled from web.a.b.WC instead. The policy is registered through
 * META-INF/services/org.teavm.extension.spi.substitution.SubstitutionPolicy.
 *
 * The substitutes are compiled against the same Walnut and library jars as the originals, so a
 * signature change in an original that the substitute does not follow fails this module's build.
 */
public final class WalnutSubstitutionPolicy extends SimpleSubstitutionPolicy {
  @Override
  public void contribute(SubstitutionSink sink) {
    sink.selectClasses(named("Automata.FA.OtfDeterminizer")
            .or(named("net.automatalib.common.setting.AutomataLibSettings")))
        .packagePrefix("web.")
        .simpleNamePrefix("W");
  }
}
