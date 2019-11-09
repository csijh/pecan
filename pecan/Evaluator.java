// Pecan 1.0 evaluator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Provide symbolic execution of a grammar. This works directly from the tree
nodes, and can be used for testing, and for tracing. It also effectively defines
the operational semantics of the grammar language.

The input is text, or tag names representing tokens separated by white space.
The output describes the external calls generated, with one line per call. */

public class Evaluator implements Testable {
    private boolean switchTest;
    private boolean tracing = false, skipTrace = false;
    private Node grammar;
    private boolean charInput, ok;
    private Source source;
    private String input;
    private int start, in, out, marked, lookahead;
    private TreeSet<String> failures;
    private StringBuffer output;
    private int outCount;

    // Do unit testing on the Stacker class, then check the switch is complete,
    // then run the Evaluator unit tests.
    public static void main(String[] args) {
        if (args.length == 0) Stacker.main(args);
        Evaluator evaluator = new Evaluator();
        evaluator.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, null);
            evaluator.parse(node);
        }
        evaluator.switchTest = false;
        Test.run(evaluator, args);
    }

    // Set up a grammar from its source, or run it on the given source.
    public String run(Source source) {
        if (grammar == null) {
            System.err.println("Error: No grammar has been set up");
            System.exit(1);
        }
        prepare(source);
        return runParser();
    }

    // Set up a grammar for subsequent tests.
    public String grammar(Source source) {
        Stacker stacker = new Stacker();
        grammar = stacker.run(source);
        charInput = ! grammar.has(TI);
        if (grammar.op() == Error) return grammar.note();
        else return null;
     }

    public void tracing(boolean on) { tracing = on; }

    // Get the Evaluator ready to run, with the given input.
    private void prepare(Source s) {
        source = s;
        input = source.rawText();
        ok = true;
        start = in = out = marked = lookahead = 0;
        failures = new TreeSet<>();
        output = new StringBuffer();
        outCount = 0;
    }

    // Run the parser
    private String runParser() {
        if (tracing) traceInput();
        if (grammar.op() == Error) return grammar.note() + "\n";
        parse(grammar.left());
        if (in > marked) failures.clear();
        if (! ok) {
            output.setLength(0);
            String s = "";
            for (String mark : failures) {
                if (s.equals("")) s = "expecting ";
                else s += ", ";
                s += mark;
            }
            output.append(source.sub(in,in).error(s));
            output.append("\n");
        }
        return output.toString();
    }

    // Parse according to the given node.
    private void parse(Node node) {
        if (tracing && ! skipTrace) System.out.println(node.trace());
        skipTrace = false;
        switch(node.op()) {
            case Error: case Temp: case List: case Empty: break;
            case Rule: parseRule(node); break;
            case Id: parseId(node); break;
            case Or: parseOr(node); break;
            case And: parseAnd(node); break;
            case Opt: parseOpt(node); break;
            case Any: parseAny(node); break;
            case Some: parseSome(node); break;
            case See: parseSee(node); break;
            case Has: parseHas(node); break;
            case Not: parseNot(node); break;
            case Tag: parseTag(node); break;
            case Success: parseSuccess(node); break;
            case Fail: parseFail(node); break;
            case Eot: parseEot(node); break;
            case Char: parseChar(node); break;
            case Text: parseText(node); break;
            case Set: parseSet(node); break;
            case Range: parseRange(node); break;
            case Split: parseSplit(node); break;
            case Point: parsePoint(node); break;
            case Cat: parseCat(node); break;
            case Mark: parseMark(node); break;
            case Drop: parseDrop(node); break;
            case Act: parseAct(node); break;
            default: assert false : "Unexpected node type " + node.op(); break;
        }
    }

    // Parse according to a rule node: parse the right hand side.
    private void parseRule(Node node) {
        if (switchTest) return;
        parse(node.right());
    }

    // Parse the rule refered to by an id (without tracing).
    private void parseId(Node node) {
        if (switchTest) return;
        skipTrace = true;
        parse(node.ref());
    }

    // Parse x / y. Parse x, and if it fails without progress, parse y instead.
    private void parseOr(Node node) {
        if (switchTest) return;
        int saveIn = in;
        parse(node.left());
        if (ok || in > saveIn) return;
        parse(node.right());
    }

    // Parse x y. If x succeeds, continue with y.
    private void parseAnd(Node node) {
        if (switchTest) return;
        parse(node.left());
        if (! ok) return;
        parse(node.right());
    }

    // Parse x?. If x fails but doesn't progress, return success.
    private void parseOpt(Node node) {
        if (switchTest) return;
        int saveIn = in;
        parse(node.left());
        if (! ok && in == saveIn) ok = true;
    }

    // Parse x*. Keep parsing x until it fails, then check progress.
    private void parseAny(Node node) {
        if (switchTest) return;
        int saveIn = in;
        ok = true;
        while (ok) {
            saveIn = in;
            parse(node.left());
        }
        if (! ok && in == saveIn) ok = true;
    }

    // Parse x+  =  x x*
    private void parseSome(Node node) {
        if (switchTest) return;
        parse(node.left());
        if (! ok) return;
        int saveIn = in;
        while (ok) {
            saveIn = in;
            parse(node.left());
        }
        if (! ok && in == saveIn) ok = true;
    }

    // Parse [x]. If x contains an action or a marker, parse twice as x& then x.
    // Otherwise parse once and decide whether to backtrack.
    private void parseSee(Node node) {
        if (switchTest) return;
        if (node.has(AA) || node.has(EE)) {
            parseHas(node);
            if (ok) parse(node.left());
        } else {
            int saveIn = in;
            parse(node.left());
            if (! ok) {
                boolean back = in != saveIn;
                in = saveIn;
                if (back && tracing) traceInput();
            }
        }
    }

    // Parse x&
    private void parseHas(Node node) {
        if (switchTest) return;
        int saveIn = in;
        lookahead++;
        parse(node.left());
        lookahead--;
        boolean back = in != saveIn;
        in = saveIn;
        if (back && tracing) traceInput();
    }

    // Parse x!
    private void parseNot(Node node) {
        if (switchTest) return;
        int saveIn = in;
        lookahead++;
        parse(node.left());
        lookahead--;
        boolean back = in != saveIn;
        in = saveIn;
        if (in != saveIn && tracing) traceInput();
        ok = ! ok;
    }

    // Parse %t
    private void parseTag(Node node) {
        if (switchTest) return;
        String tag = node.rawText();
        ok = input.startsWith(tag, in);
        if (ok) {
            start = in;
            in += tag.length();
            while (input.startsWith(" ", in) || input.startsWith("\n", in)) {
                in++;
            }
            if (tracing) traceInput();
        }
    }

    // Parse ""
    private void parseSuccess(Node node) {
        if (switchTest) return;
        ok = true;
    }

    // Parse ''
    private void parseFail(Node node) {
        if (switchTest) return;
        ok = false;
    }

    // Parse <>
    private void parseEot(Node node) {
        if (switchTest) return;
        ok = in == input.length();
    }

    private void parseChar(Node node) {
        if (switchTest) return;
        if (in >= input.length()) ok = false;
        else {
            int ch = input.codePointAt(in);
            ok = (ch == node.charCode());
            if (ok) in += Character.charCount(ch);
            if (tracing) traceInput();
        }
    }

    // Parse "abc"
    private void parseText(Node node) {
        if (switchTest) return;
        String text = node.rawText();
        int length = text.length();
        ok = true;
        if (in + length > input.length()) ok = false;
        else for (int i=0; i<length; i++) {
            if (input.charAt(in+i) != text.charAt(i)) { ok = false; break; }
        }
        if (ok) {
            in += length;
            if (tracing) traceInput();
        }
    }

    // Parse 'abc'
    private void parseSet(Node node) {
        if (switchTest) return;
        String text = node.rawText();
        int length = text.length();
        ok = false;
        if (in >= input.length()) { }
        else for (int i=0; i<length; i++) {
            if (input.charAt(in) != text.charAt(i)) continue;
            if (Character.isHighSurrogate(text.charAt(i))) {
                i++;
                if (input.charAt(in+1) != text.charAt(i)) continue;
                in++;
            }
            in++;
            if (tracing) traceInput();
            ok = true;
        }
    }

    // Parse 'a..z' or 0..127
    private void parseRange(Node node) {
        if (switchTest) return;
        int low = node.low();
        int high = node.high();
        ok = false;
        if (in < input.length()) {
            int ch = input.codePointAt(in);
            ok = (ch >= low) && (ch <= high);
            if (ok) {
                int n = Character.charCount(ch);
                in += n;
                if (tracing) traceInput();
            }
        }
    }

    // Parse <abc>
    private void parseSplit(Node node) {
        if (switchTest) return;
        String text = node.rawText();
        String rest = input.substring(in);
        ok = rest.compareTo(text) <= 0;
    }

    // Parse .
    private void parsePoint(Node node) {
        if (switchTest) return;
        if (in < input.length()) {
            ok = true;
            int ch = input.codePointAt(in);
            in += Character.charCount(ch);
            if (tracing) traceInput();
        }
        else ok = false;
    }

    // Parse Nd
    private void parseCat(Node node) {
        if (switchTest) return;
        ok = false;
        Category cat = Category.valueOf(node.rawText());
        if (in < input.length()) {
            int ch = input.codePointAt(in);
            Category c = Category.get(ch);
            ok = c == cat;
            if (ok) {
                in += Character.charCount(ch);
                if (tracing) traceInput();
            }
        }
    }

    // Parse #m
    private void parseMark(Node node) {
        if (switchTest) return;
        ok = true;
        if (lookahead > 0) return;
        if (marked != in) { marked = in; failures.clear(); }
        failures.add(node.text().substring(1));
    }

    // Parse @
    private void parseDrop(Node node) {
        if (switchTest) return;
        ok = true;
        int a = node.arity();
        if (lookahead > 0) return;
        if (a > 0 && tracing) {
            System.out.println("O" + (++outCount) + ": DROP " + a);
        }
        start = in;
    }

    // Parse @2add
    private void parseAct(Node node) {
        if (switchTest) return;
        ok = true;
        if (lookahead > 0) return;
        String s = node.rawText();
        if (charInput && in > start) s += " " + input.substring(start, in);
        s = escape(s);
        s += "\n";
        if (tracing) System.out.print("O" + (++outCount) + ": " + s);
        output.append(s);
        start = in;
    }

    // Convert control characters and Unicode to escapes.
    private String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int ch = s.codePointAt(i);
            i += Character.charCount(ch);
            if (' ' <= ch && ch <= '~') sb.append((char)ch);
            else {
                sb.append("\\");
                sb.append(ch);
                if (i < s.length()) {
                    ch = s.charAt(i);
                    if (ch == ';' || ('0' <= ch && ch <= '9')) sb.append(';');
                }
            }
        }
        return sb.toString();
    }

    // Print out the input position.
    private void traceInput() {
        int line = 1, start = 0, stop = input.length();
        for (int i = 0; i < in; i++) {
            if (input.charAt(i) != '\n') continue;
            line++;
            start = i + 1;
        }
        for (int i = in; i < input.length(); i++) {
            if (input.charAt(i) != '\n') continue;
            stop = i;
            break;
        }
        System.out.print("I" + line + ": ");
        System.out.print(input.substring(start, in));
        System.out.print("|");
        System.out.println(input.substring(in, stop));
    }
}
