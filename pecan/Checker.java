// Pecan 1.0 checker. Free and open source. See licence.txt.

package pecan;

import java.text.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Check that a grammar is valid. Report any non-terminating left recursion as
an error, using an adaptation of the well-formedness algorithm from the PEG
paper http://pdos.csail.mit.edu/papers/parsing:popl04.pdf. The single flag "can
fail" is replaced by two flags "can fail with progress" and "can fail without
progress". Nodes are annotated with the flags:

  SN   =   can succeed with no progress
  SP   =   can succeed with progress
  FN   =   can fail with no progress
  FP   =   can fail with progress
  WF   =   well-formed

Validity WF(x) is calculated by iterating to a fixed point, using:

  WF(x y) = WF(x) & (SN(x) => WF(y))
  WF(x/y) = WF(x) & WF(y)
  WF(x?) = WF(x/"") = WF(x)
  WF(x*) = WF(x) & ~SN(x)
  WF(x+) = WF(xx*) = WF(x) & ~SN(x)
  WF(x!) = WF(x)

A minor change from version 0.4 is that if a left hand alternative can't fail
with no progress, so that the right hand alternative is inaccessible, it is no
longer reported as an error (so that transformations remain valid).

A major change from version 0.4 is that actions are now allowed at the start of
the left hand item in a choice, as in (@a x / y). This is for uniformity and
consistency, especially where transformations are concerned. To obtain the
effect of undoing the action if the left choice mismatches, actions are delayed
until progress is made in the input. There are two further flag annotations:

  AA   =   involves at least one action (or discard)
  AB   =   can act (or discard) before consuming any input

These are used, or can potentially be used in the future, for optimisations. */

class Checker implements Testable {
    private String source;
    private boolean changed;

    public static void main(String[] args) {
        if (args.length == 0) Binder.main(args);
        if (args.length == 0) Test.run(new Checker());
        else Test.run(new Parser(), Integer.parseInt(args[0]));
    }

    public String test(String g, String s) throws ParseException {
        return "" + run(g);
    }

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
        case Id:
            nSN = node.ref().has(SN);
            nSP = node.ref().has(SP);
            nFN = node.ref().has(FN);
            nFP = node.ref().has(FP);
            break;
        case Drop: case Act: case Mark:
            nSN = true;
            break;
        case Tag: case Char: case Range: case Cat:
            nSP = true;
            nFN = true;
            break;
        // A string has implicit backtracking, e.g. "xy" == ['x' 'y']
        case String:
            boolean empty = node.text().equals("\"\"");
            if (empty) { nSN = true; break; }
            nSP = true;
            nFN = true;
            break;
        case Set:
            if (! node.text().equals("''")) nSP = true;
            nFN = true;
            break;
        case Rule:
            nSN = xSN;
            nSP = xSP;
            nFN = xFN;
            nFP = xFP;
            break;
        case And:
            nSN = xSN && ySN;
            nSP = xSP && ySP || xSP && ySN || xSN && ySP;
            nFN = xFN || xSN && yFN;
            nFP = xFP || xSN && yFP || xSP && yFN || xSP && yFP;
            break;
        case Or:
            nSN = xSN || xFN && ySN;
            nSP = xSP || xFN && ySP;
            nFN = xFN && yFN;
            nFP = xFP || xFN && yFP;
            break;
        case Opt:
            nSN = xFN || xSN;
            nSP = xSP;
            nFP = xFP;
            break;
        case Many:
            nSN = xFN;
            nSP = xSP && xFN;
            nFP = xFP;
            break;
        case Some:
            nSP = xSP && xFN;
            nFN = xFN;
            nFP = xFP;
            break;
        case Try:
            nSN = xSN;
            nSP = xSP;
            nFN = xFN || xFP;
            break;
        case Has:
            nSN = xSN || xSP;
            nFN = xFN || xFP;
            break;
        case Not:
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
        case Id:    nWF = node.ref().has(WF);           break;
        case Rule: case Opt: case Try: case Has:
        case Not:   nWF = xWF;                          break;
        case Mark: case Char: case Range: case Cat:
        case String: case Set: case Drop: case Act:
        case Tag:   nWF = true;                         break;
        case And:   nWF = xWF && (yWF || ! x.has(SN));  break;
        case Or:    nWF = xWF && yWF;                   break;
        case Many:
        case Some:  nWF = xWF && ! x.has(SN);           break;
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

    // Calculate AA and AB. No longer report errors.
    private void acting(Node node) throws ParseException {
        boolean xAA = false, yAA = false, nAA = false, oAA = node.has(AA);
        boolean xAB = false, yAB = false, nAB = false, oAB = node.has(AB);
        Node x = node.left(), y = node.right();
        if (x != null) { acting(x); xAA = x.has(AA); xAB = x.has(AB); }
        if (y != null) { acting(y); yAA = y.has(AA); yAB = y.has(AB); }
        switch (node.op()) {
            // Has and Not have actions switched off, so don't have AA or AB.
        case Char: case Range: case Cat: case String: case Set: case Tag:
        case Mark: case Has: case Not:
            break;
        // If [x] succeeds, x is executed a second time with actions on.
        case Try:
            nAA = xAA;
            nAB = xAB;
            break;
        case Act: case Drop:
            nAA = true;
            nAB = true;
            break;
        case Rule:
            nAA = xAA;
            nAB = xAB;
            break;
        case Id:
            nAA = node.ref().has(AA);
            nAB = node.ref().has(AB);
            break;
        case And:
            nAA = xAA || yAA;
            boolean xSN = node.left().has(SN);
            nAB = xAB || xSN && yAB;
            break;
        case Or:
            nAA = xAA || yAA;
            nAB = yAB;
            break;
        case Opt: case Many: case Some:
            nAA = xAA;
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
