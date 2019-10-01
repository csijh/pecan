// Pecan 1.0 main program. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;

/* Read in a file of tests and run them:

    pecan [-t | -trace] [line] testfile
    pecan -o output grammar
*/

class Run {
    private boolean tracing, compiling;
    private String infile, outfile, sourcefile;
    private int line = 0;
    private Evaluator evaluator;

    public static void main(String[] args) {
        Run program = new Run();
        program.run(args);
    }

    private void run(String[] args) {
        if (args == null) usage();
        tracing = compiling = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o")) compiling = true;
        }
        if (compiling) runCompile(args);
        else runTest(args);
    }

    // pecan [-t | -trace] [line] testfile
    private void runTest(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-trace")) tracing = true;
            else if (args[i].equals("-t")) tracing = true;
            else if (args[i].startsWith("-")) usage();
            else if (Character.isDigit(args[i].charAt(0))) {
                line = Integer.parseInt(args[i]);
            }
            else if (sourcefile == null) sourcefile = args[i];
            else usage();
        }
        if (sourcefile == null) usage();
        Evaluator e = new Evaluator();
        Test.run(e, args);
    }

    // pecan -o output grammar
    private void runCompile(String[] args) {
        /*
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o")) {
                outfile = args[i+1];
                i++;
            }
            else if (args[i].startsWith("-")) usage();
            else if (sourcefile == null) sourcefile = args[i];
            else usage();
        }
        if (sourcefile == null) usage();
        String grammar = null;
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(sourcefile));
            grammar = new String(bytes, "UTF-8");
        }
        catch (Exception e) {
            System.err.println("Error: can't read " + sourcefile + ": " + e);
            System.exit(1);
        }
        int n = grammar.indexOf("===");
        if (n >= 0) grammar = grammar.substring(0, n);
        Generator gen = new Generator();
        String code = gen.run(grammar);
        if (code.startsWith("Error: ")) {
            System.err.println(code.substring(7));
            System.exit(1);
        }
        insert(code);
        */
    }

    // Insert the code into the output file.
    private void insert(String code) {
        List<String> lines = null;
        Path p = Paths.get(outfile);
        PrintStream out = null;
        try {
            lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            out = new PrintStream(new File(outfile));
        }
        catch (Exception e) { throw new Error(e); }
        boolean skipping = false, done = false;
        for (String line : lines) {
            if (line.indexOf("</pecan>") >= 0) skipping = false;
            if (! skipping) out.println(line);
            if (line.indexOf("<pecan>") >= 0) {
                skipping = true;
                out.print(code);
            }
        }
        out.close();
    }

    // Give a usage message and stop.
    private static void usage() {
        System.err.println(
            "Usage:\n" +
            "    pecan [-t | -trace] [line] tests\n" +
            "Or:\n" +
            "    pecan grammar -o output\n");
        System.exit(1);
    }
}
