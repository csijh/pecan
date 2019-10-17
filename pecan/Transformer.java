// Pecan 1.0 transformer. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Provide a collection of transform methods, to be used before compiling to a
target language or to bytecode.

The transforms aim to preserve the analysis flags that might be used to guide
compilation, particularly the FP flag.

The source text of nodes is only preserved to the extent that (a) a rule node
has text which serves as a comment for its compiled function and (b) atomic
nodes still have the right text so that they are compiled correctly.

The tree structure of rules is not preserved. Nodes may becomes shared. */

// TODO: liftSome
// TODO: replace string in rule (assuming all nodes same source)

class Transformer {

    // Replace [x] by (x& x) if x contains actions or markers. Assume x is small
    // enough to be repeated twice, rather than making a separate rule for it.
    void expandSee(Node node) {
        if (node.left() != null) expandSee(node.left());
        if (node.right() != null) expandSee(node.right());
        if (node.op() == See && (node.has(AA) || node.has(EE))) {
            Node x = node.left();
            Node hasX = new Node(Has, x, x.source(), x.start(), x.end());
            node.op(And);
            node.left(hasX);
            node.right(x);
        }
    }

    // Lift out loops into separate rules. Gather the names of the ids, so that
    // new names can be added without clashes. Deal with each rule in turn,
    // passing the list node so that new rules can be inserted in front.
    void lift(Node root) {
        Node node = root;
        Set<String> names = new HashSet<>();
        while (node.op() == List) {
            names.add(node.left().left().name());
            node = node.right();
        }
        node = root;
        while (node.op() == List) {
            Node next = node.right();
            liftNode(names, node, node.left().right());
            node = next;
        }
    }

    // Given the current rule names, a list node, and a node within its rule,
    // lift the loops within the node out as separate preceding rules. Lift
    // innermost loops out first.
    private void liftNode(Set<String> names, Node list, Node node) {
        if (node.left() != null) liftNode(names, list, node.left());
        if (node.right() != null) liftNode(names, list, node.right());
        if (node.op() == Any) liftAny(names, list, node);
        if (node.op() == Some) liftSome(names, list, node);
    }

    // Lift x = ...p*... to x1 = (p x1)?. Nodes are shared, so no longer form a
    // tree. The source description of the x rule is unchanged.
    private void liftAny(Set<String> names, Node list, Node loop) {
        String x = list.left().left().name();
        String x1 = findName(names, x);
        Node p = loop.left();
        int s = p.start(), e = p.end();
        String pText = p.source().substring(s,e);
        String text = x1 + " = (" + pText + " " + x1 + ")?";
        int n0 = 0, n1 = x1.length(), n2 = n1 + 3, n3 = n2 + 1;
        int n4 = n3 + pText.length(), n5 = n4 + 1 + x1.length(), n6 = n5 + 2;
        Source src = new Source(text);
        Node id = new Node(Id, src, n0, n1);
        id.flags(loop.flags());
        Node p1 = p.copy(p.op(), src, n3, n4);
        Node and = new Node(And, p1, id, src, n3, n5);
        Node opt = new Node(Opt, and, src, n2, n6);
        Node newRule = new Node(Rule, id, opt, src, n0, n6);
        loop.op(Id);
        loop.source(src);
        loop.start(n0);
        loop.end(n1);
        loop.right(null);
        loop.left(null);
        loop.ref(newRule);
        Node oldList = new Node(
            List, list.left(), list.right(),
            list.source(), list.start(), list.end()
        );
        list.left(newRule);
        list.right(oldList);
    }

    // Lift x = ...p+... to x1 = p x1?. Nodes are shared, so no longer form a
    // tree. The source description of the x rule is unchanged.
    private void liftSome(Set<String> names, Node list, Node loop) {
        String x = list.left().left().name();
        String x1 = findName(names, x);
        Node p = loop.left();
        int s = p.start(), e = p.end();
        String pText = p.source().substring(s,e);
        String text = x1 + " = (" + pText + ") " + x1 + "?";
        Source src = new Source(text);
        Node id = new Node(Id, src, 0, x1.length());
        int optS = text.length() - 1 - x1.length(), optE = text.length();
        Node opt = new Node(Opt, id, src, optS, optE);
        int andS = x1.length() + 3, andE = text.length();
        Node and = new Node(And, p, opt, src, andS, andE);
        Node newRule = new Node(Rule, id, and, src, 0, text.length());
        loop.op(Id);
        loop.source(src);
        loop.start(0);
        loop.end(x1.length());
        loop.right(null);
        loop.left(null);
        loop.ref(newRule);
        Node oldList = new Node(
            List, list.left(), list.right(),
            list.source(), list.start(), list.end()
        );
        list.left(newRule);
        list.right(oldList);
    }

    // Given x, try x1, x2, ...
    private String findName(Set<String> names, String id) {
        String name = null;
        for (int i = 1; name == null; i++) {
            String s = id + i;
            if (! names.contains(s)) name = s;
        }
        names.add(name);
        return name;
    }

    // Currently only expandSee is tested.
    public static void main(String[] args) {
        Transformer trans = new Transformer();
        Source src = new Source("  x = [y] z");
        Node nx = new Node(Id, src, 2, 3);
        Node ny = new Node(Id, src, 7, 8);
        Node nz = new Node(Id, src, 10, 11);
        Node see = new Node(See, ny, src, 6, 9);
        see.set(AA);
        Node and = new Node(And, see, nz, src, 6, 11);
        Node rule = new Node(Rule, nx, and, src, 2, 11);
        trans.expandSee(see);
        assert(rule.text().equals("x = [y] z"));
        assert(rule.right().op() == And);
        assert(rule.right().left().op() == And);
        assert(rule.right().left().left().op() == Has);
        assert(rule.right().left().left().left().op() == Id);
        assert(rule.right().left().left().left().text().equals("y"));
        assert(rule.right().left().right().op() == Id);
        assert(rule.right().left().right().text().equals("y"));
    }
}
