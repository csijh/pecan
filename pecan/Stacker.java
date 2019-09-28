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
delayed until bytecode generation so that, during development and testing,
grammars with no actions, and arbitrary fragments of grammars, are legal.

A NET number is calculated for each node, representing the overall change in
output stack size, positive or negative, as a result of parsing. The value for
each node starts as UNKNOWN and is set at most once, so iterating to a fixed
point terminates. Recursion is resolved by deducing a result for a node such as
x / y when the result for only one subnode is known. Not all NET values may
become known, even for a well-formed grammar, because of non-terminating
recursion such as x = 'a' x and this is reported as an error.

To test for lack of underflow, a LOW number is calculated for each node. This
represents the low water mark, i.e. the lowest point that the output stack
reaches during the parsing of the node, compared to the start. For example, the
LOW value for @3x is -3 because three output items are popped before the result
is pushed back on. The LOW value for a node starts at zero and never increases.
Recursion is resolved by deducing a tentative value for a node such as x / y
when the value is only known for one subnode. However, since the value for a
node x / y is the minimum of the values for the subnodes, the LOW value may
change more than once. In fact a value may continually decrease, preventing
termination by fixed point. For example x = 'a' / 'b' @2c x @d has a
well-defined net effect of zero, but takes an arbitrary number of items off the
stack before compensating by putting items back on. To deal with this, an
arbitrary limit of -100 is put on LOW values, beyond which it is assumed that
there is an infinite loop at work.

TODO: improve by better analysis. */

class Stacker implements Testable {
    private String source;
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private boolean changed;

    // Do unit testing on the Checker class, then check the switch is complete,
    // then run the Stacker unit tests.
    public static void main(String[] args) {
        if (args.length == 0) Checker.main(args);
        stacker.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, 0, 1);
            stacker.scanNode(node);
        }
        stacker.switchTest = false;
        Test.run(new Stacker(), args);
    }

    public Node run(String text) {
        source = text;
        Checker checker = new Checker();
        Node root = checker.run(source);
        if (root.op() == Error) return root;
        clear(root);
        changed = true;
        while (changed) { changed = false; net(root); }
        String message = checkNet(root);
        changed = true;
        while (changed) { changed = false; low(root); }
        if (message == null) message = checkLow(root);
        if (message != null) {
            Node err = new Node(Error, source, 0, 0);
            err.note(message);
            return err;
        }
        return root;
    }

    // Clear all to unknown or zero.
    private void clear(Node node) {
        if (node.left() != null) clear(node.left());
        if (node.right() != null) clear(node.right());
        node.NET(UNKNOWN);
        node.LOW(0);
    }

    // Traverse the tree, bottom up, and check each node.
    private void scan(Node node) {
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
        scanNode(node);
    }

    // The main switch. Check if any of the values change.
    private void scanNode() {
        int oldNet = node.NET();
        switch(node.op()) {
        case Error: case Temp: break;
        case Include: case List: scanZero(node); break;
        case Mark: case Tag: case Char: case String: scanZero(); break;
        case Set: case Cat: case Range: case Code: scanZero(); break;
        case Codes: case Split: case End: case Has: scanZero(); break;
        case Not: case Success: case Fail: scanZero(); break;
        case Rule: scanRule(node); break;
        case Id: scanId(node); break;
        case Act: scanAct(node); break;
        case Drop: scanAct(node); break;
        case And: scanAnd(node); break;
            /*
        case Or: scanOr(node); break;
        case Opt: scanOpt(node); break;
        case Any: scanAny(node); break;
        case Some: scanSome(node); break;
        case Try: scanTry(node); break;
        */
        default: assert false : "Unexpected node type " + node.op(); break;
        }
        if (oldNet != UNKNOWN && node.NET() != oldNet) { err(node,
            "inconsistent net number of output items produced");
        }
        if (node.NET() != oldNet) changed = true;

    }

    // Scan List or Include node.
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
        node.NEED(node.arity() - 1);
    }

    // x y
    private void scanAnd(Node node) {
        if (switchTest) return;
        xNET = node.left().NET;
        yNET = node.right().NET;
        if (xNET != UNKNOWN && xNET != UNKNOWN) {
            node.NET(xNET + yNET);
            node.NEED(Math.max(xNEED, yNEED - xNET));
        }
    }

    // Calculate the NET value for a node.
    private void net(Node node) {
        int xNet = UNKNOWN, yNet = UNKNOWN, oNet = node.NET(), nNet = UNKNOWN;
        Node x = node.left(), y = node.right();
        if (x != null) { net(x); xNet = x.NET(); }
        if (y != null) { net(y); yNet = y.NET(); }
        switch(node.op()) {
        case Some: case Any: case Opt:
            nNet = 0;   break;
        case Try:
            nNet = xNet;
            break;
        case Or:
            if (xNet != UNKNOWN) nNet = xNet;
            if (yNet != UNKNOWN) nNet = yNet;
            break;
        default: throw new Error("Type " + node.op() + " unimplemented");
        }
        if (oNet == UNKNOWN && nNet != oNet) { changed = true; node.NET(nNet); }
        node.note("" + node.NET());
    }

    // Check the consistency of NET values.
    private String checkNet(Node node) {
        int xNet = UNKNOWN, yNet = UNKNOWN;
        Node x = node.left(), y = node.right();
        if (x != null) {
            String s = checkNet(x);
            if (s != null) return s;
            xNet = x.NET();
        }
        if (y != null) {
            String s = checkNet(y);
            if (s != null) return s;
            yNet = y.NET();
        }
        if (node.NET() == UNKNOWN) {
            return err(
                node, "unable to calculate number of output items produced");
        }
        switch(node.op()) {
        case Or:
            if (xNet != UNKNOWN && yNet != UNKNOWN && xNet != yNet) {
                return err(node, "choices produce unequal numbers of outputs");
            }
            break;
        case Some: case Any: case Opt:
            if (xNet != UNKNOWN && xNet != 0) {
                return err(node, "subrule produces or consumes output items");
            }
            break;
        default:
            break;
        }
        return null;
    }

    // Calculate the LOW value for a node.
    private void low(Node node) {
        int xLow = 0, yLow = 0, nLow = 0, oLow = node.LOW();
        Node x = node.left(), y = node.right();
        if (x != null) { low(x); xLow = x.LOW(); }
        if (y != null) { low(y); yLow = y.LOW(); }
        switch(node.op()) {
        case Or:
            nLow = Math.min(xLow, yLow);
            break;
        case Some: case Any: case Opt:
        case Try:
            nLow = xLow;
            break;
        default: throw new Error("Type " + node.op() + " unimplemented");
        }
        if (nLow < -100) nLow = -100;
        if (nLow < oLow) { changed = true; node.LOW(nLow); }
        node.note("" + node.NET() + "," + node.LOW());
    }

    // Check for any node where underflow can't be calculated.
    private String checkLow(Node node) {
        String x = null;
        if (node.left() != null) x = checkLow(node.left());
        if (x != null) return x;
        if (node.right() != null) x = checkLow(node.left());
        if (x != null) return x;
        if (node.LOW() == -100) return err(node, "outputs may underflow");
        else return null;
    }

    // Report an error.
    private String err(Node r, String m) {
        return Node.err(source, r.start(), r.end(), m);
    }
}
