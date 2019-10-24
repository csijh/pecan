// Pecan 1.0 main program. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;

/* Read in a file of tests and run them:

    pecan [-t | -trace] [line] testfile
    pecan grammar [-b | -c] output
*/

class Run {
    private boolean tracing, compiling, bytecode;
    private String infile, outfile, sourcefile;
    private int line = 0;
    private Evaluator evaluator;

    public static void main(String[] args) {
        Run program = new Run();
        program.run(args);
    }

    private void run(String[] args) {
        if (args == null) usage();
        tracing = compiling = bytecode = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-b")) compiling = true;
            if (args[i].equals("-c")) compiling = true;
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

    // pecan grammar [-b | -c] output
    private void runCompile(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-b") || args[i].equals("-c")) {
                if (args[i].equals("-b")) bytecode = true;
                outfile = args[i+1];
                i++;
            }
            else if (args[i].startsWith("-")) usage();
            else if (sourcefile == null) sourcefile = args[i];
            else usage();
        }
        if (sourcefile == null) usage();
        Source grammar = new Source(new File(sourcefile));
        fill(grammar, outfile);
    }

    // Read template program, extract parameters, compile grammar, write program
    // back out with compiled grammar inserted.
    private void fill(Source grammar, String outfile) {
        List<String> lines = null;
        Path p = Paths.get(outfile);
        PrintStream out = null;
        try {
            lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            out = new PrintStream(new File(outfile));
        }
        catch (Exception e) { throw new Error(e); }
        boolean skipping = false, reading = false;
        Pretty pretty = new Pretty();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (skipping && line.indexOf("</pecan>") >= 0) skipping = false;
            if (! skipping) out.println(line);
            if (reading && line.indexOf('=') < 0) {
                if (line.indexOf('>') < 0) err(i+1, outfile, "expecting >");
                reading = false;
                skipping = true;
                pretty.fillDefaults(i+1, outfile);
                Compiler compiler = new Compiler();
                compiler.formats(pretty);
                String functions = new Compiler().run(grammar);
                out.println();
                out.println(functions);
            }
            if (reading) pretty.readLine(i+1, outfile, line);
            if (! reading && ! skipping && line.indexOf("<pecan") >= 0) {
                if (line.indexOf("<pecan>") >= 0) {
                    if (! bytecode) err(i+1, outfile, "expecting attributes");
                    skipping = true;
                    // PRINT BYTES
                }
                else {
                    if (bytecode) err(i+1, outfile, "expecting <pecan>");
                    reading = true;
                }
            }
        }
        if (skipping) err(lines.size(), outfile, "expecting <pecan>");
        out.close();
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
        boolean skipping = false;
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

    private void err(int n, String f, String s) {
        System.err.println("Error on line " + n + " of " + f + ": " + s);
        System.exit(1);
    }
}
