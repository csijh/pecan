// Pecan 1.0 evaluator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* This evaluator provides symbolic execution of a grammar. It works directly
from the tree nodes, and can be used for testing, and for tracing. It also
effectively defines the operational semantics of the grammar language.

The input is text, or tag names representing tokens separated by white space.
The output describes the external calls generated, with one line per call.

Actions are normally delayed until the next time progress is made, or discarded
if parsing fails without progressing. During lookahead, actions are delayed
until the end of the lookahead, or discarded on failure or backtrack. Error
markers are gathered as a set, with a marked variable to record the position in
the input at which they were encountered. When a new marker is encountered, and
the input has progressed beyond the marked position, the old markers are
discarded and the marked position updated. Markers are ignored during lookahead.

TODO: work out which actions need to be delayed (or max no).
Look for x / y or x? or x* or x+ where x has a problem action.
A problem action is x y where x has a problem action or
x y where x is an action (or SN and action) and y is FN


    contained in an FN node where, the action can be before any progress
    x y:    x IS @   or   x is a closer node   or   xSN and y is a closer node
    x / y:  x is closer   or   y is closer

*/

public class Evaluator implements Testable {
    private boolean switchTest;
    private boolean tracing = false, skipTrace = false;
    private Node grammar;
    private boolean charInput, ok;
    private Source source;
    private String input;
    private String[] tokens;
    private int start, in, out, marked, lookahead;
    private TreeSet<String> failures;
    private Node[] delay;
    private int[] delayIn;
    private StringBuffer output;

