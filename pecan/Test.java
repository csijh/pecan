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
classes, and external user tests. To run tests, one version of the static method
Test.run is called. A representative object of one of the pecan classes is
passed as an argument. The object implements the Testable interface, so that
this Test class has no dependencies on the other pecan classes, and therefore
unit testing of a class can be done even when other classes are broken. The
Testable interface describes a method for setting up a grammar and a method for
running a test. For each test, the test method is called on the object, and its
output is compared with the expected output.

A file of tests is divided into sections, separated from each other by a line of
three or more equal signs. Each section normally represents one test, and is
separated into two parts by a line of three or more minus signs. The first part
is an input string for the test, and the second part is the expected output.

If a section contains only one part, then it normally represents a grammar or
grammar fragment to be used for subsequent tests. If a section contains a single
line, and that line doesn't contain an equal sign, then it has a special
meaning. If the line starts with // it is a comment to be ignored. If the line
contains a dot, then it is the name or path of an external file of tests to be
included. If the line is a single word without any special characters, it is the
name of a rule in the current grammar, to be used as an entry point into the
grammar for subsequent tests.

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
    private String fileName;
    private int lineNo;
    private String input, output;
    private boolean isGrammar, isSubfile, isEntry;

    String input() { return input; }
    String output() { return output; }

    // If true, the input string is a grammar.
    boolean isGrammar() { return isGrammar; }

    // If true, the input string is the name of a subfile.
    boolean isSubfile() { return isSubfile; }

    // If true, the input string is the name of a rule.
    boolean isEntry() { return isEntry; }

    // No testing of the test class.
    public static void main(String[] args) { }

    // Run tests on the class which the object belongs to.
    static void run(Testable object) { run(object, 0); }

    // Run a single test, starting on a given line number.
    static void run(Testable object, int line) { run(null, object, line); }

    // Run tests from the given file.
    static void run(String file, Testable object) { run(file, object, 0); }

    // Run a test or tests from a file. If the file is null, use the default
    // test file. If the class of the object is Run, assume they are user tests.
    static void run(String file, Testable object, int line) {
        String name = object.getClass().getSimpleName();
        boolean user = name.equals("Run");
        if (file == null) file = "tests/"+ name +".txt";
        int n = runTests(file, object, line);
        if (n == 0) {
            System.out.println("No test on line "+ line +".");
            return;
        }
        if (user) {
            if (n == 1) System.out.println("Pass 1 test.");
            else System.out.println("Pass "+ n +" tests.");
            return;
        }
        if (line > 0) System.out.println("Pass test on line "+ line +".");
        else System.out.println(name +" class OK, pass "+ n +" tests.");
    }

    // Run tests from a given file on a given object. If line > 0, run just the
    // one test that starts on that line. Return the number of tests passed.
    private static int runTests(String file, Testable object, int line) {
        List<Test> tests = extract(file);
        int passed = 0;
        for (Test test : tests) {
            if (line > 0 && test.lineNo != line) continue;
            if (test.isGrammar()) {
                object.test("GRAMMAR:\n" + test.input());
            }
            else if (test.isSubfile()) {
                File f = new File(file);
                f = new File(f.getParentFile(), test.input());
                String subfile = f.getPath();
                passed += runTests(subfile, object, line);
            }
            else {
                String out = object.test(test.input());
                String message = test.check(out);
                if (message == null) { passed++; continue; }
                System.err.print(message);
                System.exit(1);
            }
        }
        return passed;
    }

    // Extract tests from a given file.
    static List<Test> extract(String fileName) {
        Path path = Paths.get(fileName);
        System.out.println("ext " + fileName);
        List<String> lines = null;
        try { lines = Files.readAllLines(path, StandardCharsets.UTF_8); }
        catch (Exception e) {
            System.err.println("Error: can't read " + fileName + e);
            System.exit(1);
        }
        return readTests(fileName, lines);
    }

    // Divide the lines from a file into test objects.
    private static List<Test> readTests(String file, List<String> lines) {
        List<Test> tests = new ArrayList<Test>();
        int start = 0;
        String grammar = null;
        for (int i=0; i<=lines.size(); i++) {
            if (i < lines.size() && ! all(lines.get(i), '=')) continue;
            Test t = readTest(lines, start, i);
            if (t == null) continue;
            t.input = unescape(t.input);
            t.fileName = file;
            tests.add(t);
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
