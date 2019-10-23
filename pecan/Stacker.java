// Pecan 1.0 stacker. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.io.*;
import static pecan.Op.*;
import static pecan.Node.Count.*;

/* This pass checks that output handling is consistent, that a fixed number of
output items is produced for any given node, and that a fixed number of output
items is needed at the start of any node. Nodes within & or ! lookaheads are
excluded from the analysis, since actions ae switched off during them.

If the first rule of a grammar doesn't produce one item, or needs output items
and thus causes stack underflow, reporting is delayed until compilation so that,
during development and testing, grammars with no actions, and arbitrary
fragments of grammars, are legal.

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
            Node node = new Node(op, null, null);
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
        node.set(NET, UNKNOWN);
        node.set(NEED, 0);
    }

    // Traverse the tree, bottom up, and check each node.
    private void scan(Node node) {
        if (node.op() == Has || node.op() == Not) {
            clearZero(node);
        } else {
            if (node.left() != null) scan(node.left());
            if (node.right() != null) scan(node.right());
            scanNode(node);
        }
    }

    // Clear all to zero.
    private void clearZero(Node node) {
        if (node.left() != null) clearZero(node.left());
        if (node.right() != null) clearZero(node.right());
        node.set(NET, 0);
        node.set(NEED, 0);
    }

    // The main switch. Check if any of the values change.
    private void scanNode(Node node) {
        int oldNet = node.get(NET);
        int oldNeed = node.get(NEED);
        switch(node.op()) {
        case Error: case Temp: break;
        case List: case Empty: scanZero(node); break;
        case Mark: case Tag: case Char: case String: scanZero(node); break;
        case Set: case Cat: case Range: scanZero(node); break;
        case Split: case Eot: case Has: scanZero(node); break;
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
        case See: scanSee(node); break;
        default: assert false : "Unexpected node type " + node.op(); break;
        }
        if (oldNet != UNKNOWN && node.get(NET) != oldNet) {
            err(node, "inconsistent net number of output items produced");
        }
        if (node.get(NEED) >= 100) {
            err(node, "outputs may underflow");
        }
        if (node.get(NET) != oldNet || node.get(NEED) != oldNeed) {
            changed = true;
        }
    }

    // Scan node not involving actions.
    private void scanZero(Node node) {
        if (switchTest) return;
        node.set(NET, 0);
        node.set(NEED, 0);
    }

    private void scanRule(Node node) {
        if (switchTest) return;
        node.set(NET, node.right().get(NET));
        node.set(NEED, node.right().get(NEED));
    }

    private void scanId(Node node) {
        if (switchTest) return;
        node.set(NET, node.ref().get(NET));
        node.set(NEED, node.ref().get(NEED));
    }

    // @a, @1a, @2a, ...
    private void scanAct(Node node) {
        if (switchTest) return;
        node.set(NET, 1 - node.arity());
        node.set(NEED, node.arity());
    }

    // @, @1, @2, ...
    private void scanDrop(Node node) {
        if (switchTest) return;
        node.set(NET, - node.arity());
        node.set(NEED, node.arity());
    }

    // x y
    private void scanAnd(Node node) {
        if (switchTest) return;
        int xNET = node.left().get(NET);
        int yNET = node.right().get(NET);
        int xNEED = node.left().get(NEED);
        int yNEED = node.right().get(NEED);
        if (xNET != UNKNOWN && yNET != UNKNOWN) {
            node.set(NET, xNET + yNET);
            node.set(NEED, Math.max(xNEED, yNEED - xNET));
        }
    }

    // x / y
    private void scanOr(Node node) {
        if (switchTest) return;
        int xNET = node.left().get(NET);
        int yNET = node.right().get(NET);
        int xNEED = node.left().get(NEED);
        int yNEED = node.right().get(NEED);
        if (xNET != UNKNOWN && yNET != UNKNOWN && xNET != yNET) {
            err(node, "alternatives have different net outputs");
        }
        if (xNET != UNKNOWN) node.set(NET, xNET);
        if (yNET != UNKNOWN) node.set(NET, yNET);
        node.set(NEED, Math.max(xNEED, yNEED));
    }

    // x?, x*, x+
    private void scanRepeat(Node node) {
        if (switchTest) return;
        int xNET = node.left().get(NET);
        if (xNET != UNKNOWN && xNET != 0) {
            err(node, "subrule has non-zero net output");
        }
        node.set(NET, 0);
        node.set(NEED, node.left().get(NEED));
    }

    // [x]
    private void scanSee(Node node) {
        if (switchTest) return;
        node.set(NET, node.left().get(NET));
        node.set(NEED, node.left().get(NEED));
    }

    // Find a lowest level invalid node to report.
    private void check(Node node) {
        if (node.left() != null) check(node.left());
        if (node.right() != null) check(node.right());
        if (root.op() == Error) return;
        if (node.get(NET) == UNKNOWN) err(node, "variable net output");
    }

    // Report an error.
    private void err(Node r, String m) {
        root = new Node(Error, r.source());
        root.note(r.source().error(m));
    }

    // Annotate each node with non-zero values.
    private void annotate(Node node) {
        if (node.left() != null) annotate(node.left());
        if (node.right() != null) annotate(node.right());
        String s = "";
        int net = node.get(NET), need = node.get(NEED);
        if (need != 0) s += "NEED=" + need;
        if (net != 0) {
            if (s.length() != 0) s += ",";
            s += "NET=" + net;
        }
        node.note(s);
    }
}
