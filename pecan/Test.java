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

A file of tests is divided into sections, separated from each other by a line
starting with four or more equal signs. Each section normally represents one
test, and is separated into two parts by a line starting with four or more dots.
The first part is an input string for the test, and the second part is the
expected output.

If a section contains only one part, then it represents a grammar to be used for
subsequent tests. The grammar may contain inclusions. If the grammar is a single
line consisting of just an inclusion, the target file may be another test file,
rather than just a grammar. If the grammar is a single comment line, it is
ignored.

Line endings in a test file are converted to \n when it is read in. To allow the
test file to contain control characters or unicode characters as plain text,
numerical escapes are supported, so \nnn represents a character by its decimal
code, or by its hex code if the first digit is zero. */

public class Test {
    private Source in;
    private Source out;
    private boolean grammar, trace;

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
        Source source = new Source(new File(file));
        List<Test> tests = makeTests(file, source, trace);
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

    // Divide the lines from a file into sections, and the sections into test
    // objects. If a section is a single line inclusion, include another test
    // file, else convert it into a test.
    private static List<Test> makeTests(String file, Source s, boolean trace) {
        List<Source> lines = s.lines();
        List<Test> tests = new ArrayList<Test>();
        int start = 0;
        String grammar = null;
        for (int i = 0; i <= lines.size(); i++) {
            if (i < lines.size() && ! lines.get(i).startsWith("====")) continue;
            Source line = lines.get(start);
            if (i == start + 1 && line.startsWith("{") && line.endsWith("}")) {
                String file2 = line.substring(1, line.length() - 1);
                file2 = s.relativePath(file2);
                Source source = new Source(new File(file2));
                tests.addAll(makeTests(file2, source, trace));
            }
            else if (i != start + 1 || ! line.startsWith("--")) {
                Test t = makeTest(file, lines, start, i);
                if (trace) t.trace = true;
                tests.add(t);
            }
            start = i + 1;
        }
        return tests;
    }

    //--------------------------------------------------------------------

    // Create a test object from a range of lines.
    private static Test makeTest(String f, List<Source> lines, int s, int e) {
        int divider = -1;
        for (int i = s; i < e && divider < 0; i++) {
            if (lines.get(i).startsWith("....")) divider = i;
        }
        if (divider < 0) divider = e;
        Test test = new Test();
        if (divider > s) test.in = lines.get(s).extend(lines.get(divider-1));
        else test.in = new Source("");
        if (divider < e) test.out = lines.get(divider+1).extend(lines.get(e-1));
        else test.out = new Source("");
        if (divider != e) test.in = test.in.unescape();
        if (divider == e) test.grammar = true;
        return test;
    }

    // Run tests from a given file on a given object. If line > 0, run just the
    // one test that starts on that line. Return the number of tests passed.
    private static int runTests(Testable object, List<Test> tests, int line) {
        int passed = 0;
        for (Test test : tests) {
            if (! test.grammar &&
                line > 0 && test.in.lineNumber(0) != line) continue;
            String message;
            if (test.trace) object.tracing(true);
            if (test.grammar) message = object.grammar(test.in);
            else {
                Object obj = object.run(test.in);
                String out = obj.toString();
                message = test.check(out);
                if (message == null) passed++;
            }
            if (message != null) {
                System.err.println(message);
                System.exit(1);
            }
        }
        return passed;
    }

    private static void report(boolean unitTest, String name, int line, int n) {
        if (n == 0) System.out.println(
            "No test on line " + line + ".");
        else if (unitTest && n == 1) System.out.println(
            name + " class OK, pass 1 test.");
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
*/
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
        int lineNo = in.lineNumber(0);
        String path = in.path();
        if (out.equals(out)) return null;
        String result = "";
        result += "Fail test on line " + lineNo + " of " + path + ":\n";
        result += "---------- Expected ----------\n";
        result += out;
        result += "---------- Actual ----------\n";
        result += out;
        return result;
    }
}
