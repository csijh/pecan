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
    private Source original;
    private Source in;
    private Source out;
    private boolean grammar, trace, raw;

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
        List<Test> tests = makeTests(source, trace);
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
    private static List<Test> makeTests(Source s, boolean trace) {
        List<Test> tests = new ArrayList<Test>();
        int start = 0, end = s.indexOf("=====");
        while (end >= 0) {
            makeTest(tests, s.sub(start, end), trace);
            start = s.indexOf("\n", end) + 1;
            if (start >= s.length()) end = -1;
            else end = s.indexOf("=====", start);
        }
        if (start < s.length()) {
            makeTest(tests, s.sub(start, s.length()), trace);
        }
        return tests;
    }

    // Create a test object from a range of lines.
    private static void makeTest(List<Test> tests, Source s, boolean trace) {
        int d = s.indexOf(".....");
        if (d >= 0) {
            Test t = new Test();
            t.in = s.sub(0, d);
            int eol = s.indexOf("\n", d) + 1;
            t.out = s.sub(eol, s.length());
            t.trace = trace;
            tests.add(t);
            return;
        }
        int eol = s.indexOf("\n");
        if (eol == s.length() - 1) {
            if (s.startsWith("--")) return;
            if (s.startsWith("{") && s.endsWith("}\n")) {
                String file2 = s.substring(1, s.length() - 2);
                file2 = s.relativePath(file2);
                Source source2 = new Source(new File(file2));
                tests.addAll(makeTests(source2, trace));
                return;
            }
        }
        Test t = new Test();
        t.in = s.sub(0, s.length());
        t.out = s.sub(s.length(), s.length());
        t.grammar = true;
        tests.add(t);
    }

    // Run tests from a given file on a given object. If line > 0, run just the
    // one test that starts on that line. Return the number of tests passed.
    private static int runTests(Testable object, List<Test> tests, int line) {
        int passed = 0;
        for (Test test : tests) if (test.run(object, line)) passed++;
        return passed;
    }

    // Carry out the test. Return true if the test counts as passed.
    private boolean run(Testable object, int line) {
        String message;
        if (grammar) {
            message = object.grammar(in);
            if (message != null) err(message);
            return false;
        }
        if (line > 0 && in.lineNumber() != line) return false;
        if (trace) object.tracing(true);
        Object obj = object.run(in);
        message = check(obj.toString());
        if (message != null) err(message);
        return true;
    }

    // Print message and exit.
    private void err(String message) {
        System.err.println(message);
        System.exit(1);
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
            if (ch == '\\') { out += "\\92"; continue; }
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
    private String check(String s) {
//        String s1 = escape(s);
        String s1 = s;
        String s2 = out.text();
        int lineNo = in.lineNumber();
        String path = in.path();
        if (s1.equals(s2)) return null;
        String result = "";
        result += "Fail test on line " + lineNo + " of " + path + ":\n";
        result += "---------- Expected ----------\n";
        result += s2;
        result += "---------- Actual ----------\n";
        result += s1;
        return result;
    }
}
