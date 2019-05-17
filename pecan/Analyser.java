// Pecan 5 analyzer. Free and open source. See licence.txt.

package pecan;

import java.text.*;
import java.util.*;
import java.io.*;
import static pecan.Op.*;
import static pecan.Info.Flag.*;

/* Work out the FIRST, START and FOLLOW set for every node. This is very much
like traditional FIRST/FOLLOW analysis for CFG grammars, except that FIRST is
divided into two disjoint sets, FIRST and START:

   FIRST =  input items which a parsing expression can start with, where
            parsing always makes progress (SP or FP), so the item is consumed
   START =  input items which a parsing expression can start with (SP or FP),
            but where there may also be no progress (FN), so that the
            item may be matched by a subsequent alternative
   FOLLOW = items which can follow an expression

For example ('a' 'b') has 'a' in its FIRST set, whereas ['a' 'b'] has 'a' in its
START set.

For character input, the ascii characters are treated as 128 separate items. In
addition, there are a further 30 items representing the characters beyond ascii
from each disjoint Unicode category (excluding Uc). Explicit individual
characters beyond ascii are regarded as belonging to one of these 30 sets, but
the set is treated as a START item because it might not match.

The calculations use the SN,SP,FN,FP flags (succeed/fail without/with progress)
from the checker pass. The calculations are roughly these, with FIRST/START
synthesized upwards in the tree, and FOLLOW inherited downwards in the tree:

  e = x / y:
    FIRST(e) += FIRST(x) + FIRST(y)
    START(e) += (START(x) - FIRST(y)) + (START(y) - FIRST(x))
    FOLLOW(x) += FOLLOW(e), FOLLOW(y) += FOLLOW(e)

  e = x y:
    if SN(x) then same as x / y else
    FIRST(e) += FIRST(x), START(e) += START(x),
    FOLLOW(y) += FOLLOW(e)
    if (SN(y)) FOLLOW(x) += FOLLOW(e)

  e = "":
    FIRST(e) += {}, START(e) += {}, (FOLLOW(e) from above)

  e = "a"
    if ascii then FIRST(e) += {'a'} else START(e) += category,
    (FOLLOW(e) from above)

  e = x* = x+ / ""
    FIRST(e) += FIRST(x), START(e) += START(x), FOLLOW(x) += FOLLOW(e)

  e = id
    FIRST(e) += FIRST(id), START(e) += START(id), FOLLOW(id) += FOLLOW(e)

  e = [x]
    FIRST += {}, START(x) += FIRST(x) + START(x), FOLLOW(x) += FOLLOW(e)

  e = x&   or   e = x!
    START(e) += FIRST(x), (FOLLOW(e) from above)

  e = %Tag
    FIRST(e) += {Tag}, START += {}, (FOLLOW(e) from above)

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
        if (root.has(TextInput)) bits = 128 + 31;
        else bits = maxTagIndex(root) + 1;
        changed = true;
        while (changed) { changed = false; findSets(root); }
        return root;
    }

    // Find the maximum tag index.
    int maxTagIndex(Node node) {
        int x = 0, y = 0;
        if (node.op() == Tag) x = node.value();
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
        case Drop: case Act: case Mark:
            break;
        case Rule:
            add(node.FIRST(), x.FIRST());
            add(node.START(), x.START());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        case Id:
            add(node.FIRST(), node.ref().FIRST());
            add(node.START(), node.ref().START());
            add(node.ref().FOLLOW(), node.FOLLOW());
            break;
        case Tag:
            add(node.FIRST(), node.value());
            break;
        case Char:
            ch = node.value();
            if (ch < 128) add(node.FIRST(), ch);
            else add(node.START(), 128 + Character.getType(ch));
            break;
        case Cat:
            Category cat = Category.values()[node.value()];
            if (cat == Category.Uc) {
                for (int i=0; i<bits; i++) add(node.FIRST(), i);
            } else {
                add(node.FIRST(), cat.ascii);
                add(node.FIRST(), 128 + node.value());
            }
            break;
        case Range:
            int from = x.value(), to = y.value();
            for (ch = from; ch <= to; ch++) {
                if (ch < 128) add(node.FIRST(), ch);
                else add(node.START(), 128 + Character.getType(ch));
            }
            break;
        case String: case Set:
            String text = node.text();
            if (text.length() == 2) break;
            ch = text.codePointAt(1);
            if (ch < 128) add(node.FIRST(), ch);
            else add(node.START(), 128 + Character.getType(ch));
            break;
        case Some: case Many: case Opt:
            add(node.FIRST(), x.FIRST());
            add(node.START(), x.START());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        case Try:
            add(node.START(), x.FIRST());
            add(node.START(), x.START());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        // Note x! swaps S and F, but not P and N, so is the same as x&
        case Has: case Not:
            add(node.START(), x.FIRST());
            add(node.START(), x.START());
            break;
        case And:
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
        case Or:
            add(node.FIRST(), x.FIRST());
            add(node.FIRST(), y.FIRST());
            addSub(node.START(), x.START(), y.FIRST());
            addSub(node.START(), y.START(), x.FIRST());
            add(y.FOLLOW(), node.FOLLOW());
            add(x.FOLLOW(), node.FOLLOW());
            break;
        default:
            throw new Error("Node type "+ node.op() +" not implemented");
        }
        node.note(" " +
            show(node, node.FIRST()) + ":" +
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
        if (n.has(TokenInput)) return b.toString();
        boolean invert = b.cardinality() > bits / 2;
        String s = "";
        for (int i=0; i<128; i++) {
            if (invert ? ! b.get(i) : b.get(i)) s += (char) i;
        }
        if (invert) s += "!";
        return s;
    }
}
