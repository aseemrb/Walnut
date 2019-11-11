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

package Main;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import Automata.Automaton;
import Automata.NumberSystem;
import Automata.OstrowskiNumeration;

/**
 * This class contains the main method. It is responsible to get a command from user
 * and parse and dispatch the command appropriately.
 * @author Hamoon
 */
public class Prover {
	static String REGEXP_FOR_THE_LIST_OF_COMMANDS = "(eval|def|macro|reg|load|ost|exit|quit)";
	static String REGEXP_FOR_EMPTY_COMMAND = "^\\s*(;|::|:)\\s*$";
	/**
	 * the high-level scheme of a command is a name followed by some arguments and ending in either ; : or ::
	 */
	static String REGEXP_FOR_COMMAND = "^\\s*(\\w+)(\\s+.*)?(;|::|:)\\s*$";
	static Pattern PATTERN_FOR_COMMAND = Pattern.compile(REGEXP_FOR_COMMAND);

	static String REGEXP_FOR_exit_COMMAND = "^\\s*(exit|quit)\\s*(;|::|:)$";

	static String REGEXP_FOR_load_COMMAND = "^\\s*load\\s+(\\w+\\.txt)\\s*(;|::|:)\\s*$";
	/**
	 * group for filename in REGEXP_FOR_load_COMMAND
	 */
	static int L_FILENAME = 1;
	static Pattern PATTERN_FOR_load_COMMAND = Pattern.compile(REGEXP_FOR_load_COMMAND);

	static String REGEXP_FOR_eval_def_COMMANDS = "^\\s*(eval|def)\\s+([a-zA-Z]\\w*)((\\s+([a-zA-Z]\\w*))*)\\s+\"(.*)\"\\s*(;|::|:)\\s*$";
	/**
	 * important groups in REGEXP_FOR_eval_def_COMMANDS
	 */
	static int ED_TYPE = 1, ED_NAME = 2, ED_FREE_VARIABLES = 3 ,ED_PREDICATE = 6, ED_ENDING = 7;
	static Pattern PATTERN_FOR_eval_def_COMMANDS = Pattern.compile(REGEXP_FOR_eval_def_COMMANDS);
	static String REXEXP_FOR_A_FREE_VARIABLE_IN_eval_def_COMMANDS = "[a-zA-Z]\\w*";
	static Pattern PATTERN_FOR_A_FREE_VARIABLE_IN_eval_def_COMMANDS = Pattern.compile(REXEXP_FOR_A_FREE_VARIABLE_IN_eval_def_COMMANDS);

	static String REGEXP_FOR_macro_COMMAND = "^\\s*macro\\s+([a-zA-Z]\\w*)\\s+\"(.*)\"\\s*(;|::|:)\\s*$";
	static int M_NAME = 1,M_DEFINITION = 2;
	static Pattern PATTERN_FOR_macro_COMMAND = Pattern.compile(REGEXP_FOR_macro_COMMAND);

	static String REGEXP_FOR_reg_COMMAND = "^\\s*(reg)\\s+([a-zA-Z]\\w*)\\s+((((msd|lsd)_(\\d+|\\w+))|((msd|lsd)(\\d+|\\w+))|(msd|lsd)|(\\d+|\\w+))|(\\{(\\s*(\\+|\\-)?\\s*\\d+)(\\s*,\\s*(\\+|\\-)?\\s*\\d+)*\\s*\\}))\\s+\"(.*)\"\\s*(;|::|:)\\s*$";

	/**
	 * important groups in REGEXP_FOR_reg_COMMAND
	 */
	static int R_NAME = 2,R_ALPHABET = 3,R_NUMBER_SYSTEM = 4,R_SET = 13, R_REGEXP = 18;
	static Pattern PATTERN_FOR_reg_COMMAND = Pattern.compile(REGEXP_FOR_reg_COMMAND);
	static String REXEXP_FOR_A_SINGLE_ELEMENT_OF_A_SET = "(\\+|\\-)?\\s*\\d+";
	static Pattern PATTERN_FOR_A_SINGLE_ELEMENT_OF_A_SET = Pattern.compile(REXEXP_FOR_A_SINGLE_ELEMENT_OF_A_SET);

