// Pecan 5 interpreter. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Info.Flag.*;

/* This interpreter works directly from the tree nodes, and can be used for
testing, and for tracing. It also effectively defines the operational semantics
of the grammar language.

The test input may be text, or tag names with possible white space in between.
The output describes the external calls that would be made, with one line of
text per call.

TODO: trace option:
    I3 print input with | position when it progresses
    G3 print the text of a node (on one line) when parsing it
    O3 print the output when generated
    with x y z or x / y / z, go straight to x

TODO: work out which actions need to be delayed, or max no.
    contained in an FN node where, the action can be before any progress
    x y:    x IS @   or   x is a closer node   or   xSN and y is a closer node
    x / y:  x is closer   or   y is closer

*/

public class Interpreter implements Test.Callable {
    private String grammar;
    private boolean textInput, ok, marked;
    private String input;
    private Node root;
    private int start, in, mark, lookahead;
    private BitSet failures;
    private String[] markers;
    private List<Node> delay;
    private StringBuffer output;

    public static void main(String[] args) throws ParseException {
        Interpreter program = new Interpreter();
        Test.run(args, program);
    }
    static int no = 0;

    // Each test consists of a grammar followed by a line of ten tildes followed
    // by some source text. If the grammar is missing, the grammar from
    // the previous test is used.
    public String test(String s) throws ParseException {
        String[] parts = s.split("~~~~~~~~~~\n");
        String input;
        if (parts.length == 1) input = parts[0];
        else { grammar = parts[0]; input = parts[1]; }
        prepare(grammar, input);
        no++;
        String out = run();
        return out;
    }

    // Get the interpreters ready to run, with the given grammar and text.
    void prepare(String grammar, String text) throws ParseException {
        Stacker stacker = new Stacker();
        root = stacker.run(grammar);
        textInput = root.has(TextInput);
        input = text;
        ok = true;
        start = 0;
        in = 0;
        mark = 0;
        lookahead = 0;
        marked = false;
        markers = new String[markerSize(root)];
        gatherMarkers(root);
        failures = new BitSet();
        delay = new ArrayList<>();
        output = new StringBuffer();
    }

