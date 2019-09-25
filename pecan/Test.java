// Pecan 1.0 testing. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.text.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;

/* Run a collection of tests. This supports both internal unit tests for pecan
classes, and external user tests. To run tests, call the static method Test.run.
A representative object of one of the pecan classes is passed as an argument.
The object implements the Testable interface, so that this Test class has no
dependencies on the other pecan classes, and therefore unit testing of a class
can be done even when other classes are broken. For each test, the test method
is called on the object, and its output is compared with the expected output.

A file of tests is divided into sections, separated from each other by a line of
three or more equal signs. Each section normally represents one test, and is
separated into two parts by a line of three or more minus signs. The first part
is an input string for the test, and the second part is the expected output.

If a section contains only one part, then it represents a grammar to be used for
subsequent tests. The grammar may contain inclusions. The grammar is a single
line consisting of just an inclusion, the target file may be another test file,
rather than just a grammar.

Line endings in a test file are converted to \n when it is read in. To allow the
test file to contain control characters or unicode characters as plain text,
\nnn represents a character by its decimal code, or by its hex code if the code
starts with zero, \\ represents a single backslash, and a \ followed by any
other character removes the character. In particular, \ followed by a space can
be used as a separator, and \ followed by a newline can be used to cancel the
newline. For example, given that 960 is the decimal code for the character pi,
then:

   \960x      is pi followed by x
   \960\ 5    is pi followed by the digit 5
   \\960x     is the five characters \960x
   ...\13\    is a line ending in CR instead of LF
*/

public class Test {
    private Source input;
    private String output;

    // No testing of the test class.
    public static void main(String[] args) { }

    // Run tests on the class which the object belongs to. The command line
    // arguments can optionally contain a filename of tests, a line number to
    // specify a single test from the file, or a "-t" or "-trace" option. The
    // default file is the unit test file for the given class.
    static void run(Testable object, String[] args) {
        String file = null;
        boolean trace = false;
        int line = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-trace")) trace = true;
            else if (args[i].equals("-t")) trace = true;
            else if (args[i].startsWith("-")) usage();
            else if (Character.isDigit(args[i].charAt(0))) {
                if (line != 0) usage();
                line = Integer.parseInt(args[i]);
            }
            else if (file != null) usage();
            else file = args[i];
        }
        boolean unitTest = file == null && line == 0;
        String name = object.getClass().getSimpleName();
        if (file == null) file = "tests/"+ name +".txt";
        List<Test> tests = makeTests(file, readFile(file));
        int n = runTests(object, tests, line);
        report(unitTest, name, line, n);
    }

    // Give a usage message and stop.
    private static void usage() {
        System.err.println(
            "Error: options are [-t | -trace] [line] [testfile]\n"
        );
        System.exit(1);
    }

    // Read a UTF8 file as a list of lines.
    private static List<String> readFile(String fileName) {
        Path path = Paths.get(fileName);
        try { return Files.readAllLines(path, StandardCharsets.UTF_8); }
        catch (Exception e) {
            System.err.println("Error: can't read " + fileName + e);
            System.exit(1);
            return null;
        }
    }

    // Divide the lines from a file into sections, and the sections into test
    // objects. If a section is a single line inclusion, include another test
    // file, else convert it into a test.
    private static List<Test> makeTests(String file, List<String> lines) {
        List<Test> tests = new ArrayList<Test>();
        int start = 0;
        String grammar = null;
        for (int i = 0; i <= lines.size(); i++) {
            if (i < lines.size() && ! lines.get(i).matches("====*")) continue;
            String line = lines.get(start);
            if (i == start + 1 && line.matches("\".*\"")) {
                String file2 = line.substring(1, line.length() - 1);
                tests.addAll(makeTests(file2, readFile(file2)));
            }
            else tests.add(makeTest(file, lines, start, i));
            start = i + 1;
        }
        return tests;
    }

    // Create s test object from a range of lines.
    private static Test makeTest(String f, List<String> lines, int s, int e) {
        int divider = -1;
        for (int i = s; i < e && divider < 0; i++) {
            if (lines.get(i).matches("----*")) divider = i;
        }
        if (divider < 0) divider = e;
        String in = "", out = "";
        for (int i = s; i < divider; i++) in += lines.get(i) + "\n";
        if (divider != e) in = unescape(in);
        for (int i = divider + 1; i < e; i++) out += lines.get(i) + "\n";
        Test test = new Test();
        test.input = new Source(in, f, s + 1);
        test.output = out;
        if (divider == e) test.input.grammar(true);
        return test;
    }

    // Run tests from a given file on a given object. If line > 0, run just the
    // one test that starts on that line. Return the number of tests passed.
    private static int runTests(Testable object, List<Test> tests, int line) {
        int passed = 0;
        for (Test test : tests) {
            if (line > 0 && test.input.firstLine() != line) continue;
            Object obj = object.run(test.input);
            String out = obj.toString();
            String message = test.check(out);
            if (message == null) { passed++; continue; }
            System.err.print(message);
            System.exit(1);
        }
        return passed;
    }

    private static void report(boolean unitTest, String name, int line, int n) {
        if (n == 0) System.out.println(
            "No test on line " + line + ".");
        else if (unitTest) System.out.println(
            name + " class OK, pass " + n + " tests.");
        else if (line > 0) System.out.println(
            "Pass test on line " + line + ".");
        else if (n == 1) System.out.println(
            "Pass 1 test.");
        else System.out.println(
            "Pass " + n + " tests.");
    }