	static Pattern PATTERN_FOR_A_SINGLE_NOT_SPACED_WORD = Pattern.compile("\\w+");

	static String REGEXP_FOR_ost_COMMAND = "^\\s*ost\\s+([a-zA-Z]\\w*)\\s*\\[\\s*((\\d+\\s*)*)\\]\\s*\\[\\s*((\\d+\\s*)*)\\]\\s*(;|:|::)\\s*$";
	static Pattern PATTERN_FOR_ost_COMMAND = Pattern.compile(REGEXP_FOR_ost_COMMAND);
	static int GROUP_OST_NAME = 1;
	static int GROUP_OST_PREPERIOD = 2;
	static int GROUP_OST_PERIOD = 4;
	static int GROUP_OST_END = 6;

	/**
	 * if the command line argument is not empty, we treat args[0] as a filename.
	 * if this is the case, we read from the file and load its commands before we submit control to user.
	 * if the the address is not a valid address or the file does not exist, we print an appropriate error message
	 * and submit control to the user.
	 * if the file contains the exit command we terminate the program.
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		UtilityMethods.setPaths();
		// IntegrationTest IT = new IntegrationTest(true);
		//IT.runTestCases(384);
		//IT.runPerformanceTest("Walnut with Valmari without refactoring", 5);
		//IT.runPerformanceTest("Walnut with dk.bricks", 5);
		//IT.createTestCases();
		run(args);
	}

	public static void run(String[] args){
		BufferedReader in = null;
		if(args.length >= 1){
			//reading commands from the file with address args[0]
			try{
				in = new BufferedReader(
					new InputStreamReader(
						new FileInputStream(
							UtilityMethods.get_address_for_command_files() + args[0]),
						"utf-8"));
				if(!readBuffer(in, false)) return;
			}
			catch (IOException e) {
				System.out.flush();
				System.err.println(e.getMessage());
				//e.printStackTrace();
			} finally {
				try {
					if (in != null) {
						in.close();
					}
				} catch (IOException ex) {
					System.out.flush();
					System.err.println(ex.getMessage());
				}
			}
		}

		// Now we parse commands from the console.
		in = new BufferedReader(new InputStreamReader(System.in));
		readBuffer(in, true);
	}

	/**
	 * Takes a BufferedReader and reads from it until we hit end of file or exit command.
	 * @param in
	 * @param console = true if in = System.in
	 * @return
	 */
	public static boolean readBuffer(BufferedReader in, boolean console){
		try{
		    StringBuffer buffer = new StringBuffer();
			while(true) {
				if (console) {
					System.out.print(UtilityMethods.PROMPT);
				}

				String s = in.readLine();
				if(s == null) {
					return true;
				}

		    	int index1 = s.indexOf(';');
		    	int index2 = s.indexOf(':');
		    	int index;
		    	if(index1 != -1 && index2 != -1) {
		    		index = (index1 < index2) ? index1 : index2;
		    	} else if(index1 != -1) {
					index = index1;
				} else {
					index = index2;
				}

		    	if((s.length() - 1) > index && s.charAt(index + 1) == ':') {
		    		index++;
		    	}

		    	if(index != -1) {
		    		s = s.substring(0, index + 1);
		    		buffer.append(s);
		    		s = buffer.toString();
		    		if(!console) {
		    			System.out.println(s);
		    		}

		    		try {
		    			if(!dispatch(s)) {
		    				return false;
		    			}
		    		} catch(Exception e) {
		    			System.out.flush();
		    			System.err.println(
		    				UtilityMethods.COLOR_FAILED +
		    				e.getMessage() +
		    				UtilityMethods.COLOR_RESET +
		    				UtilityMethods.newLine() + "\t: " + s);
		    			System.err.flush();
		    		}

		    		buffer = new StringBuffer();
		    	} else {
		    		buffer.append(s);
		    	}
		    }
		} catch(IOException e) {
			System.out.flush();
			System.err.println(e.getMessage());
			System.err.flush();
		}

		return true;
	}

