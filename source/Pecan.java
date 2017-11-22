// Part of Pecan 4. Open source - see licence.txt.

package pecan;

//import static pecan.Op.*;
//import java.text.*;
import java.util.*;
//import java.util.List;
//import java.io.*;
//import java.nio.*;
//import java.nio.charset.*;
//import javax.swing.*;
//import javax.swing.text.*;
//import java.awt.*;
//import java.awt.event.*;

/* Runs the pecan parser generator. */

class Pecan {
    public static void main(String[] args) throws Exception {
        Pecan program = new Pecan();
//        if (args.length > 0 && args[0].equals("-it")) program.itest(args);
        if (args.length < 2) usage();
        if (args[0].equals("-test")) program.test(args[1]);
//        if (args[0].equals("-t")) program.test(args[1], args[2]);
//        if (args[0].equals("-java")) program.gen("Java,"+args[1], args[2]);
//        if (args[0].equals("-j")) program.gen("Java,"+args[1], args[2]);
    }

    // Give a usage message and stop.
    private static void usage() {
        System.err.println("Usage:");
        System.err.println("pecan -test <file>");
//        System.err.println("pecan -java <names> <pecanfile>");
//        System.err.println("pecan -it");
        System.exit(1);
    }

    // Run tests from the command line file through the interpreter.
    void test(String filename) {
        int passed = 0;
        String message = null;
        List<Test> tests = Test.extract(filename);
        Interpreter interp = new Interpreter();
        for (Test t : tests) {
            message = t.run(interp);
            if (message != null) break;
            passed++;
        }
        if (message != null) System.err.print(message);
        else if (passed == 1) System.out.println("Pass 1 test.");
        else System.out.println("Pass " + passed + " tests.");
    }

    // Generate code
/*
    private void gen(String list, String grammar) {
        String[] names = list.trim().split(" *, *");
        File grammarFile = new File(grammar);
        File dir = grammarFile.getParentFile();
        File output = new File(dir, names[1] + ".java");
        Generator generator = new Generator();
        PrintWriter pw;
        try { pw = new PrintWriter(output); }
        catch (Exception e) { throw new Error(e); }
        generator.run(grammarFile, pw, names);
        pw.close();
    }
*/
/*
    // Do integration testing.  The tests come from the Tests.txt file, as with
    // unit testing, but files are created and run in the TestFiles directory.

    private void itest(String[] args) throws IOException {
        if (args.length == 1) Interpreter.main(new String[] { });
        int n = (args.length == 1) ? 0 : Integer.parseInt(args[1]);
        String grammar = "pecan/TestFiles/grammar.txt";
        String tests = "pecan/TestFiles/tests.txt";
        String message = null;
        for (Test test : Test.extract("Pecan", n)) {
            String in = test.input();
            String[] parts = in.split("~~~~~~~~~~\n", 2);
            PrintWriter out = new PrintWriter(grammar);
            out.print(parts[0]);
            out.close();
            out = new PrintWriter(tests);
            parts[1] = parts[1].replace("~~~~~~~~~~\n", "==========\n");
            parts[1] = parts[1].replace("~~~~~\n", "----------\n");
            out.print(parts[1]);
            out.close();
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            OutputStream output = new ByteArrayOutputStream();
            PrintStream print = new PrintStream(output);
            System.setOut(print);
            System.setErr(print);
            try { main(new String[] {"-t", tests, grammar}); }
            catch (Throwable t) {
                System.setOut(oldOut);
                System.setErr(oldErr);
                throw new Error(t);
            }
            print.flush();
            print.close();
            String result = output.toString();
            System.setOut(oldOut);
            System.setErr(oldErr);
            message = test.check(result);
            if (message != null) break;
        }
        if (message != null) System.err.print(message);
    }
*/
}
