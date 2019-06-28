// Pecan 1.0 stacker. Free and open source. See licence.txt.

package pecan;

import java.text.*;
import java.util.*;
import java.io.*;
import static pecan.Op.*;

/* This pass checks that output handling is consistent, that a fixed number of
output items is produced for any given rule, and that there is no stack
underflow. (This is a change from version 0.4, where the first rule was forced
to produce a single output item. This is to support grammar fragments.)

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

TODO: improve by detecting repeated configurations. */

class Stacker implements Testable {
    private String source;
    private static final int UNKNOWN = Short.MIN_VALUE;
    private boolean changed;

    public static void main(String[] args) {
        if (args.length == 0) Checker.main(args);
        if (args.length == 0) Test.run(new Stacker());
        else Test.run(new Parser(), Integer.parseInt(args[0]));
    }

    public String test(String g, String s) throws ParseException {
        return "" + run(g);
    }

    Node run(String text) throws ParseException {
        source = text;
        Checker checker = new Checker();
        Node root = checker.run(source);
        clear(root);
        changed = true;
        while (changed) { changed = false; net(root); }
        checkNet(root);
        changed = true;
        while (changed) { changed = false; low(root); }
        checkLow(root);
        return root;
    }

    // Clear all to unknown or zero.
    private void clear(Node node) {
        if (node.left() != null) clear(node.left());
        if (node.right() != null) clear(node.right());
        node.NET(UNKNOWN);
        node.LOW(0);
    }

    // Calculate the NET value for a node.
    private void net(Node node) {
        int xNet = UNKNOWN, yNet = UNKNOWN, oNet = node.NET(), nNet = UNKNOWN;
        Node x = node.left(), y = node.right();
        if (x != null) { net(x); xNet = x.NET(); }
        if (y != null) { net(y); yNet = y.NET(); }
        switch(node.op()) {
        case Drop: case String: case Set: case Divider: case Range: case Cat:
        case Tag: case Some: case Many: case Opt: case Has: case Not: case Char:
        case Mark:
            nNet = 0;   break;
        case Act:
            int arity = node.ref().value();
            nNet = 1-arity;
            break;
        case Id:
            nNet = node.ref().NET();
            break;
        case Rule: case Try:
            nNet = xNet;
            break;
        case And:
            if (xNet != UNKNOWN && yNet != UNKNOWN) nNet = xNet + yNet;
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
    private void checkNet(Node node) throws ParseException {
        int xNet = UNKNOWN, yNet = UNKNOWN;
        Node x = node.left(), y = node.right();
        if (x != null) { checkNet(x); xNet = x.NET(); }
        if (y != null) { checkNet(y); yNet = y.NET(); }
        if (node.NET() == UNKNOWN) {
            err(node, "unable to calculate number of output items produced");
        }
        switch(node.op()) {
        case Or:
            if (xNet != UNKNOWN && yNet != UNKNOWN && xNet != yNet) {
                err(node, "choices produce unequal numbers of outputs");
            }
            break;
        case Some: case Many: case Opt:
            if (xNet != UNKNOWN && xNet != 0) {
                err(node, "subrule produces or consumes output items");
            }
            break;
        default:
            break;
        }
    }

    // Calculate the LOW value for a node.
    private void low(Node node) {
        int xLow = 0, yLow = 0, nLow = 0, oLow = node.LOW();
        Node x = node.left(), y = node.right();
        if (x != null) { low(x); xLow = x.LOW(); }
        if (y != null) { low(y); yLow = y.LOW(); }
        switch(node.op()) {
        case Drop: case String: case Set: case Char: case Divider: case Range:
        case Cat: case Tag: case Mark:
            break;
        case Act:
            int arity = node.ref().value();
            nLow = -arity;
            break;
        case Id:
            nLow = node.ref().LOW();
            break;
        case Rule:
            nLow = xLow;
            break;
        case And:
            nLow = Math.min(xLow, x.NET() + yLow);
            break;
        case Or:
            nLow = Math.min(xLow, yLow);
            break;
        case Some: case Many: case Opt:
        case Try: case Has: case Not:
            nLow = xLow;
            break;
        default: throw new Error("Type " + node.op() + " unimplemented");
        }
        if (nLow < -100) nLow = -100;
        if (nLow < oLow) { changed = true; node.LOW(nLow); }
        node.note("" + node.NET() + "," + node.LOW());
    }

    // Check for underflow of the first rule.
    private void checkLow(Node node) throws ParseException {
        if (node.LOW() < 0) err(node, "outputs may underflow");
    }

    // Report an error and stop.
    private void err(Node r, String m) throws ParseException {
        throw new ParseException(Node.err(source, r.start(), r.end(), m), 0);
    }
}