    // Do unit testing on the Stacker class, then check the switch is complete,
    // then run the Evaluator unit tests.
    public static void main(String[] args) {
        if (args.length == 0) Stacker.main(args);
        Evaluator evaluator = new Evaluator();
        evaluator.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, 0, 1);
            evaluator.parse(node);
        }
        evaluator.switchTest = false;
        Test.run(evaluator, args);
    }

    // Set up a grammar from its source, or run it on the given source.
    public String run(Source source) {
        if (source.grammar()) return setup(source);
        else {
            prepare(source);
            return runParser();
        }
    }

    // Set up a grammar for subsequent tests
    private String setup(Source source) {
        Stacker stacker = new Stacker();
        grammar = stacker.run(source);
        charInput = grammar.has(CI);
        if (grammar.op() == Error) return grammar.note();
        else return null;
    }

    // Get the Evaluator ready to run, with the given input.
    private void prepare(Source s) {
        source = s;
        if (grammar.has(TI)) tokens = source.text().split("\\s+");
        else input = source.text();
        ok = true;
        start = in = out = marked = lookahead = 0;
        failures = new TreeSet<>();
        delay = new Node[100];
        delayIn = new int[100];
        output = new StringBuffer();
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
            if (charInput) output.append(source.error(in, in, s));
            else {
                output.append("Error at token " + in);
                if (s.length() > 0) output.append(": " + s);
            }
            output.append("\n");
        }
        return output.toString();
    }

    // Parse according to the given node.
    private void parse(Node node) {
        if (tracing && ! skipTrace) System.out.println(node.trace());
        skipTrace = false;
        switch(node.op()) {
            case Rule: parseRule(node); break;
            case Id: parseId(node); break;
            case Or: parseOr(node); break;
            case And: parseAnd(node); break;
            case Opt: parseOpt(node); break;
            case Any: parseAny(node); break;
            case Some: parseSome(node); break;
            case Try: parseTry(node); break;
            case Has: parseHas(node); break;
            case Not: parseNot(node); break;
            case Code: parseCode(node); break;
            case String: parseString(node); break;
            case Set: parseSet(node); break;
            case Range: parseRange(node); break;
            case Cat: parseCat(node); break;
            case Tag: parseTag(node); break;
            case Mark: parseMark(node); break;
            case Drop: parseDrop(node); break;
            case Act: parseAct(node); break;
            case End: parseEnd(node); break;
            default: throw new Error("Not implemented " + node.op());
        }
    }

    // Parse according to a rule node: parse the right hand side.
    private void parseRule(Node node) {
        if (switchTest) return;
        parse(node.left());
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
        int saveOut = out;
        parse(node.left());
        if (ok || in > saveIn) return;
        out = saveOut;
        parse(node.right());
    }

    // Parse x y. If x succeeds, continue with y.
    private void parseAnd(Node node) {
        if (switchTest) return;
        parse(node.left());
        if (!ok) return;
        parse(node.right());
    }

    // Parse x?
    private void parseOpt(Node node) {
        if (switchTest) return;
        int saveIn = in;
        int saveOut = out;
        parse(node.left());
        if (!ok && in == saveIn) {
            out = saveOut;
            ok = true;
        }
    }

    // Parse x*
    private void parseAny(Node node) {
        if (switchTest) return;
        int saveIn = in;
        int saveOut = out;
        ok = true;
        while (ok) {
            saveIn = in;
            saveOut = out;
            parse(node.left());
        }
        if (!ok && in == saveIn) {
            out = saveOut;
            ok = true;
        }
    }

    // Parse x+  =  x x*
    private void parseSome(Node node) {
        if (switchTest) return;
        int saveIn = in;
        int saveOut = out;
        parse(node.left());
        if (!ok && in == saveIn) out = saveOut;
        if (!ok) return;
        saveIn = in;
        saveOut = out;
        while (ok) {
            saveIn = in;
            saveOut = out;
            parse(node.left());
        }
        if (!ok && in == saveIn) {
            out = saveOut;
            ok = true;
        }
    }

    // Parse [x]
    private void parseTry(Node node) {
        if (switchTest) return;
        int saveIn = in;
        int saveOut = out;
        lookahead++;
        parse(node.left());
        lookahead--;
        if (ok && lookahead == 0) takeActions();
        if (!ok) {
            if (in != saveIn && tracing) traceInput();
            in = saveIn;
            out = saveOut;
        }
    }

    // Parse x&
    private void parseHas(Node node) {
        if (switchTest) return;
        int saveIn = in;
        int saveOut = out;
        lookahead++;
        parse(node.left());
        lookahead--;
        out = saveOut;
        if (in != saveIn && tracing) traceInput();
        in = saveIn;
    }

    // Parse x!
    private void parseNot(Node node) {
        if (switchTest) return;
        int saveIn = in;
        int saveOut = out;
        lookahead++;
        parse(node.left());
        lookahead--;
        out = saveOut;
        if (in != saveIn && tracing) traceInput();
        in = saveIn;
        ok = !ok;
    }

    // Parse 127
    private void parseCode(Node node) {
        if (switchTest) return;
        if (in >= input.length()) ok = false;
        else {
            int ch = input.codePointAt(in);
            ok = (ch == node.charCode());
            if (ok) {
                if (lookahead == 0 && out > 0) takeActions();
                int n = Character.charCount(ch);
                in += n;
                if (tracing) traceInput();
            }
        }
    }

    // Parse "abc"
    private void parseString(Node node) {
        if (switchTest) return;
        int length = node.text().length() - 2;
        String text = node.text().substring(1, length+1);
        ok = true;
        if (in + length > input.length()) ok = false;
        else for (int i=0; i<length; i++) {
            if (input.charAt(in+i) != text.charAt(i)) { ok = false; break; }
        }
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            in += length;
            if (tracing) traceInput();
        }
    }

    // Parse 'abc'
    private void parseSet(Node node) {
        if (switchTest) return;
        int length = node.text().length() - 2;
        String text = node.text().substring(1, length+1);
        ok = false;
        if (in >= input.length()) { }
        else for (int i=0; i<length; i++) {
            if (input.charAt(in) != text.charAt(i)) continue;
            if (lookahead == 0 && out > 0) takeActions();
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
        int low = node.left().charCode();
        int high = node.right().charCode();
        ok = false;
        if (in < input.length()) {
            int ch = input.codePointAt(in);
            ok = (ch >= low) && (ch <= high);
            if (ok) {
                if (lookahead == 0 && out > 0) takeActions();
                int n = Character.charCount(ch);
                in += n;
                if (tracing) traceInput();
            }
        }
    }

    // Parse Nd
    private void parseCat(Node node) {
        if (switchTest) return;
        ok = false;
        Category cat = Category.valueOf(node.name());
        if (in < input.length()) {
            int ch = input.codePointAt(in);
            Category c = Category.get(ch);
            ok = c == cat || cat == Category.Uc;
            if (ok) {
                if (lookahead == 0 && out > 0) takeActions();
                in += Character.charCount(ch);
                if (tracing) traceInput();
            }
        }
    }

    // Parse %t
    private void parseTag(Node node) {
        if (switchTest) return;
        String tag;
        tag = node.text().substring(1);
        if (in < tokens.length) ok = tokens[in].equals(tag);
        else ok = false;
        if (ok) {
            start = in;
            if (lookahead == 0 && out > 0) takeActions();
            in++;
            if (tracing) traceInput();
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
        delay[out] = node;
        delayIn[out++] = in;
    }

    // Parse @2add
    private void parseAct(Node node) {
        if (switchTest) return;
        ok = true;
        delay[out] = node;
        delayIn[out++] = in;
    }

    // Parse <>
    private void parseEnd(Node node) {
        if (switchTest) return;
        ok = in == input.length();
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

    // Carry out any delayed actions.
    private void takeActions() {
        for (int i = 0; i < out; i++) {
            Node node = delay[i];
            int oldIn = delayIn[i];
            String s = node.text();
            if (! s.equals("@")) s = s.substring(1);
            if (tracing) System.out.println("O: " + s);
            takeAction(node, oldIn);
        }
        out = 0;
    }

    // Carry out an action.
    private void takeAction(Node node, int oldIn) {
        if (node.op() == Drop) { start = oldIn; return; }
        if (node.op() != Act) throw new Error("Expecting Act");
        output.append(node.name());
        if (charInput && oldIn > start) {
            output.append(" " + input.substring(start, oldIn));
        }
        output.append("\n");
        start = oldIn;
    }
}
