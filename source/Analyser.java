// Part of Pecan 4. Open source - see licence.txt.

package pecan;

import java.text.*;
import java.util.*;
import java.io.*;
import static pecan.Op.*;
import static pecan.Info.Flag.*;

/* Work out the FIRST, START and FOLLOW set for every node.  This is very much
like traditional FIRST/FOLLOW analysis, except that FIRST is divided into two
disjoint sets, FIRST and START:
-
-   FIRST =  input items which a parsing expression can start with, where
-            parsing always makes progress (SP or FP), so the item is consumed
-   START =  input items which a parsing expression can start with (SP or FP),
-            but where there may also be no progress (SN or FN), so that the
-            item may be matched by a subsequent alternative
-   FOLLOW = items which can follow an expression (inherited downwards,
-            rather than synthesised upwards)
-
For example ('a' 'b') has 'a' in its FIRST set, whereas ['a' 'b'] has 'a' in its
START set.

For character input, the ascii characters are treated as 128 separate items.  In
addition, there are a further 30 items representing the characters beyond ascii
from each disjoint Unicode category (excluding Uc). Explicit individual
characters beyond ascii are regarded as belonging to one of these 30 sets, but
are treated as START items.


Need two sets of starters (a) can start with s and commits (b) can start with s
but doesn't necessarily commit (so a later alternative could be offered s). Also
need follow.  FIRST, START, FOLLOW.  FIRST and START are disjoint.  FOLLOW is
calculated down the tree

Generate: a = x y z
bool a() { return x() && y() && z(); }

a = x / y / z
bool a() { switch() {  x()  y()  z(); } }

a = [x] y / z
bool a() { if (try(x)) y(); else z(); }

LN Can involve a lookahead before progressing.  Doesn't differentiate starters.
Need two sets of starters (a) can start with s and commits (b) can start with s
but doesn't necessarily commit (so a later alternative could be offered s). Also
need follow.  FIRST, START, FOLLOW.  FIRST and START are disjoint.  FOLLOW is
calculated down the tree

x / y:
  FIRST += xFIRST + yFIRST
  START += (xSTART - yFIRST) + (ySTART - xFIRST)
  xFOLLOW += FOLLOW, yFOLLOW += FOLLOW
x y:
  if xSN then same as x / y else
  FIRST += xFIRST, START += xSTART,
  yFOLLOW += FOLLOW
  if (y.SN) xFOLLOW += FOLLOW
"":
  FIRST += {}, START += {}, (FOLLOW from above)
"a"
  FIRST += {'a'}, ...
x*: = x+ / ""
  FIRST += xFIRST, START += xSTART, xFOLLOW += FOLLOW
ID:
  FIRST += xFIRST, START += xSTART, xFOLLOW += FOLLOW
[x]:
  FIRST += {}, START += XFIRST + xSTART, xFOLLOW += FOLLOW
x&:
  xFOLLOW += ?
x!:
%TAG:
  FIRST += {TAG}, START += {}, ...

Soft fail = return false.
Hard fail = exception jump to innermost containing lookahead.
WHY DOES CURRENT INTERPRETER WORK?
Because it makes progress checks at various points, to return stepwise to
the exception point.
IF we say the x inside a lookahead [x] x& x! mustn't act without progressing,
then progress is input consumption only.

What if hard fail = success = ok, and only soft fail = false = return.  This
is enough to stop y in x / y.  But what about x y?
*/


class Analyser implements Test.Callable {
    private String source;
    private boolean changed;
    private int bits;

    public static void main(String[] args) {
        Stacker.main(null);
        Test.run(args, new Analyser());
    }

    public String test(String s) throws ParseException { return "" + run(s); }

    Node run(String text) throws ParseException {
        source = text;
        Stacker stacker = new Stacker();
        Node root = stacker.run(source);
        if (root.has(IC)) bits = 128 + 31;
        else bits = maxTagIndex(root) + 1;
        changed = true;
        while (changed) { changed = false; findSets(root); }
        return root;
    }

    // Find the maximum tag index.
    int maxTagIndex(Node node) {
        int x = 0, y = 0;
        if (node.op() == TAG) x = node.value();
        if (node.left() != null) x = maxTagIndex(node.left());
        if (node.right() != null) y = maxTagIndex(node.right());
        return Math.max(x, y);
    }

