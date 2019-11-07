// Pecan 1.0 checker. Free and open source. See licence.txt.

package pecan;

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

These are calculated by iterating to a fixed point.

Some errors are deferred until just before compiling. This is for uniformity and
consistency, especially where transformations are concerned. Specifically, if a
left hand alternative always progresses when it fails, so that the right hand
alternative is inaccessible, that becomes a late warning. If there is an action
at the start of the left hand item in a choice, as in (@a x / y), that also
becomes a late warning. There are some further flag annotations:

  EE   =   contains marker
  AA   =   contains action (or discard)
  AB   =   contains action (or discard) before progressing

The first two are to check whether [x] needs to be translated to x& x. The third
is to check for @a x / y. These are conservative checks, e.g. AA and AB could be
tracked more tightly by tracking AFN, ASN, AFP, ASP.  */

class Checker implements Testable {
    private boolean switchTest;
    private Node root;
    private boolean changed;

    // Do unit testing on the Binder class, then check the switch is complete,
    // then run the Checker unit tests.
    public static void main(String[] args) {
        if (args.length == 0) Binder.main(args);
        Checker checker = new Checker();
        checker.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, null);
            checker.scanNode(node);
        }
        checker.switchTest = false;
        Test.run(checker, args);
    }

    // Run the checker on the given source text. Repeat scanning until no flags
    // change. Check and report any problems.
    public Node run(Source source) {
        Binder binder = new Binder();
        root = binder.run(source);
        if (root.op() == Error) return root;
        apply(root);
        return root;
    }

    // Apply the binder to an existing tree.
    Node apply(Node node) {
        root = node;
        clear(root);
        changed = true;
        while (changed) { changed = false; scan(root); }
        check(root);
        if (root.op() != Error) annotate(root);
        return root;
    }

    // Clear the flags, in case this is a re-run.
    private void clear(Node node) {
        if (node.left() != null) clear(node.left());
        if (node.right() != null) clear(node.right());
        node.unset(SN);
        node.unset(SP);
        node.unset(FN);
        node.unset(FP);
        node.unset(WF);
        node.unset(EE);
        node.unset(AA);
        node.unset(AB);
    }

    // Traverse the tree, bottom up, and check each node.
    private void scan(Node node) {
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
        scanNode(node);
    }

    // The main switch. Check if any of the flags change.
    private void scanNode(Node node) {
        int flags = node.flags();
        switch(node.op()) {
            case Error: case Temp: break;
            case List: case Empty: scanList(node); break;
            case Rule: scanRule(node); break;
            case Id: scanId(node); break;
            case Act: scanAct(node); break;
            case Drop: scanAct(node); break;
            case Mark: scanMark(node); break;
            case And: scanAnd(node); break;
            case Or: scanOr(node); break;
            case Opt: scanOpt(node); break;
            case Any: scanAny(node); break;
            case Some: scanSome(node); break;
            case See: scanSee(node); break;
            case Tag: case Char: case Text: case Set: scanMatch(node); break;
            case Cat: case Range: scanMatch(node); break;
            case Success: scanSuccess(node); break;
            case Fail: scanFail(node); break;
            case Split: case Eot: scanSplit(node); break;
            case Has: scanHas(node); break;
            case Not: scanNot(node); break;
            default: assert false : "Unexpected node type " + node.op(); break;
        }
        if (node.flags() != flags) changed = true;
    }

    // Scan List or Include node.
    private void scanList(Node node) {
        if (switchTest) return;
        node.set(WF);
    }

    private void scanRule(Node node) {
        if (switchTest) return;
        if (node.right().has(SN)) node.set(SN);
        if (node.right().has(SP)) node.set(SP);
        if (node.right().has(FN)) node.set(FN);
        if (node.right().has(FP)) node.set(FP);
        if (node.right().has(WF)) node.set(WF);
        if (node.right().has(EE)) node.set(EE);
        if (node.right().has(AA)) node.set(AA);
        if (node.right().has(AB)) node.set(AB);
    }

    private void scanId(Node node) {
        if (switchTest) return;
        if (node.ref().has(SN)) node.set(SN);
        if (node.ref().has(SP)) node.set(SP);
        if (node.ref().has(FN)) node.set(FN);
        if (node.ref().has(FP)) node.set(FP);
        if (node.ref().has(WF)) node.set(WF);
        if (node.ref().has(EE)) node.set(EE);
        if (node.ref().has(AA)) node.set(AA);
        if (node.ref().has(AB)) node.set(AB);
    }

    // Act or Drop.
    private void scanAct(Node node) {
        if (switchTest) return;
        node.set(SN);
        node.set(WF);
        node.set(AA);
        node.set(AB);
    }

    private void scanMark(Node node) {
        if (switchTest) return;
        node.set(SN);
        node.set(WF);
        node.set(EE);
    }

    // Split or Eot, a lookahead.
    private void scanSplit(Node node) {
        if (switchTest) return;
        node.set(SN);
        node.set(FN);
        node.set(WF);
    }

    // Tag, Char, Text, Set, Cat, Range.
    // A string has implicit backtracking, e.g. "xy" == ['x' 'y']
    private void scanMatch(Node node) {
        if (switchTest) return;
        node.set(SP);
        node.set(FN);
        node.set(WF);
    }

    // Empty string ""
    private void scanSuccess(Node node) {
        if (switchTest) return;
        node.set(SN);
        node.set(WF);
    }

    // Empty set ''
    private void scanFail(Node node) {
        if (switchTest) return;
        node.set(FN);
        node.set(WF);
    }

    // x y
    private void scanAnd(Node node) {
        if (switchTest) return;
        Node x = node.left(), y = node.right();
        boolean xSN = x.has(SN), ySN = y.has(SN);
        boolean xSP = x.has(SP), ySP = y.has(SP);
        boolean xFN = x.has(FN), yFN = y.has(FN);
        boolean xFP = x.has(FP), yFP = y.has(FP);
        boolean xWF = x.has(WF), yWF = y.has(WF);
        boolean xAA = x.has(AA), yAA = y.has(AA);
        boolean xEE = x.has(EE), yEE = y.has(EE);
        boolean xAB = x.has(AB), yAB = y.has(AB);
        if (xSN && ySN) node.set(SN);
        if (xSP && ySP || xSP && ySN || xSN && ySP) node.set(SP);
        if (xFN || xSN && yFN) node.set(FN);
        if (xFP || xSN && yFP || xSP && yFN || xSP && yFP) node.set(FP);
        if (xWF && (yWF || ! xSN)) node.set(WF);
        if (xEE && (ySN || yFN) || yEE) node.set(EE);
        if (xAA || yAA) node.set(AA);
        if (xAB || xSN && yAB) node.set(AB);
    }

    // x / y
    private void scanOr(Node node) {
        if (switchTest) return;
        Node x = node.left(), y = node.right();
        boolean xSN = x.has(SN), ySN = y.has(SN);
        boolean xSP = x.has(SP), ySP = y.has(SP);
        boolean xFN = x.has(FN), yFN = y.has(FN);
        boolean xFP = x.has(FP), yFP = y.has(FP);
        boolean xWF = x.has(WF), yWF = y.has(WF);
        boolean xAA = x.has(AA), yAA = y.has(AA);
        boolean xEE = x.has(EE), yEE = y.has(EE);
        boolean xAB = x.has(AB), yAB = y.has(AB);
        if (xSN || xFN && ySN) node.set(SN);
        if (xSP || xFN && ySP) node.set(SP);
        if (xFN && yFN) node.set(FN);
        if (xFP || xFN && yFP) node.set(FP);
        if (xWF && yWF) node.set(WF);
        if (xEE || yEE) node.set(EE);
        if (xAA || yAA) node.set(AA);
        if (xAB || xFN && yAB) node.set(AB);
    }

    // x?
    private void scanOpt(Node node) {
        if (switchTest) return;
        Node x = node.left();
        boolean xSN = x.has(SN);
        boolean xSP = x.has(SP);
        boolean xFN = x.has(FN);
        boolean xFP = x.has(FP);
        boolean xWF = x.has(WF);
        boolean xEE = x.has(EE);
        boolean xAA = x.has(AA);
        boolean xAB = x.has(AB);
        if (xFN || xSN) node.set(SN);
        if (xSP) node.set(SP);
        if (xFP) node.set(FP);
        if (xWF) node.set(WF);
        if (xEE) node.set(EE);
        if (xAA) node.set(AA);
        if (xAB) node.set(AB);
    }

    // x*
    private void scanAny(Node node) {
        if (switchTest) return;
        Node x = node.left();
        boolean xSN = x.has(SN);
        boolean xSP = x.has(SP);
        boolean xFN = x.has(FN);
        boolean xFP = x.has(FP);
        boolean xWF = x.has(WF);
        boolean xEE = x.has(EE);
        boolean xAA = x.has(AA);
        boolean xAB = x.has(AB);
        if (xFN) node.set(SN);
        if (xSP && xFN) node.set(SP);
        if (xFP) node.set(FP);
        if (xWF && ! xSN) node.set(WF);
        if (xEE) node.set(EE);
        if (xAA) node.set(AA);
        if (xAB) node.set(AB);
    }

    // x+
    private void scanSome(Node node) {
        if (switchTest) return;
        Node x = node.left();
        boolean xSN = x.has(SN);
        boolean xSP = x.has(SP);
        boolean xFN = x.has(FN);
        boolean xFP = x.has(FP);
        boolean xWF = x.has(WF);
        boolean xEE = x.has(EE);
        boolean xAA = x.has(AA);
        boolean xAB = x.has(AB);
        if (xSP && xFN) node.set(SP);
        if (xFN) node.set(FN);
        if (xFP) node.set(FP);
        if (xWF && ! xSN) node.set(WF);
        if (xEE) node.set(EE);
        if (xAA) node.set(AA);
        if (xAB) node.set(AB);
    }

    // [x] = x& x
    private void scanSee(Node node) {
        if (switchTest) return;
        Node x = node.left();
        boolean xSN = x.has(SN);
        boolean xSP = x.has(SP);
        boolean xFP = x.has(FP);
        boolean xFN = x.has(FN);
        boolean xWF = x.has(WF);
        boolean xEE = x.has(EE);
        boolean xAA = x.has(AA);
        boolean xAB = x.has(AB);
        if (xSN) node.set(SN);
        if (xSP) node.set(SP);
        if (xFN || xFP) node.set(FN);
        if (xWF) node.set(WF);
        if (xEE) node.set(EE);
        if (xAA) node.set(AA);
        if (xAB) node.set(AB);
    }

    // x&  (actions and errors are switched off)
    private void scanHas(Node node) {
        if (switchTest) return;
        Node x = node.left();
        boolean xSN = x.has(SN);
        boolean xSP = x.has(SP);
        boolean xFP = x.has(FP);
        boolean xFN = x.has(FN);
        boolean xWF = x.has(WF);
        if (xSN || xSP) node.set(SN);
        if (xFN || xFP) node.set(FN);
        if (xWF) node.set(WF);
    }

    // x!  (actions and errors are switched off)
    private void scanNot(Node node) {
        if (switchTest) return;
        Node x = node.left();
        boolean xSN = x.has(SN);
        boolean xSP = x.has(SP);
        boolean xFP = x.has(FP);
        boolean xFN = x.has(FN);
        boolean xWF = x.has(WF);
        boolean xAA = x.has(AA);
        if (xFN || xFP) node.set(SN);
        if (xSN || xSP) node.set(FN);
        if (xWF) node.set(WF);
    }

    // Find a lowest level invalid node to report.
    private void check(Node node) {
        if (node.left() != null) check(node.left());
        if (node.right() != null) check(node.right());
        if (root.op() == Error) return;
        if (! node.has(WF)) err(node, "potential infinite loop");
    }

    // Report an error.
    private void err(Node r, String m) {
        root = new Node(Error, r.source());
        root.note(r.source().error(m));
    }

    // Annotate each node with its flags.
    private void annotate(Node node) {
        if (node.left() != null) annotate(node.left());
        if (node.right() != null) annotate(node.right());
        String s = "";
        for (Node.Flag f : Node.Flag.values()) {
            if (f == WF) continue;
            if (! node.has(f)) continue;
            if (! s.equals("")) s += ",";
            s = s + f;
        }
        node.note(s);
    }
}
