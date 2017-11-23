// Part of Pecan 4. Open source - see licence.txt.

package pecan;

import java.text.*;
import static pecan.Op.*;
import static pecan.Info.Flag.*;

/* Check that a grammar is valid.  Report any non-terminating left recursion as
an error, using an adaptation of the well-formed algorithm from the PEG paper
http://pdos.csail.mit.edu/papers/parsing:popl04.pdf.  The difference is that
the single flag "can fail" is replaced by two flags "can fail with progress"
and "can fail without progress".   Nodes are annotated with the flags:

  SN   =   can succeed with no progress
  SP   =   can succeed with progress
  FN   =   can fail with no progress
  FP   =   can fail with progress
  WF   =   well-formed

There are further checks on choices and repetitions. If the left hand
alternative of a choice, or similar, fails without progressing, then it musn't
have done any actions (including discards). And a left hand alternative must be
able to fail with no progress, or the right hand alternative would be
inacessible. Two further flag annotations are used:

  AA   =   includes at least one action, or discard
  AB   =   can act, or discard, before consuming any input

All these are calculated by iterating to a fixed point.  Validity WF(x) is
calculated by:

  WF(x y) = WF(x) & (SN(x) => WF(y))
  WF(x/y) = WF(x) & WF(y)
  WF(x?) = WF(x/"") = WF(x)
  WF(x*) = WF(x) & ~SN(x)
  WF(x+) = WF(xx*) = WF(x) & ~SN(x)
  WF(x!) = WF(x)

*/

class Checker implements Test.Callable {
    private String source;
    private boolean changed;

    public static void main(String[] args) {
        Binder.main(null);
        Test.run(args, new Checker());
    }

    public String test(String s) throws ParseException { return "" + run(s); }

    // Run the checker on the given source text.
    Node run(String text) throws ParseException {
        source = text;
        Binder binder = new Binder();
        Node root = binder.run(text);
        changed = true;
        while (changed) { changed = false; progress(root); }
        changed = true;
        while (changed) { changed = false; valid(root); }
        changed = true;
        while (changed) { changed = false; acting(root); }
        check(root);
        return root;
    }

    // Check whether a node has the flags SN, SP, FN, FP
    private void progress(Node node) {
        boolean xSN = false, ySN = false, oSN = node.has(SN), nSN = false;
        boolean xSP = false, ySP = false, oSP = node.has(SP), nSP = false;
        boolean xFN = false, yFN = false, oFN = node.has(FN), nFN = false;
        boolean xFP = false, yFP = false, oFP = node.has(FP), nFP = false;
        Node x = node.left(), y = node.right();
        if (x != null) {
            progress(x);
            xSN = x.has(SN); xSP = x.has(SP); xFN = x.has(FN); xFP = x.has(FP);
        }
        if (y != null) {
            progress(y);
            ySN = y.has(SN); ySP = y.has(SP); yFN = y.has(FN); yFP = y.has(FP);
        }
        switch (node.op()) {
        case ID:
            nSN = node.ref().has(SN);
            nSP = node.ref().has(SP);
            nFN = node.ref().has(FN);
            nFP = node.ref().has(FP);
            break;
        case DROP: case ACT:
            nSN = true;
            break;
        case TAG: case CHAR: case RANGE: case CAT:
            nSP = true;
            nFN = true;
            break;
        // A string has implicit backtracking, e.g. "xy" == ['x' 'y']
        case STRING:
            boolean empty = node.text().equals("\"\"");
            if (empty) { nSN = true; break; }
            nSP = true;
            nFN = true;
            break;
        case SET:
            if (! node.text().equals("''")) nSP = true;
            nFN = true;
            break;
        case RULE: case MARK:
            nSN = xSN;
            nSP = xSP;
            nFN = xFN;
            nFP = xFP;
            break;
        case AND:
            nSN = xSN && ySN;
            nSP = xSP && ySP || xSP && ySN || xSN && ySP;
            nFN = xFN || xSN && yFN;
            nFP = xFP || xSN && yFP || xSP && yFN || xSP && yFP;
            break;
        case OR:
            nSN = xSN || xFN && ySN;
            nSP = xSP || xFN && ySP;
            nFN = xFN && yFN;
            nFP = xFP || xFN && yFP;
            break;
        case OPT:
            nSN = xFN || xSN;
            nSP = xSP;
            nFP = xFP;
            break;
        case MANY:
            nSN = xFN;
            nSP = xSP && xFN;
            nFP = xFP;
            break;
        case SOME:
            nSP = xSP && xFN;
            nFN = xFN;
            nFP = xFP;
            break;
        case TRY:
            nSN = xSN;
            nSP = xSP;
            nFN = xFN || xFP;
            break;
        case HAS:
            nSN = xSN || xSP;
            nFN = xFN || xFP;
            break;
        case NOT:
            nSN = xFN || xFP;
            nFN = xSN || xSP;
            break;
        default:
            throw new Error("Type " + node.op() + " not implemented");
        }
        if (nSN != oSN || nSP != oSP || nFN != oFN || nFP != oFP) {
            changed = true;
        }
        if (nSN) node.set(SN);
        if (nSP) node.set(SP);
        if (nFN) node.set(FN);
        if (nFP) node.set(FP);
        node.note(
            (nSN ? "1" : "0") +
            (nSP ? "1" : "0") +
            (nFN ? "1" : "0") +
            (nFP ? "1" : "0")
        );
    }