    // Calculate the FIRST and START sets for a node.
    void findSets(Node node) {
        Node x = node.left(), y = node.right();
        if (x != null) findSets(x);
        if (y != null) findSets(y);
        int ch;
        switch (node.op()) {
        case DROP: case ACT:
            break;
        case RULE: case MARK:
            add(node.FIRST(), x.FIRST());
            add(node.START(), x.START());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        case ID:
            add(node.FIRST(), node.ref().FIRST());
            add(node.START(), node.ref().START());
            add(node.ref().FOLLOW(), node.FOLLOW());
            break;
        case TAG:
            add(node.FIRST(), node.value());
            break;
        case CHAR:
            ch = node.value();
            if (ch < 128) add(node.FIRST(), ch);
            else add(node.START(), 128 + Character.getType(ch));
            break;
        case CAT:
            Category cat = Category.values()[node.value()];
            if (cat == Category.Uc) {
                for (int i=0; i<bits; i++) add(node.FIRST(), i);
            } else {
                add(node.FIRST(), cat.ascii);
                add(node.FIRST(), 128 + node.value());
            }
            break;
        case RANGE:
            int from = x.value(), to = y.value();
            for (ch = from; ch <= to; ch++) {
                if (ch < 128) add(node.FIRST(), ch);
                else add(node.START(), 128 + Character.getType(ch));
            }
            break;
        case STRING: case SET:
            String text = node.text();
            if (text.length() == 2) break;
            ch = text.codePointAt(1);
            if (ch < 128) add(node.FIRST(), ch);
            else add(node.START(), 128 + Character.getType(ch));
            break;
        case SOME: case MANY: case OPT:
            add(node.FIRST(), x.FIRST());
            add(node.START(), x.START());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        case TRY:
            add(node.START(), x.FIRST());
            add(node.START(), x.START());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        // Note x! swaps S and F, but not P and N, so is the same as x&
        case HAS: case NOT:
            add(node.START(), x.FIRST());
            add(node.START(), x.START());
            break;
        case AND:
            boolean xSN = x.has(SN), ySN = y.has(SN);
            if (xSN) {
                add(node.FIRST(), x.FIRST());
                add(node.FIRST(), y.FIRST());
                addSub(node.START(), x.START(), y.FIRST());
                addSub(node.START(), y.START(), x.FIRST());
            } else {
                add(node.FIRST(), x.FIRST());
                add(node.START(), x.START());
            }
            add(y.FOLLOW(), node.FOLLOW());
            add(x.FOLLOW(), y.FIRST());
            add(x.FOLLOW(), y.START());
            if (ySN) add(x.FOLLOW(), node.FOLLOW());
            break;
        case OR:
            add(node.FIRST(), x.FIRST());
            add(node.FIRST(), y.FIRST());
            addSub(node.START(), x.START(), y.FIRST());
            addSub(node.START(), y.START(), x.FIRST());
            add(y.FOLLOW(), node.FOLLOW());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        default:
            throw new Error("Type " + node.op() + " not implemented");
        }
        node.note(" " + show(node, node.FIRST()) + ":" +
                  show(node, node.START()) + ":" +
                  show(node, node.FOLLOW())
        );
    }

    // Set the given bit in a bitset, and check changed.
    void add(BitSet b, int i) {
        if (! b.get(i)) {
            changed = true;
            b.set(i);
        }
    }

    // Set the given bits b in bitset n, and check changed.
    void add(BitSet n, BitSet b) {
        int len = b.length();
        for (int i=0; i<len; i++) {
            if (b.get(i) && ! n.get(i)) {
                changed = true;
                n.set(i);
            }
        }
    }

/*
    // Add the bits not in b to n, and check changed.
    void addNot(BitSet n, BitSet b) {
        for (int i=0; i<bits; i++) {
            if (! b.get(i)) {
                changed = true;
                b.set(i);
            }
        }
    }
*/
    // Add the bits in a but not b to n, and check changed.
    void addSub(BitSet n, BitSet a, BitSet b) {
        int len = a.length();
        for (int i=0; i<len; i++) {
            if (a.get(i) && ! b.get(i) && ! n.get(i)) {
                changed = true;
                n.set(i);
            }
        }
    }

    // Show a bitset, just for testing.
    String show(Node n, BitSet b) {
        if (n.has(IT)) return b.toString();
        boolean invert = b.cardinality() > bits / 2;
        String s = "";// = "'";
        for (int i=0; i<128; i++) {
            if (invert ? ! b.get(i) : b.get(i)) s += (char) i;
        }
        //s += "'";
        if (invert) s += "!";
        return s;
    }
}