/*
    // Run tests from a given file on a given object. If line > 0, run just the
    // one test that starts on that line. Return the number of tests passed.
    private static int runTests(String file, Testable object, int line) {
        List<Test> tests = extract(file);
        int passed = 0;
        for (Test test : tests) {
            if (test.isGrammar()) {
                String out = object.test("GRAMMAR:\n" + test.input());
                if (out == null) out = "";
                String message = test.check(out);
                if (message == null) continue;
                System.err.println(message);
                System.exit(1);
            }
            else if (test.isSubfile()) {
                File f = new File(file);
                f = new File(f.getParentFile(), test.input());
                String subfile = f.getPath();
                passed += runTests(subfile, object, 0);
                continue;
            }
            if (line > 0 && test.lineNo != line) continue;
            String out = object.test(test.input());
            String message = test.check(out);
            if (message == null) { passed++; continue; }
            System.err.print(message);
            System.exit(1);
        }
        return passed;
    }

    // Divide the lines from a file into test objects.
    private static List<Test> readTests(String file, List<String> lines) {
        List<Test> tests = new ArrayList<Test>();
        int start = 0;
        String grammar = null;
        for (int i=0; i<=lines.size(); i++) {
            if (i < lines.size() && ! all(lines.get(i), '=')) continue;
            readTest(tests, lines, start, i);
            if (t != null) {
                if (! t.isGrammar) t.input = unescape(t.input);
                t.fileName = file;
                tests.add(t);
            }
            start = i + 1;
        }
        return tests;
    }

    // Check whether a line consists of one repeated character.
    private static boolean all(String line, char ch) {
        if (line.length() < 3) return false;
        for (int i=0; i<line.length(); i++) {
            if (line.charAt(i) != ch) return false;
        }
        return true;
    }

    // Create a test object from a range of lines.
    private static Test readTest(List<String> lines, int start, int end) {
        int endi = -1;
        for (int i=start; i<end; i++) if (all(lines.get(i), '-')) endi = i;
        if (endi < 0) endi = end;
        Test test = new Test();
        test.input = test.output = "";
        test.lineNo = start + 1;
        for (int i=start; i<endi; i++) test.input += lines.get(i) + "\n";
        for (int i=endi+1; i<end; i++) test.output += lines.get(i) + "\n";
        if (end == start + 1) {
            if (test.input.startsWith("//")) return null;
            else if (test.input.indexOf('=') >= 0) test.isGrammar = true;
            else if (test.input.indexOf('.') >= 0) test.isSubfile = true;
            else test.isEntry = true;
        }
        if (test.isSubfile || test.isEntry) {
            test.input = test.input.substring(0, test.input.length() - 1);
        }
        else if (endi == end) test.isGrammar = true;
        return test;
    }
*/
    // Interpret escape characters in a string.
    static String unescape(String text) {
        String out = "";
        for (int i=0; i<text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\\') { out += ch; continue; }
            if (++i >= text.length()) return out;
            ch = text.charAt(i);
            if (ch == '\\') { out += "\\"; continue; }
            if (! Character.isDigit(ch)) continue;
            boolean hex = ch == '0';
            int end;
            for (end = i; end < text.length(); end++) {
                if (! digit(text.charAt(end), hex)) break;
            }
            int base = hex ? 16 : 10;
            int n = Integer.parseInt(text.substring(i, end), base);
            if (Character.charCount(n) == 1) {
                out += (char) n;
            }
            else {
                out += new String(Character.toChars(n));
            }
            i = end - 1;
        }
        return out;
    }

    // Check for a decimal or hex digit.
    private static boolean digit(char ch, boolean hex) {
        if (Character.isDigit(ch)) return true;
        if (! hex) return false;
        return "ABCDEFabcdef".indexOf(ch) >= 0;
    }

    // Escape the control and non-ascii characters in a string.
    static String escape(String text) {
        String out = "";
        for (int i=0; i<text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                int uc = text.codePointAt(i);
                out += "\\" + uc;
                i++;
                continue;
            }
            if (ch == '\\') { out += "\\\\"; continue; }
            if (ch >= ' ' && ch <= '~') { out += ch; continue; }
            if (ch == '\n') { out += ch; continue; }
            out += "\\" + (int) ch;
            if (i == text.length() - 1) continue;
            if (! Character.isDigit(text.charAt(i+1))) continue;
            out += "\\ ";
        }
        return out;
    }

    // Check the given actual output against the expected output. Return an
    // error message, or null for success.
    private String check(String out) {
        out = escape(out);
        int lineNo = input.firstLine();
        String fileName = input.fileName();
        if (out.equals(output)) return null;
        String result = "";
        result += "Fail test on line " + lineNo + " of " + fileName + ":\n";
        result += "---------- Expected ----------\n";
        result += output;
        result += "---------- Actual ----------\n";
        result += out;
        return result;
    }
}