    // Check whether a node is well-formed.
    private void valid(Node node) {
        boolean xWF = false, yWF = false, nWF = false, oWF = node.has(WF);
        Node x = node.left(), y = node.right();
        if (x != null) { valid(x); xWF = x.has(WF); }
        if (y != null) { valid(y); yWF = y.has(WF); }
        switch (node.op()) {
        case ID:    nWF = node.ref().has(WF);           break;
        case RULE: case OPT: case TRY: case HAS:
        case NOT:   nWF = xWF;                          break;
        case MARK: case CHAR: case RANGE: case CAT:
        case STRING: case SET: case DROP: case ACT:
        case TAG:   nWF = true;                         break;
        case AND:   nWF = xWF && (yWF || ! x.has(SN));  break;
        case OR:    nWF = xWF && yWF;                   break;
        case MANY:
        case SOME:  nWF = xWF && ! x.has(SN);           break;
        default:
            throw new Error("Type " + node.op() + " not implemented");
        }
        if (nWF && ! oWF) changed = true;
        if (nWF) node.set(WF);
    }

    // Find a lowest level invalid node to report.
    private void check(Node node) throws ParseException {
        if (node.left() != null) check(node.left());
        if (node.right() != null) check(node.right());
        if (! node.has(WF)) err(node, "potential infinite loop");
    }

    // Calculate AA and AB and report errors.  Check that left hand alternatives
    // and similar can fail without progressing and without actions.
    private void acting(Node node) throws ParseException {
        boolean xAA = false, yAA = false, nAA = false, oAA = node.has(AA);
        boolean xAB = false, yAB = false, nAB = false, oAB = node.has(AB);
        Node x = node.left(), y = node.right();
        if (x != null) { acting(x); xAA = x.has(AA); xAB = x.has(AB); }
        if (y != null) { acting(y); yAA = y.has(AA); yAB = y.has(AB); }
        switch (node.op()) {
        case CHAR: case RANGE: case CAT: case STRING: case SET: case TAG:
        // HAS and NOT have actions switched off, so don't have AA or AB.
        case HAS: case NOT:
            break;
        // If [x] succeeds, x is executed a second time with actions.
        case TRY:
            nAA = xAA;
            nAB = xAB;
            break;
        case ACT: case DROP:
            nAA = true;
            nAB = true;
            break;
        case RULE: case MARK:
            nAA = xAA;
            nAB = xAB;
            break;
        case ID:
            nAA = node.ref().has(AA);
            nAB = node.ref().has(AB);
            break;
        case AND:
            nAA = xAA || yAA;
            boolean xSN = node.left().has(SN);
            nAB = xAB || xSN && yAB;
            break;
        case OR:
            nAA = xAA || yAA;
            if (xAB) err(x, "alternative can act without progressing");
            nAB = yAB;
            if (! x.has(FN)) err(y, "unreachable alternative");
            break;
        case OPT: case MANY: case SOME:
            nAA = xAA;
            if (xAB) err(x, "component can act without progressing");
            if (! x.has(FN)) err(node, "unreachable repetition");
            break;
        default:
            throw new Error("Type " + node.op() + " not implemented");
        }
        if (nAA && ! oAA) { changed = true; node.set(AA); }
        if (nAB && ! oAB) { changed = true; node.set(AB); }
    }

    // Report an error and stop.
    private void err(Node r, String m) throws ParseException {
        int s = r.start();
        int e = r.end();
        throw new ParseException(Node.err(source, s, e, m), 0);
    }
}