// Part of Pecan 4. Open source - see licence.txt.

package pecan;

import java.io.*;
import java.text.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;

/* Run text-based unit tests.  Supports both internal tests of the pecan
classes, and external tests of user grammars.  The tests for a pecan class X are
in the file tests/X.txt.  The classes implement the Testable interface, so that
unit testing of a class can be done even when other classes are currently
broken.  ParseExceptions are used to signal failures during any pass.

Each test is separated from the next by a line of equal signs.  A test consists
of the sample input, then a line of minus signs, then the expected output.  Line
endings are converted to \n.

To allow the sample input or expected output to contain control characters or
unicode characters as plain text, \nnn represents a character by its decimal
code, or by its hex code if the code starts with zero, \\ represents a single
backslash, and a \ followed by any other character removes the character.  In
particular, \ followed by a space can be used as a separator, and \ followed by
a newline can be used to cancel the newline.  For example, given that 960 is
the decimal code for the character pi, then:

   \960x      is pi followed by x
   \960\ 5    is pi followed by the digit 5
   \\960x     is the five characters \960x
   ...\13\    is a line ending in CR instead of LF
*/

public class Test {
    // Run a test on an object, with input and output expressed as strings.
    static interface Callable { String test(String in) throws ParseException; }

    private String fileName;
    private int lineNo;
    private String input, output;

    String input() { return input; }
    String output() { return output; }

    // Called from the main method of a class for unit testing, passing the
    // command line arguments, and a sample object of that class.
    static void run(String[] args, Callable object) {
        int line = 0;
        if (args != null && args.length > 0) line = Integer.parseInt(args[0]);
        try { tests(object, line); }
        catch (Exception e) { throw new Error(e); }
    }

    // Run the tests for a class. If line > 0, just run the test on that line.
    private static void tests(Callable object, int line) throws Exception {
        String name = object.getClass().getName();
        name = name.substring(name.lastIndexOf('.') + 1);
        List<Test> tests = extract("tests", name+".txt");
        for (int i=0; i<tests.size(); i++) {
            Test test = tests.get(i);
            if (line > 0 && test.lineNo != line) continue;
            String out;
            try { out = object.test(test.input); }
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
            if (message == null) continue;
            System.out.print(message);
            System.exit(1);
        }
        System.out.println(name + " class OK");
    }

    // Extract tests from a given file.
    static List<Test> extract(String dir, String fileName) {
        Path path = Paths.get(dir, fileName);
        List<String> lines;
        try { lines = Files.readAllLines(path, StandardCharsets.UTF_8); }
        catch (Exception e) { throw new Error(e); }
        return readTests(fileName, lines);
    }

    // Divide the lines from a file into test objects.
    private static List<Test> readTests(String file, List<String> lines) {
        List<Test> tests = new ArrayList<Test>();
        int start = 0;
        for (int i=0; i<=lines.size(); i++) {
            if (i < lines.size() && ! all(lines.get(i), '=')) continue;
            Test t = readTest(lines, start, i);
            t.fileName = file;
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
        int mid = -1;
        for (int i=start; i<end; i++) {
            if (! all(lines.get(i), '-')) continue;
            mid = i;
            break;
        }
        if (mid < 0) mid = end;
        Test test = new Test();
        test.lineNo = start + 1;
        test.input = "";
        for (int i=start; i<mid; i++) test.input += lines.get(i) + "\n";
        test.output = "";
        for (int i=mid+1; i<end; i++) test.output += lines.get(i) + "\n";
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
            out += (char) n;
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
/*
    // Utility function: Read the contents of a UTF-8 file as a string.
    static String read(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes, "UTF-8");
        }
        catch (Exception err) { throw new Error(err); }
    }

    // Utility function: Read the contents of a UTF-8 url resource as a string.
    static String read(URL url) {
        try {
            Path path = Paths.get(url.toURI());
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, "UTF-8");
        }
        catch (Exception err) { throw new Error(err); }
    }

    // Utility function: Read the contents of a UTF-8 file as lines.
    static List<String> readLines(File file) {
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        }
        catch (Exception err) { throw new Error(err); }
    }

    // Utility function: Read the contents of a UTF-8 url resource as lines.
    static List<String> readLines(URL url) {
        try {
            Path path = Paths.get(url.toURI());
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        }
        catch (Exception err) { throw new Error(err); }
    }

    // Read UTF-8 file from a stream.
    static String read(InputStream stream) {
        System.setProperty("file.encoding", "UTF-8");
        byte[] buffer = new byte[1];
        int len = 0;
        while(true) try {
            int n = stream.read(buffer, len, buffer.length - len);
            if (n < 0) { stream.close(); break; }
            len += n;
            if (len == buffer.length) {
                buffer = Arrays.copyOf(buffer, 2*buffer.length);
            }
        }
        catch (Exception err) { throw new Error(err); }
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, len);
        Charset utf8 = Charset.forName("UTF-8");
        CharBuffer charBuffer = utf8.decode(byteBuffer);
        char[] text = new char[charBuffer.length()];
        charBuffer.get(text);
        return new String(text);
    }

    // Split the text at marker lines into an array of tests.  Each test has
    // four strings: class name, note, input, output.
    private static String[][] split(String text, boolean user) {
        String[] parts = text.split("==========\n");
        String[][] tests = new String[parts.length][4];
        for (int i=0; i<parts.length; i++) {
            String part = parts[i];
            String[] sections = part.split("----------\n", 2);
            tests[i][3] = (sections.length == 1) ? "" : sections[1];
            if (user) {
                tests[i][2] = sections[0];
                tests[i][1] = "";
                tests[i][0] = "";
            }
            else {
                sections = sections[0].split("\n", 2);
                tests[i][2] = sections[1];
                sections = sections[0].split(" ", 2);
                tests[i][1] = sections[1];
                tests[i][0] = sections[0];
            }
        }
        return tests;
    }
*/

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
