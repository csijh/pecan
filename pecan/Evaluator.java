// Pecan 1.0 evaluator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* This evaluator provides symbolic execution of a grammar. It works directly
from the tree nodes, and can be used for testing, and for tracing. It also
effectively defines the operational semantics of the grammar language.

The input may be text, or tag names with possible white space in between. The
output describes the external calls that would be made, with one line per call.

Actions are normally delayed until the next time progress is made, or discarded
if parsing fails without progressing. During lookahead, actions are delayed
until the end of the lookahead, or discarded on failure or backtrack. Error
markers are gathered as a bit set, with a mark variable to record the position
in the input at which they were encountered. When a new marker is encountered,
and the input has progressed beyond the mark position, the old markers are
discarded and the mark updated. Old markers are also discarded when an error is
reported, if the input position has progressed beyond the mark. Markers are
ignored during lookahead.

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
    private int start, in, out, mark, lookahead;
    private BitSet failures;
    private String[] markers;
    private Node[] delay;
    private StringBuffer output;

    public static void main(String[] args) {
        int line = 0;
        Evaluator program = new Evaluator();
        if (args != null) for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-trace")) program.tracing = true;
            else line = Integer.parseInt(args[i]);
        }
        if (args.length == 0) Stacker.main(args);
        Test.run(program, line);
    }

    public String test(String grammar, String input) throws ParseException {
        prepare(grammar, input);
        return run();
    }

    // Set the tracing flag.
    void trace(boolean b) {
        tracing = b;
    }

    // Get the Evaluator ready to run, with the given grammar and text.
    void prepare(String grammar, String text) throws ParseException {
        Stacker stacker = new Stacker();
        root = stacker.run(grammar);
        textInput = root.has(TextInput);
        input = text;
        ok = true;
        start = 0;
        in = 0;
        out = 0;
        mark = 0;
        lookahead = 0;
        markers = new String[markerSize(root)];
        gatherMarkers(root);
        failures = new BitSet();
        delay = new Node[100];
        output = new StringBuffer();
    }

    // Run the parser
    String run() throws ParseException {
        if (tracing) traceInput();
        parse(root.left());
        if (in > mark) failures.clear();
        takeActions();
        if (! ok) {
            output.setLength(0);
            String s;
            if (failures.isEmpty()) s = "";
            else s = "expecting ";
            int i = -1;
            while ((i = failures.nextSetBit(i+1)) >= 0) {
                if (! s.equals("expecting ")) s += ", ";
                s += markers[i];
            }
            output.append(Node.err(input, in, in, s));
        }
        return output.toString();
    }

    // Parse according to the given node.
    void parse(Node node) {
        int saveIn, saveOut, length;
        String text;
        if (tracing && ! skipTrace) System.out.println(node.trace());
        skipTrace = false;
        switch(node.op()) {
        case Rule:
            parse(node.left());
            break;
        case Id:
            skipTrace = true;
            parse(node.ref());
            break;
        case Or:
            saveIn = in;
            saveOut = out;
            parse(node.left());
            if (ok || in > saveIn) return;
            out = saveOut;
            parse(node.right());
            break;
        case And:
            parse(node.left());
            if (!ok) return;
            parse(node.right());
            break;
        case Opt:
            saveIn = in;
            saveOut = out;
            parse(node.left());
            if (!ok && in == saveIn) {
                out = saveOut;
                ok = true;
            }
            break;
        case Many:
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
            break;
        case Some:
            saveIn = in;
            saveOut = out;
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
            break;
        case Try:
            saveIn = in;
            saveOut = out;
            lookahead++;
            parse(node.left());
            lookahead--;
            in = saveIn;
            if (ok && lookahead == 0) takeActions();
            if (ok) parse(node.left());
            else out = saveOut;
            break;
        case Has:
            saveIn = in;
            saveOut = out;
            lookahead++;
            parse(node.left());
            lookahead--;
            out = saveOut;
            in = saveIn;
            break;
        case Not:
            saveIn = in;
            saveOut = out;
            lookahead++;
            parse(node.left());
            lookahead--;
            out = saveOut;
            in = saveIn;
            ok = !ok;
            break;
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
            int cats = node.value();
            if (in < input.length()) {
                int ch = input.codePointAt(in);
                int bit = 1 << Character.getType(ch);
                ok = ((cats & bit) != 0);
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
            if (mark != in) { mark = in; failures.clear(); }
            failures.set(node.value());
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
        output.append(node.ref().text());
        if (textInput && in > start) {
            output.append(" " + input.substring(start,in));
        }
        output.append("\n");
        start = in;
    }

    // Measure the number of markers.
    private int markerSize(Node node) {
        int m = 0, n = 0;
        if (node.left() != null) m = markerSize(node.left());
        if (node.left() != null && node.right() != null) n = markerSize(node.right());
        m = Math.max(m, n);
        if (node.op() == Mark) m = Math.max(m, node.value() + 1);
        return m;
    }

    // Gather strings for markers.
    private void gatherMarkers(Node node) {
        if (node.left() != null) gatherMarkers(node.left());
        if (node.left() != null && node.right() != null) gatherMarkers(node.right());
        if (node.op() == Mark) {
            markers[node.value()] = node.text().substring(1);
        }
    }
}
