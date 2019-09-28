// Pecan 1.0 stacker. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.io.*;
import static pecan.Op.*;

/* This pass checks that output handling is consistent, that a fixed number of
output items is produced for any given node, and that a fixed number of output
items is needed at the start of any node.

As a change from version 0.4, if the first rule of a grammar doesn't produce one
item, or needs output items and thus causes stack underflow, reporting is
delayed until compilation so that, during development and testing, grammars with
no actions, and arbitrary fragments of grammars, are legal.

A NET number is calculated for each node, representing the overall change in
output stack size, positive or negative, as a result of parsing. The value for
each node starts as UNKNOWN and is set at most once, so iterating to a fixed
point terminates. Recursion is resolved by deducing a result for a node such as
x / y when the result for only one subnode is known. Not all NET values may
become known, even for a well-formed grammar, because of non-terminating
recursion such as x = 'a' x and this is reported as an error.

To test for lack of underflow, a NEED number is calculated for each node. This
represents the number of items which need to be on the stack before parsing the
node. For example, the NEED value for @3x is 3 because three output items are
popped before the result is pushed back on. The NEED value for a node starts at
zero and never decreases. Recursion is resolved by deducing a tentative value
for a node such as x / y when the value is only known for one subnode. However,
since the value for a node x / y is the minimum of the values for the subnodes,
the NEED value may change more than once. In fact a value may continually
decrease, preventing termination by fixed point. For example x = 'a' / 'b' @2c x
@d has a well-defined net effect of zero, but takes an arbitrary number of items
off the stack before compensating by putting items back on. To deal with this,
an arbitrary limit of 100 is put on NEED values, beyond which it is assumed that
there is an infinite loop at work.

TODO: improve NEED calculation by better analysis. */

class Stacker implements Testable {
    private boolean switchTest;
    private Node root;
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private boolean changed;

    // Do unit testing on the Checker class, then check the switch is complete,
    // then run the Stacker unit tests.
    public static void main(String[] args) {
        if (args.length == 0) Checker.main(args);
        Stacker stacker = new Stacker();
        stacker.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, 0, 1);
            stacker.scanNode(node);
        }
        stacker.switchTest = false;
        Test.run(stacker, args);
    }

    public Node run(Source source) {
        Checker checker = new Checker();
        root = checker.run(source);
        if (root.op() == Error) return root;
        clear(root);
        changed = true;
        while (changed) { changed = false; scan(root); }
        if (root.op() != Error) check(root);
        if (root.op() != Error) annotate(root);
        return root;
    }

    // Clear all to unknown or zero.
    private void clear(Node node) {
        if (node.left() != null) clear(node.left());
        if (node.right() != null) clear(node.right());
        node.NET(UNKNOWN);
        node.NEED(0);
    }

    // Traverse the tree, bottom up, and check each node.
    private void scan(Node node) {
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
        scanNode(node);
    }

    // The main switch. Check if any of the values change.
    private void scanNode(Node node) {
        int oldNet = node.NET();
        int oldNeed = node.NEED();
        switch(node.op()) {
        case Error: case Temp: break;
        case List: case Empty: scanZero(node); break;
        case Mark: case Tag: case Char: case String: scanZero(node); break;
        case Set: case Cat: case Range: case Code: scanZero(node); break;
        case Codes: case Split: case End: case Has: scanZero(node); break;
        case Not: case Success: case Fail: scanZero(node); break;
        case Rule: scanRule(node); break;
        case Id: scanId(node); break;
        case Act: scanAct(node); break;
        case Drop: scanDrop(node); break;
        case And: scanAnd(node); break;
        case Or: scanOr(node); break;
        case Opt: scanRepeat(node); break;
        case Any: scanRepeat(node); break;
        case Some: scanRepeat(node); break;
        case Try: scanTry(node); break;
        default: assert false : "Unexpected node type " + node.op(); break;
        }
        if (oldNet != UNKNOWN && node.NET() != oldNet) {
            err(node, "inconsistent net number of output items produced");
        }
        if (node.NEED() >= 100) {
            err(node, "outputs may underflow");
        }
        if (node.NET() != oldNet || node.NEED() != oldNeed) changed = true;

    }

    // Scan node not involving actions.
    private void scanZero(Node node) {
        if (switchTest) return;
        node.NET(0);
        node.NEED(0);
    }

    private void scanRule(Node node) {
        if (switchTest) return;
        node.NET(node.right().NET());
        node.NEED(node.right().NEED());
    }

    private void scanId(Node node) {
        if (switchTest) return;
        node.NET(node.ref().NET());
        node.NEED(node.ref().NEED());
    }

    // @a, @1a, @2a, ...
    private void scanAct(Node node) {
        if (switchTest) return;
        node.NET(1 - node.arity());
        node.NEED(node.arity());
    }

    // @, @1, @2, ...
    private void scanDrop(Node node) {
        if (switchTest) return;
        node.NET(- node.arity());
        node.NEED(node.arity());
    }

    // x y
    private void scanAnd(Node node) {
        if (switchTest) return;
        int xNET = node.left().NET();
        int yNET = node.right().NET();
        int xNEED = node.left().NEED();
        int yNEED = node.right().NEED();
        if (xNET != UNKNOWN && yNET != UNKNOWN) {
            node.NET(xNET + yNET);
            node.NEED(Math.max(xNEED, yNEED - xNET));
        }
    }

    // x / y
    private void scanOr(Node node) {
        if (switchTest) return;
        int xNET = node.left().NET();
        int yNET = node.right().NET();
        int xNEED = node.left().NEED();
        int yNEED = node.right().NEED();
        if (xNET != UNKNOWN && yNET != UNKNOWN && xNET != yNET) {
            err(node, "alternatives have different net outputs");
        }
        if (xNET != UNKNOWN) node.NET(xNET);
        if (yNET != UNKNOWN) node.NET(yNET);
        node.NEED(Math.max(xNEED, yNEED));
    }

    // x?, x*, x+
    private void scanRepeat(Node node) {
        if (switchTest) return;
        int xNET = node.left().NET();
        if (xNET != UNKNOWN && xNET != 0) {
            err(node, "subrule has non-zero net output");
        }
        node.NET(0);
        node.NEED(node.left().NEED());
    }

    // [x]
    private void scanTry(Node node) {
        if (switchTest) return;
        node.NET(node.left().NET());
        node.NEED(node.left().NEED());
    }

    // Find a lowest level invalid node to report.
    private void check(Node node) {
        if (node.left() != null) check(node.left());
        if (node.right() != null) check(node.right());
        if (root.op() == Error) return;
        if (node.NET() == UNKNOWN) err(node, "variable net output");
    }

    // Report an error.
    private void err(Node r, String m) {
        int s = r.start();
        int e = r.end();
        root = new Node(Error, r.source(), 0, 0);
        root.note(r.source().error(s, e, m));
    }

    // Annotate each node with non-zero values.
    private void annotate(Node node) {
        if (node.left() != null) annotate(node.left());
        if (node.right() != null) annotate(node.right());
        String s = "";
        int net = node.NET(), need = node.NEED();
        if (need != 0) s += "NEED=" + need;
        if (net != 0) {
            if (s.length() != 0) s += ",";
            s += "NET=" + net;
        }
        node.note(s);
    }
}
