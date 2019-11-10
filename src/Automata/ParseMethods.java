/*	 Copyright 2016 Hamoon Mousavi
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

package Automata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Main.Predicate;
import Main.UtilityMethods;

public class ParseMethods {
	static int ALPHABET_SET = 12;
	static int ALPHABET_NUMBER_SYSTEM = 2;
	static int STATE_DECLARATION_STATE_NAME = 1;
	static int STATE_DECLARATION_OUTPUT = 2;
	static int TRANSITION_INPUT = 1;
	static int TRANSITION_DESTINATION = 6;

	static String REGEXP_FOR_TRUE_FALSE = "^\\s*(true|false)\\s*$";
	static Pattern PATTERN_FOR_TRUE_FALSE = Pattern.compile(REGEXP_FOR_TRUE_FALSE);

	// static String REGEXP_FOR_ALPHABET_DECLARATION = "^(\\s*((((msd|lsd)_(\\d+|\\w+))|((msd|lsd)(\\d+|\\w+))|(msd|lsd)|(\\d+|\\w+))|(\\{\\s*(\\+|\\-)?\\s*\\d+\\s*(\\s*,\\s*(\\+|\\-)?\\s*\\d+\\s*)*\\}))\\s*)+\\s*$";

	static String NEXT_ALPHABET_TOKEN = "\\G\\s*((((msd|lsd)_(\\d+|\\w+))|((msd|lsd)(\\d+|\\w+))|(msd|lsd)|(\\d+|\\w+))|(\\{\\s*((\\+|\\-)?\\s*\\d+\\s*(\\s*,\\s*(\\+|\\-)?\\s*\\d+)*)\\s*\\}))\\s*";
	static Pattern PATTERN_NEXT_ALPHABET_TOKEN = Pattern.compile(NEXT_ALPHABET_TOKEN);

	static String ELEMENT = "\\G\\s*,?\\s*(((\\+|\\-)?\\s*\\d+)|\\*)";
	static Pattern PATTERN_ELEMENT = Pattern.compile(ELEMENT);

	static String REGEXP_FOR_STATE_DECLARATION = "^\\s*(\\d+)\\s+((\\+|\\-)?\\s*\\d+)\\s*$";
	static Pattern PATTERN_FOR_STATE_DECLARATION = Pattern.compile(REGEXP_FOR_STATE_DECLARATION);

	static String REGEXP_FOR_TRANSITION = "^\\s*((((\\+|\\-)?\\s*\\d+\\s*)|(\\s*\\*\\s*))+)\\s*\\->\\s*((\\d+\\s*)+)\\s*$";
	static Pattern PATTERN_FOR_TRANSITION = Pattern.compile(REGEXP_FOR_TRANSITION);

	public static boolean parseTrueFalse(String s,boolean[] singleton){
		Matcher m = PATTERN_FOR_TRUE_FALSE.matcher(s);
		if(m.find()){
			singleton[0] = m.group(1).equals("true");
			return true;
		}
		return false;
	}

	public static boolean parseAlphabetDeclaration(
		String s,
		List<List<Integer>> A,
		List<NumberSystem> bases) throws Exception {
		//if(!s.matches(REGEXP_FOR_ALPHABET_DECLARATION))return false;
		Matcher m = PATTERN_NEXT_ALPHABET_TOKEN.matcher(s);
		int index = 0;
		while(m.find(index)){
			if(m.group(ALPHABET_SET) != null){
				List<Integer> list = new ArrayList<Integer>();
				parseList(m.group(ALPHABET_SET),list);
				A.add(list);
				bases.add(null);
			}

			if(m.group(ALPHABET_NUMBER_SYSTEM) != null) {
				String ns = "msd_2";
				if(m.group(3) != null) {
					ns = m.group(3);
				}

				if(m.group(6) != null) {
					ns = "msd_" + m.group(6);
				}

				if(m.group(9) != null) {
					ns =  m.group(9) + "_2";
				}

				if(m.group(10) != null) {
					ns = "msd_" + m.group(10);
				}

				HashMap<String,NumberSystem> H = Predicate.get_number_system_Hash();
				if(!H.containsKey(ns)) {
					try {
						H.put(ns, new NumberSystem(ns));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						throw new Exception(
							e.getMessage() + UtilityMethods.newLine() +
							"\tNumber system " + ns + " does not exist.");
					}
				}

				A.add(H.get(ns).getAlphabet());
				bases.add(H.get(ns));
			}

			index = m.end();
		}

		if(index < s.length()) {
			return false;
		}
		return true;
	}
	public static boolean parseStateDeclaration(String s, int[] pair) {
		Matcher m = PATTERN_FOR_STATE_DECLARATION.matcher(s);
		if(m.find()){
			pair[0] = UtilityMethods.parseInt(m.group(STATE_DECLARATION_STATE_NAME));
			pair[1] = UtilityMethods.parseInt(m.group(STATE_DECLARATION_OUTPUT));
			return true;
		}

		return false;
	}

	public static boolean parseTransition(
		String s,
		List<Integer> input,
		List<Integer> dest) {
		Matcher m = PATTERN_FOR_TRANSITION.matcher(s);
		if(m.find()){
			parseList(m.group(TRANSITION_INPUT), input);
			parseList(m.group(TRANSITION_DESTINATION), dest);
			return true;
		}
		return false;
	}

	public static void parseList(String s, List<Integer> list) {
		int index = 0;
		Matcher m = PATTERN_ELEMENT.matcher(s);
		while(m.find(index)){
			if(m.group(1).equals("*"))list.add(null);
			else list.add(UtilityMethods.parseInt(m.group(1)));
			index = m.end();
		}
	}
}
