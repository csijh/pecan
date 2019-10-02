// Pecan 1.0 transformer. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Provide a collection of transform methods to be used particularly for
compiling to a target language or to bytecode. */

// TODO: liftSome
// TODO: replace string in rule (assuming all nodes same source)

class Transformer {

    // Replace [x] by (x& x) if x contains actions or markers. Assume x is small
    // enough to be repeated twice, rather than making a separate rule for it.
    // The node for x is shared, so the nodes no longer for a true tree. The
    // source description of the changed nodes is not accurate. The source
    // description of the rule containing the nodes isn't changed.
    void expandTry(Node node) {
        if (node.left() != null) expandTry(node.left());
        if (node.right() != null) expandTry(node.right());
        if (node.op() == Try && (node.has(AA) || node.has(EE))) {
            Node x = node.left();
            node.op(And);
            node.left(new Node(Has, x, x.source(), x.start(), x.end()));
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
        System.out.println("LN " + node.op());
        if (node.left() != null) liftNode(names, list, node.left());
        if (node.right() != null) liftNode(names, list, node.right());
        if (node.op() == Any) liftAny(names, list, node);
//        if (node.op() == Some) liftSome(names, list, node);
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
        Source src = new Source(text);
        Node id = new Node(Id, src, 0, x1.length());
        int andS = x1.length() + 4, andE = andS + pText.length();
        Node and = new Node(And, p, id, src, andS, andE);
        Node opt = new Node(Opt, and, src, andS-1, text.length());
        Node newRule = new Node(Rule, id, opt, src, 0, text.length());
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

    // Currently no testing.
    public static void main(String[] args) {
    }
}
