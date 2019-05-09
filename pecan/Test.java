// Pecan 5 testing. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.text.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;

/* Run a collection of tests. This supports both internal unit tests for pecan
classes, and external tests for user grammars. To run tests, the static method
Test.run is called with a representative object of one of the pecan classes and
the name of a file of tests. The object implements the Testable interface, so
that this Test class has no dependencies on the other pecan classes, and
therefore unit testing of a class can be done even when other classes are
broken. The Testable interface describes a single test method. For each test,
the test method is called on the object, and its output is compared with the
expected output. ParseExceptions are used to signal failures during testing.

In a file of tests, each test has up to three sections: a grammar, a sample
input string, and an expected output string. Tests are separated from each other
by a line of three or more equal signs. A grammar is separated from the rest of
a test by a line of minus signs. An input string is separated from an output
string by a line of dots. If the grammar section is omitted, the test uses the
same grammar as the previous test. If the input string is omitted, e.g. for some
of the early pecan passes, it is assumed to be the empty string. Line endings in
a test file are converted to \n when it is read in.

To allow the grammar or sample input or expected output to contain control
characters or unicode characters as plain text, \nnn represents a character by
its decimal code, or by its hex code if the code starts with zero, \\ represents
a single backslash, and a \ followed by any other character removes the
character. In particular, \ followed by a space can be used as a separator, and
\ followed by a newline can be used to cancel the newline. For example, given
that 960 is the decimal code for the character pi, then:

   \960x      is pi followed by x
   \960\ 5    is pi followed by the digit 5
   \\960x     is the five characters \960x
   ...\13\    is a line ending in CR instead of LF
*/

public class Test {
    private String fileName;
    private int lineNo;
    private String grammar, input, output;

    // No testing of the test class.
    public static void main(String[] args) { }

    String input() { return input; }
    String output() { return output; }

    // Run tests from a given file on a given object. If line > 0, run just the
    // one test that starts on that line. Return the number of tests passed.
    static int run(String file, Testable object, int line) {
        List<Test> tests = extract(file);
        int passed = 0;
        for (Test test : tests) {
            if (line > 0 && test.lineNo != line) continue;
            String out;
            try { out = object.test(test.grammar, test.input); }
            catch (ParseException e) {
                out = e.getMessage();
                if (out == null) out = "" + e;
            }
            catch (Exception e) {
                out = e.toString() + "\n";
                if (out == null) out = "" + e + "\n";
                for (StackTraceElement t : e.getStackTrace()) {
                    out += t + "\n";
                }
            }
            String message = test.check(out);
            if (message == null) { passed++; continue; }
            System.err.print(message);
            System.exit(1);
        }
        return passed;
    }

    // Extract tests from a given file.
    static List<Test> extract(String fileName) {
        Path path = Paths.get(fileName);
        List<String> lines = null;
        try { lines = Files.readAllLines(path, StandardCharsets.UTF_8); }
        catch (Exception e) {
            System.err.println("Error: can't read " + fileName);
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
            if (t.grammar == null) t.grammar = grammar;
            else grammar = t.grammar;
            t.fileName = file;
            t.grammar = unescape(t.grammar);
            t.input = unescape(t.input);
            tests.add(t);
            start = i + 1;
        }
        return tests;
    }

    // Check whether a line consists of one repeated character.
    private static boolean all(String line, char ch) {
        if (line.length() < 2) return false;
        for (int i=0; i<line.length(); i++) {
            if (line.charAt(i) != ch) return false;
        }
        return true;
    }

    // Create a test object from a range of lines.
    private static Test readTest(List<String> lines, int start, int end) {
        int endg = -1, endi = -1;
        for (int i=start; i<end; i++) {
            if (all(lines.get(i), '-')) endg = i;
            if (all(lines.get(i), '.')) endi = i;
        }
        Test test = new Test();
        test.lineNo = start + 1;
        test.grammar = endg < 0 ? null : "";
        test.input = "";
        test.output = "";
        if (endg < 0) endg = start - 1;
        if (endi < 0) endi = endg;
        for (int i=start; i<endg; i++) test.grammar += lines.get(i) + "\n";
        for (int i=endg+1; i<endi; i++) test.input += lines.get(i) + "\n";
        for (int i=endi+1; i<end; i++) test.output += lines.get(i) + "\n";
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