    // Run the parser
    String run() throws ParseException {
        parse(root.left());
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
            output.append(Node.err(input, mark, mark, s));
        }
        return output.toString();
    }

    // Parse according to the given node.
    void parse(Node node) {
        int saveIn, saveMark, length;
        String text;
        System.out.println(node.trace());
        switch(node.op()) {
        case RULE:
            parse(node.left());
            break;
        case ID:
            parse(node.ref());
            break;
        case OR:
            saveIn = in;
            parse(node.left());
            if (ok || in > saveIn) return;
            parse(node.right());
            break;
        case AND:
            parse(node.left());
            if (!ok) return;
            parse(node.right());
            break;
        case OPT:
            saveIn = in;
            parse(node.left());
            if (!ok && in == saveIn) ok = true;
            break;
        case MANY:
            saveIn = in;
            while (ok) {
                saveIn = in;
                parse(node.left());
            }
            if (in == saveIn) ok = true;
            break;
        case SOME:
            saveIn = in;
            parse(node.left());
            if (!ok) return;
            saveIn = in;
            while (ok) {
                saveIn = in;
                parse(node.left());
            }
            if (in == saveIn) ok = true;
            break;
        case TRY:
            saveIn = in;
            lookahead++;
            parse(node.left());
            lookahead--;
            in = saveIn;
            if (ok) parse(node.left());
            break;
        case HAS:
            saveIn = in;
            lookahead++;
            parse(node.left());
            lookahead--;
            in = saveIn;
            break;
        case NOT:
            saveMark = mark;
            mark = Integer.MAX_VALUE;
            saveIn = in;
            lookahead++;
            parse(node.left());
            lookahead--;
            in = saveIn;
            ok = !ok;
            mark = saveMark;
            if (! ok && mark < in) mark = in;
            break;
        case CHAR:
            if (in >= input.length()) ok = false;
            else {
                int ch = input.codePointAt(in);
                ok = (ch == node.value());
                if (ok) {
                    takeActions();
                    int n = Character.charCount(ch);
                    in += n;
                    traceInput();
                }
            }
            break;
        case STRING:
            length = node.text().length() - 2;
            text = node.text().substring(1, length+1);
            ok = true;
            if (in + length > input.length()) ok = false;
            else for (int i=0; i<length; i++) {
                if (input.charAt(in+i) != text.charAt(i)) { ok = false; break; }
            }
            if (ok) { takeActions(); in += length; traceInput(); }
            if (! ok && mark < in) mark = in;
            break;
        case SET:
            length = node.text().length() - 2;
            text = node.text().substring(1, length+1);
            ok = false;
            if (in >= input.length()) { }
            else for (int i=0; i<length; i++) {
                if (input.charAt(in) != text.charAt(i)) continue;
                takeActions();
                if (Character.isHighSurrogate(text.charAt(i))) {
                    i++;
                    if (input.charAt(in+1) != text.charAt(i)) continue;
                    in++;
                }
                in++;
                traceInput();
                ok = true;
            }
            if (! ok && mark < in) mark = in;
            break;
        case RANGE:
            int low = node.left().value();
            int high = node.right().value();
            ok = false;
            if (in < input.length()) {
                int ch = input.codePointAt(in);
                ok = (ch >= low) && (ch <= high);
                if (ok) {
                    takeActions();
                    int n = Character.charCount(ch);
                    in += n;
                    traceInput();
                }
            }
            if (! ok && mark < in) mark = in;
            break;
        case CAT:
            ok = false;
            int cats = node.value();
            if (in < input.length()) {
                int ch = input.codePointAt(in);
                int bit = 1 << Character.getType(ch);
                ok = ((cats & bit) != 0);
                if (ok) {
                    takeActions();
                    int n = Character.charCount(ch);
                    in += n;
                    traceInput();
                }
            }
            if (! ok && mark < in) mark = in;
            break;
        case TAG:
            String query;
            if (node.text().charAt(0) == '%') query = node.text().substring(1);
            else query = node.text().substring(1, node.text().length()-1);
            if (query.length() == 0) ok = in == input.length();
            else ok = input.startsWith(query, in);
            if (ok) {
                start = in;
                in += query.length();
                while (in < input.length() &&
                    (input.charAt(in) == ' ' || input.charAt(in) == '\n')) in++;
            }
            if (! ok && mark < in) mark = in;
            break;
        case MARK:
            if (in > mark) { mark = in; failures.clear(); }
            if (in == mark) failures.set(node.value());
            ok = true;
            break;
        case DROP:
            ok = true;
            delay.add(node);
            /*
            start = in;
            */
            break;
        case ACT:
            ok = true;
            if (lookahead > 0) break;
            delay.add(node);
            /*
            int arity = node.ref().value();
            output.append(node.ref().text());
            if (textInput && in > start) {
                output.append(" " + input.substring(start,in));
            }
            output.append("\n");
            start = in;
            */
            break;
        default:
            throw new Error("Not implemented " + node.op());
        }
    }

    // Print out the input position. TODO: multiline.
    private void traceInput() {
        System.out.print(input.substring(0, in));
        System.out.print("|");
        System.out.println(input.substring(in));
    }

    // Carry out any delayed actions.
    private void takeActions() {
        for (Node node : delay) {
            System.out.println("O: " + node.text());
            takeAction(node);
        }
        delay.clear();
    }

    // Carry out an action.
    private void takeAction(Node node) {
        if (node.op() == DROP) { start = in; return; }
        if (node.op() != ACT) throw new Error("Expecting ACT");
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
        if (node.op() == MARK) m = Math.max(m, node.value() + 1);
        return m;
    }

    // Gather strings for markers.
    private void gatherMarkers(Node node) {
        if (node.left() != null) gatherMarkers(node.left());
        if (node.left() != null && node.right() != null) gatherMarkers(node.right());
        if (node.op() == MARK) {
            markers[node.value()] = node.text().substring(1);
        }
    }
}
