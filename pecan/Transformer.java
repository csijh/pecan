// Pecan 1.0 transformer. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Provide a collection of transform methods, to be used before compiling to a
target language or to bytecode. After trandforming, the ckecker may need to be
run again to generate the right analysis flags.

The source text of nodes is only preserved to the extent that (a) a rule node
has text which serves as a comment for its compiled function and (b) atomic
nodes still have the right text so that they are compiled correctly. */

// TODO: replace string in rule (assuming all nodes same source)

class Transformer {

    // Replace [x] by (x& x) if x contains actions or markers. Assume x is small
    // enough to be repeated twice, rather than making a separate rule for it.
    void expandSee(Node node) {
        if (node.left() != null) expandSee(node.left());
        if (node.right() != null) expandSee(node.right());
        if (node.op() == See && (node.has(AA) || node.has(EE))) {
            Node x = node.left();
            Node hasX = new Node(Has, x, x.source());
            node.op(And);
            node.left(hasX);
            node.right(x.deepCopy());
        }
    }

    // Lift out loops into separate rules. Gather the names of the ids, so that
    // new names can be added without clashes. Deal with each rule in turn,
    // passing the list node so that new rules can be inserted.
    void lift(Node root) {
        Node node = root;
        Set<String> names = new HashSet<>();
        while (node.op() == List) {
            names.add(node.left().left().rawText());
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
        String x = list.left().left().rawText();
        String x1 = findName(names, x);
        Node id = new Node(Id, x1);
        Node p = loop.left();
        Node and;
        if (p.op() == Or) and = new Node(And, "(", p, ") ", id, "");
        else and = new Node(And, "", p, " ", id, "");
        Node opt = new Node(Opt, "(", and, ")?");
        Node id2 = id.deepCopy();
        Node newRule = new Node(Rule, "", id2, " = ", opt, "");
        id.ref(newRule);
        id2.ref(newRule);
        loop.op(Id);
        loop.source(new Source(x1));
        loop.right(null);
        loop.left(null);
        loop.ref(newRule);
        Node list2 = new Node(List, newRule, list.right(), list.source());
        list.right(list2);
    }

    // Lift x = ...p+... to x1 = p x1?. Nodes are shared, so no longer form a
    // tree. The source description of the x rule is unchanged.
    private void liftSome(Set<String> names, Node list, Node loop) {
        String x = list.left().left().rawText();
        String x1 = findName(names, x);
        Node id = new Node(Id, x1);
        Node p = loop.left();
        Node opt = new Node(Opt, "", id, "?");
        Node and;
        if (p.op() == Or) and = new Node(And, "(", p, ") ", opt, "");
        else and = new Node(And, "", p, " ", opt, "");
        Node id2 = id.deepCopy();
        Node newRule = new Node(Rule, "", id2, " = ", and, "");
        id.ref(newRule);
        id2.ref(newRule);
        loop.op(Id);
        loop.source(new Source(x1));
        loop.right(null);
        loop.left(null);
        loop.ref(newRule);
        Node list2 = new Node(List, newRule, list.right(), list.source());
        list.right(list2);
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
        Node nx = new Node(Id, "x");
        Node ny = new Node(Id, "y");
        Node nz = new Node(Id, "z");
        Node see = new Node(See, "[", ny, "]");
        see.set(AA);
        Node and = new Node(And, "", see, " ", nz, "");
        Node rule = new Node(Rule, "", nx, " = ", and, "");
        trans.expandSee(see);
        assert(rule.text().equals("x = [y] z"));
        assert(rule.right().op() == And);
        assert(rule.right().left().op() == And);
        assert(rule.right().left().left().op() == Has);
        assert(rule.right().left().left().left().op() == Id);
        assert(rule.right().left().left().left().text().equals("y"));
        assert(rule.right().left().right().op() == Id);
        assert(rule.right().left().right().text().equals("y"));
        System.out.println("Transformer class OK");
    }
}