	public static boolean dispatch(String s) throws Exception{
		if(s.matches(REGEXP_FOR_EMPTY_COMMAND)) {
			// If the command is just ; or : do nothing.
			return true;
		}

		Matcher matcher_for_command = PATTERN_FOR_COMMAND.matcher(s);
		if(!matcher_for_command.find()) {
			throw new Exception("Invalid command.");
		}

		String commandName = matcher_for_command.group(1);
		if(!commandName.matches(REGEXP_FOR_THE_LIST_OF_COMMANDS)) {
			throw new Exception("No such command exists.");
		}

		if(commandName.equals("exit") || commandName.equals("quit")){
			if(s.matches(REGEXP_FOR_exit_COMMAND)) {
				return false;
			}

			throw new Exception("Invalid command.");
		} else if(commandName.equals("load")) {
			if(!loadCommand(s)) return false;
		} else if(commandName.equals("eval") || commandName.equals("def")) {
			eval_def_commands(s);
		} else if(commandName.equals("macro")) {
			macroCommand(s);
		} else if(commandName.equals("reg")) {
			regCommand(s);
		} else if(commandName.equals("ost")) {
			ostCommand(s);
		} else {
			throw new Exception("Invalid command " + commandName + ".");
		}
		return true;
	}

	public static TestCase dispatchForIntegrationTest(String s) throws Exception{
		if(s.matches(REGEXP_FOR_EMPTY_COMMAND)){//if the command is just ; or : do nothing
			return null;
		}

		Matcher matcher_for_command = PATTERN_FOR_COMMAND.matcher(s);
		if(!matcher_for_command.find())throw new Exception("Invalid command.");

		String commandName = matcher_for_command.group(1);
		if(!commandName.matches(REGEXP_FOR_THE_LIST_OF_COMMANDS)) {
			throw new Exception("No such command exists.");
		}

		if(commandName.equals("exit") || commandName.equals("quit")) {
			if(s.matches(REGEXP_FOR_exit_COMMAND)) return null;
			throw new Exception("Invalid command.");
		} else if(commandName.equals("load")){
			if(!loadCommand(s)) return null;
		} else if(commandName.equals("eval") || commandName.equals("def")) {
			return eval_def_commands(s);
		} else if(commandName.equals("macro")) {
			return macroCommand(s);
		} else if(commandName.equals("reg")) {
			return regCommand(s);
		} else {
			throw new Exception("Invalid command: " + commandName);
		}
		return null;
	}

	/**
	 * load x.p; loads commands from the file x.p. The file can contain any command except for load x.p;
	 * The user don't get a warning if the x.p contains load x.p but the program might end up in an infinite loop.
	 * Note that the file can contain load y.p; whenever y != x and y exist.
	 * @param s
	 * @return
	 * @throws Exception
	 */
	public static boolean loadCommand(String s) throws Exception {
		Matcher m = PATTERN_FOR_load_COMMAND.matcher(s);
		if(!m.find()) throw new Exception("Invalid use of load command.");
		BufferedReader in = null;

		try {
			in = new BufferedReader(
				new InputStreamReader(
					new FileInputStream(
						UtilityMethods.get_address_for_command_files() +
						m.group(L_FILENAME)),
					"utf-8"));
			if(!readBuffer(in,false)) {
				return false;
			}
		} catch (IOException e) {
			System.out.flush();
			System.err.println(e.getMessage());
			System.err.flush();
		}
		return true;
	}

	public static TestCase eval_def_commands(String s) throws Exception {
		Automaton M = null;

		Matcher m = PATTERN_FOR_eval_def_COMMANDS.matcher(s);
		if(!m.find()) {
			throw new Exception("Invalid use of eval/def command.");
		}

		List<String> free_variables = new ArrayList<String>();
		if(m.group(ED_FREE_VARIABLES)!= null) {
			which_matrices_to_compute(m.group(ED_FREE_VARIABLES), free_variables);
		}

		boolean printSteps = m.group(ED_ENDING).equals(":");
		boolean printDetails = m.group(ED_ENDING).equals("::");

		Computer c = new Computer(m.group(ED_PREDICATE), printSteps, printDetails);
		c.write(UtilityMethods.get_address_for_result() + m.group(ED_NAME)+".txt");
		c.drawAutomaton(UtilityMethods.get_address_for_result() + m.group(ED_NAME) + ".gv");
		if(free_variables.size() > 0) {
			c.writeMatrices(
				UtilityMethods.get_address_for_result()+m.group(ED_NAME)+".mpl", free_variables);
		}

		c.writeLog(UtilityMethods.get_address_for_result() + m.group(ED_NAME) + "_log.txt");
		if(printDetails) {
			c.writeDetailedLog(
				UtilityMethods.get_address_for_result() + m.group(ED_NAME) + "_detailed_log.txt");
		}

		if(m.group(ED_TYPE).equals("def")) {
			c.write(UtilityMethods.get_address_for_automata_library() + m.group(ED_NAME) + ".txt");
		}

		M = c.getTheFinalResult();
		return new TestCase(s, M, "", c.mpl, printDetails ? c.log_details.toString() : "");
	}

