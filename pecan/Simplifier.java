// Pecan 1.0 simplifier. Free and open source. See licence.txt.

package pecan;

import java.text.*;
import static pecan.Op.*;
import static pecan.Info.Flag.*;

/* Simplify a grammar to make generating code simpler.
If x has actions, replace [x] by (x& x) so it gets executed twice. In x&
and x!, make a copy of x with no actions, if necessary. */

class Simplifier implements Test.Callable {
    private String source;
    private boolean changed;

    public static void main(String[] args) {
        Stacker.main(null);
        Test.run(args, new Simplifier());
    }

    public String test(String s) throws ParseException { return "" + run(s); }

    // Run the passes up to this one on the given source text.
    Node run(String text) throws ParseException {
        source = text;
        Stacker stacker = new Stacker();
        Node root = stacker.run(text);
//        expandSome(root);
        expandTry(root);
        deactivate(root);
        return root;
    }

    // Replace x+ by x x*. Make one copy of x an Id cross reference, so that
    // it remains a tree. Also, clear notes.
    private void expandSome(Node node) {
        if (node.left() != null) expandSome(node.left());
        if (node.right() != null) expandSome(node.right());
        /*
        if (node.op() == SOME) {
            Node x = node.left();
            Node xid = new Node(Id, source, x.start(), x.end());
            xid.ref(x);
            node.op(And);
            int s = node.start(), e = node.end();
            node.left(xid);
            node.right(new Node(MANY, x, source, s, e));
        }
        */
        node.note("");
    }

    // Replace [x] by (x& x) if x contains actions.
    private void expandTry(Node node) {
        if (node.left() != null) expandTry(node.left());
        if (node.right() != null) expandTry(node.right());
        if (node.op() == TRY && node.has(AA)) {
            Node x = node.left();
            Node xid = new Node(Id, source, x.start(), x.end());
            xid.ref(x);
            node.op(And);
            int s = node.start(), e = node.end();
            node.left(new Node(HAS, x, source, s, e));
            node.right(xid);
        }
    }

    // In x& or x! replace x by a copy with actions removed.
    // Some resulting nodes still refer to the original source text.
    private void deactivate(Node node) {
        if (node.left() != null) deactivate(node.left());
        if (node.right() != null) expandSome(node.right());
        if (node.op() != HAS && node.op() != NOT) return;
        if (! node.left().has(AA)) return;
        node.left(inactive(node.left()));
    }

    // Create an inactive copy of a subtree.
    private Node inactive(Node node) {
        Op op = node.op();
        Node x = node.left(), y = node.right();

        // Change an action to "do nothing"
        if (op == ACT || op == DROP) {
            Node n = new Node(STRING, "\"\"", 0, 2);
            return n;
        }

        // Remove an action altogether in simple cases.
        if (op == And && (x.op() == ACT || x.op() == DROP)) {
            return inactive(y);
        }
        if (op == And && (y.op() == ACT || y.op() == DROP)) {
            return inactive(x);
        }

        // For an Id, make it refer to an inactive copy of its rule.
        if (op == Id) {
            String name = "$" + node.text();
            Node copy = new Node(Id, name, 0, name.length());
            copy.ref(inactiveRule(node.ref()));
            return copy;
        }

        // Otherwise, make a copy.
        if (x != null) x = inactive(x);
        if (y != null) y = inactive(y);
        Node copy = node.copy(x, y);
        return copy;
    }

    // If a node is a rule, insert an extra rule where necessary.
    private Node inactiveRule(Node node) {
        // No need for change.
        if (! node.has(AA)) return node;

        // An inactive copy has already been made.
        Node next = node.right();
        if (next != null && next.text().startsWith("$")) return next;

        // Make a copy, insert it, and return it.
        String name = "$" + node.text();
        Node rhs = inactive(node.left());
        Node copy = new Node(Rule, rhs, next, name, 0, name.length());
        node.right(copy);
        return copy;
    }

    // Report an error and stop.
    private void err(Node r, String m) throws ParseException {
        int s = r.start();
        int e = r.end();
        throw new ParseException(Node.err(source, s, e, m), 0);
    }
}
