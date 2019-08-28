// Pecan 1.0 evaluator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* This evaluator provides symbolic execution of a grammar. It works directly
from the tree nodes, and can be used for testing, and for tracing. It also
effectively defines the operational semantics of the grammar language.

The input is text, or tag names representing tokens, separated by white space.
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
    private boolean tracing = false, skipTrace = false;
    private String grammar;
    private boolean textInput, ok;
    private String input;
    private Node root;
    private int start, in, out, marked, lookahead;
    private TreeSet<String> failures;
    private Node[] delay;
    private StringBuffer output;

    public static void main(String[] args) {
        int line = 0;
        Evaluator program = new Evaluator();
        if (args != null) for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-trace")) program.tracing = true;
            else if (args[i].equals("-t")) program.tracing = true;
            else line = Integer.parseInt(args[i]);
        }
        if (args.length == 0) Stacker.main(args);
        Test.run(program, line);
    }

    // Carry out a test, or set up a grammar for subsequent tests
    public String test(String input) {
        if (input.startsWith("GRAMMAR:\n")) {
            grammar = input.substring(9);
            Stacker stacker = new Stacker();
            root = stacker.run(grammar);
            textInput = root.has(TextInput);
            if (root.op() == Error) return root.note();
            else return null;
        }
        else {
            prepare(input);
            return run();
        }
    }

    // Get the Evaluator ready to run, with the given input.
    private void prepare(String text) {
        input = text;
        ok = true;
        start = 0;
        in = 0;
        out = 0;
        marked = 0;
        lookahead = 0;
        failures = new TreeSet<>();
        delay = new Node[100];
        output = new StringBuffer();
    }

    // Run the parser
    private String run() {
        if (tracing) traceInput();
        if (root.op() == Error) return root.note() + "\n";
        parse(root.left());
        if (in > marked) failures.clear();
        takeActions();
        if (! ok) {
            output.setLength(0);
            String s = "";
            for (String mark : failures) {
                if (s.equals("")) s = "expecting ";
                else s += ", ";
                s += mark;
            }
            output.append(Node.err(input, in, in, s));
            output.append("\n");
        }
        return output.toString();
    }

    // Parse according to the given node.
    private void parse(Node node) {
        int saveIn, saveOut, length;
        String text;
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
        case Char:
            if (in >= input.length()) ok = false;
            else {
                int ch = input.codePointAt(in);
                ok = (ch == node.value());
                if (ok) {
                    if (lookahead == 0 && out > 0) takeActions();
                    int n = Character.charCount(ch);
                    in += n;
                    if (tracing) traceInput();
                }
            }
            break;
        case String:
            length = node.text().length() - 2;
            text = node.text().substring(1, length+1);
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
            break;
        case Set:
            length = node.text().length() - 2;
            text = node.text().substring(1, length+1);
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
            break;
        case Range:
            int low = node.left().value();
            int high = node.right().value();
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
            break;
        case Cat:
            ok = false;
            Category cat = Category.valueOf(node.name());
            if (in < input.length()) {
                int ch = input.codePointAt(in);
                Category c = Category.get(ch);
                ok = c == cat || cat == Category.Uc;
                if (ok) {
                    if (lookahead == 0 && out > 0) takeActions();
                    int n = Character.charCount(ch);
                    in += n;
                    if (tracing) traceInput();
                }
            }
            break;
        case Tag:
            String query;
            if (node.text().charAt(0) == '%') query = node.text().substring(1);
            else query = node.text().substring(1, node.text().length()-1);
            if (query.length() == 0) ok = in == input.length();
            else ok = input.startsWith(query, in);
            if (ok) {
                start = in;
                if (lookahead == 0 && out > 0) takeActions();
                in += query.length();
                while (in < input.length() &&
                    (input.charAt(in) == ' ' || input.charAt(in) == '\n')) in++;
                if (tracing) traceInput();
            }
            break;
        case Mark:
            ok = true;
            if (lookahead > 0) break;
            if (marked != in) { marked = in; failures.clear(); }
            failures.add(node.text().substring(1));
            break;
        case Drop:
            ok = true;
            delay[out++] = node;
            break;
        case Act:
            ok = true;
            if (lookahead > 0) break;
            delay[out++] = node;
            break;
        default:
            throw new Error("Not implemented " + node.op());
        }
    }

    // Parse according to a rule node: parse the right hand side.
    private void parseRule(Node node) {
        parse(node.left());
    }

    // Parse the rule refered to by an id (without tracing).
    private void parseId(Node node) {
        skipTrace = true;
        parse(node.ref());
    }

    // Parse x / y. Try x, and it fails without progress, try y instead.
    private void parseOr(Node node) {
        int saveIn = in;
        int saveOut = out;
        parse(node.left());
        if (ok || in > saveIn) return;
        out = saveOut;
        parse(node.right());
    }

    // Parse x y. If x succeeds, continue with y.
    private void parseAnd(Node node) {
        parse(node.left());
        if (!ok) return;
        parse(node.right());
    }

    // Parse x?
    private void parseOpt(Node node) {
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
        int saveIn = in;
        int saveOut = out;
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

    // TODO check if need to take out redo x
    // Parse [x]
    private void parseTry(Node node) {
        int saveIn = in;
        int saveOut = out;
        lookahead++;
        parse(node.left());
        lookahead--;
        in = saveIn;
        if (ok && lookahead == 0) takeActions();
        if (ok) parse(node.left());
        else out = saveOut;
        /*
        if (ok && lookahead == 0) takeActions();
        if (!ok) {
            in = saveIn;
            out = saveOut;
        }
        */
    }

    // Parse x&
    private void parseHas(Node node) {
        int saveIn = in;
        int saveOut = out;
        lookahead++;
        parse(node.left());
        lookahead--;
        out = saveOut;
        in = saveIn;
    }

    // Parse x!
    private void parseNot(Node node) {
        int saveIn = in;
        int saveOut = out;
        lookahead++;
        parse(node.left());
        lookahead--;
        out = saveOut;
        in = saveIn;
        ok = !ok;
    }

    private void parseChar(Node node) {
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
            String s = node.text();
            if (! s.equals("@")) s = s.substring(1);
            if (tracing) System.out.println("O: " + s);
            takeAction(node);
        }
        out = 0;
    }

    // Carry out an action.
    private void takeAction(Node node) {
        if (node.op() == Drop) { start = in; return; }
        if (node.op() != Act) throw new Error("Expecting Act");
        output.append(node.name());
        if (textInput && in > start) {
            output.append(" " + input.substring(start,in));
        }
        output.append("\n");
        start = in;
    }
}