	public static TestCase macroCommand(String s) throws Exception {
		Matcher m = PATTERN_FOR_macro_COMMAND.matcher(s);
		if(!m.find())throw new Exception("invalid use of macro command");

		try{
			BufferedWriter out =
					new BufferedWriter(
							new OutputStreamWriter(
									new FileOutputStream(
											UtilityMethods.get_address_for_macro_library()+m.group(M_NAME)+".txt"), "utf-8"));
			out.write(m.group(M_DEFINITION));
			out.close();
		}
		catch (Exception o){
			System.out.println("Could not write the macro " + m.group(M_NAME));
		}
		return null;
	}

	public static TestCase regCommand(String s) throws Exception {
		Matcher m = PATTERN_FOR_reg_COMMAND.matcher(s);
		if(!m.find())throw new Exception("invalid use of reg command");
		NumberSystem ns = null;
		List<Integer> alphabet = null;
		if((m.group(R_NUMBER_SYSTEM)!=null)){
			String base = "msd_2";
			if(m.group(5) != null)base = m.group(5);
			if(m.group(8) != null)base = "msd_"+m.group(8);
			if(m.group(11) != null)base =  m.group(11)+"_2";
			if(m.group(12) != null)base = "msd_"+m.group(12);
			try{
				if(!Predicate.number_system_Hash.containsKey(base))
					Predicate.number_system_Hash.put(base, new NumberSystem(base));
				ns = Predicate.number_system_Hash.get(base);
			}catch(Exception e){
				throw new Exception("number system " + base + " does not exist: char at " + m.start(R_NUMBER_SYSTEM)+UtilityMethods.newLine()+"\t:"+e.getMessage());
			}
			alphabet = ns.getAlphabet();
		}

		else if(m.group(R_SET) != null){
			alphabet = what_is_the_alphabet(m.group(R_SET));
		}
		Automaton R = new Automaton(m.group(R_REGEXP),alphabet,ns);
		R.draw(UtilityMethods.get_address_for_result()+m.group(R_NAME)+".gv",m.group(R_REGEXP));
		R.write(UtilityMethods.get_address_for_result()+m.group(R_NAME)+".txt");
		R.write(UtilityMethods.get_address_for_automata_library()+m.group(R_NAME)+".txt");

		return new TestCase(s,R,"","","");
	}

	public static void ostCommand(String s) throws Exception {
		Matcher m = PATTERN_FOR_ost_COMMAND.matcher(s);
		if(!m.find()) {
			throw new Exception("Invalid use of ost command.");
		}

		OstrowskiNumeration ostr = new OstrowskiNumeration(
			m.group(GROUP_OST_NAME),
			m.group(GROUP_OST_PREPERIOD),
			m.group(GROUP_OST_PERIOD));
		ostr.createRepresentationAutomaton();
		ostr.createAdderAutomaton();
	}

	private static void which_matrices_to_compute(String s, List<String> L){
		Matcher m1 = PATTERN_FOR_A_FREE_VARIABLE_IN_eval_def_COMMANDS.matcher(s);
		while (m1.find()) {
		    String t = m1.group();
		    L.add(t);
		}
	}

	private static List<Integer> what_is_the_alphabet(String s){
		List<Integer> L = new ArrayList<Integer>();
		s = s.substring(1, s.length()-1); //truncation { and } from beginning and end
		Matcher m = PATTERN_FOR_A_SINGLE_ELEMENT_OF_A_SET.matcher(s);
		while(m.find()){
			L.add(UtilityMethods.parseInt(m.group()));
		}
		UtilityMethods.removeDuplicates(L);

		return L;
	}
}
