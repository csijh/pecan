// Pecan 5 main program. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;

/* Read in a file of tests and run them:

    java -jar pecan.jar [-trace] [line] testfile

*/

class Run implements Testable {
    public static void main(String[] args) {
        int line = 0;
        String filename = null;
        Interpreter interpreter = new Interpreter();
        if (args != null) for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-trace")) interpreter.trace(true);
            else if (Character.isDigit(args[i].charAt(0))) {
                line = Integer.parseInt(args[i]);
            }
            else if (filename == null) filename = args[i];
            else usage();
        }
        if (filename == null) usage();
        Test.run(filename, interpreter, line);
    }

    // Give a usage message and stop.
    private static void usage() {
        System.err.println(
            "Usage:\n" +
            "    pecan [-trace] [line] testsfile\n" +
            "Or:\n" +
            "    java -jar pecan.jar [-trace] [line] testsfile\n");
        System.exit(1);
    }

    // Run a test passed from the Test class.
    public String test(String grammar, String input) throws ParseException {
        Interpreter interpreter = new Interpreter();
        return interpreter.test(grammar, input);
    }

/*
    // Run tests from the command line file through the interpreter.
    void run(String grammarFile, String testsFile) throws IOException {
        int passed = 0;
        String message = null;
        String grammar = new String(Files.readAllBytes(Paths.get(grammarFile)), StandardCharsets.UTF_8);
        List<Test> tests = Test.extract(".", testsFile);
        System.out.println("#tests = " + tests.size());
        Interpreter interpreter = new Interpreter();
        for (Test test : tests) {
            interpreter.prepare(grammar, test.input);

        }

        for (Test t : tests) {
            message = t.run(interp);
            if (message != null) break;
            passed++;
        }
        if (message != null) System.err.print(message);
        else if (passed == 1) System.out.println("Pass 1 test.");
        else System.out.println("Pass " + passed + " tests.");

    }
*/
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
    // Do integration testing. The tests come from the Tests.txt file, as with
    // unit testing, but files are created and run in the TestFiles directory.

    private void itest(String[] args) throws IOException {
        if (args.length == 1) Interpreter.main(new String[] { });
        int n = (args.length == 1) ? 0 : Integer.parseInt(args[1]);
        String grammar = "pecan/TestFiles/grammar.txt";
        String tests = "pecan/TestFiles/tests.txt";
        String message = null;
        for (Test test : Test.extract("Run", n)) {
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
