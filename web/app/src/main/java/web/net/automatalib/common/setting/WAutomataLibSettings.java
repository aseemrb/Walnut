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

package web.net.automatalib.common.setting;

import net.automatalib.common.setting.AutomataLibProperty;

/**
 * Browser substitute for AutomataLib's settings loader (see WalnutSubstitutionPolicy). The original
 * discovers settings through the classpath, local files, and ServiceLoader, none of which exist in the
 * browser. Walnut never overrides an AutomataLib setting, so returning the caller's default matches
 * the JVM behavior exactly.
 */
public final class WAutomataLibSettings {
  private static final WAutomataLibSettings INSTANCE = new WAutomataLibSettings();

  private WAutomataLibSettings() {}

  public static WAutomataLibSettings getInstance() {
    return INSTANCE;
  }

  public String getProperty(AutomataLibProperty property) {
    return null;
  }

  public String getProperty(AutomataLibProperty property, String defaultValue) {
    return defaultValue;
  }
}
