// Pecan 1.0 binder. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;
import static java.lang.Character.*;

/* Carry out binding:
Replace empty String by Success, empty Set by Fail, empty Split by End.
Replace String or Set with single character by Char.
Replace unnamed Act by Drop.
Check for missing or duplicate definitions.
Create cross-references from ids to their definitions.
Check consistency of arities of actions.
Check that sets consists of distinct characters.
Check that ranges are non-empty.
Check that numerical character codes are in the range 0..1114111.
Check whether the grammar has text or tokens as input. */

class Binder implements Testable {
    private Source source;
    private Node root;
    private HashMap<String,Node> rules;
    private Map<String,Integer> arities;

    public static void main(String[] args) {
        if (args.length == 0) Parser.main(args);
        Test.run(new Binder(), args);
    }

    // Run the passes up to the binder on the given source text.
    public Node run(Source s) {
        source = s;
        Parser parser = new Parser();
        root = parser.run(source);
        if (root.op() == Error) return root;
        rules = new HashMap<String,Node>();
        arities = new HashMap<String,Integer>();
        collect();
        bind(root);
        return root;
    }

    // Collect the rules in a map. Ignore duplicates for now.
    private void collect() {
        Node node = root;
        while (node != null) {
            if (node.op() == List) {
                String name = node.left().left().name();
                if (rules.get(name) == null) rules.put(name, node.left());
                node = node.right();
            }
            else {
                String name = node.left().name();
                if (rules.get(name) == null) rules.put(name, node);
                node = null;
            }
        }
    }

    // Bind a grammar. Scan the nodes, check type of input.
    private void bind(Node root) {
        scan(root);
        if (root.has(TextInput) && root.has(TokenInput)) {
            err(root, "there is both text and token input");
        }
    }

    // Traverse the tree, top down, and check each node.
    private void scan(Node node) {
        switch(node.op()) {
        case And: case Or: case Opt: case Any: case Some: case Drop:
        case Has: case Not: case Try: case Mark: case End: case List:
            break;
        case Cat: bindCat(node); break;
        case Tag: bindTag(node); break;
        case Rule: bindRule(node); break;
        case Id: bindId(node); break;
        case Code: bindCode(node); break;
        case Codes: bindRange(node); break;
        case String: bindString(node); break;
        case Split: bindSplit(node); break;
        case Set: bindSet(node); break;
        case Range: bindRange(node); break;
        case Act: bindAct(node); break;
        default: throw new Error("Type " + node.op() + " not implemented");
        }
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
    }

    // Set the TextInput flag on the root node.
    private void bindCat(Node node) {
        root.set(TextInput);
    }

    // Set the TokenInput flag on the root node.
    private void bindTag(Node node) {
        root.set(TokenInput);
    }

    // Check that a rule name is not a duplicate.
    private void bindRule(Node node) {
        String name = node.name();
        Node first = rules.get(name);
        if (node != first) err(node.left(), name + " is already defined");
    }

    // Bind an id to its defining rule, creating a cross-reference.
    private void bindId(Node node) {
        String name = node.name();
        Node rule = rules.get(name);
        if (rule == null) err(node, "unknown name");
        node.ref(rule);
    }

    // Find the character code represented by a number, and check in range.
    // Set the TextInput flag on the root node.
    private void bindCode(Node node) {
        int ch = node.charCode();
        if (ch > 1114111) err(node, "code too big");
        root.set(TextInput);
    }

    // Set the TextInput flag on the root node.
    // Set "" to Success and "x" to Char.
    private void bindString(Node node) {
        int n = node.name().codePointCount(0, node.name().length());
        if (n == 0) node.op(Success);
        if (n == 1) node.op(Char);
        if (n > 0) root.set(TextInput);
    }

    // Set the TextInput flag on the root node. Set <> to End.
    private void bindSplit(Node node) {
        int n = node.name().length();
        if (n == 0) node.op(End);
        if (n > 0) root.set(TextInput);
    }

    // Check that a set consists of distinct characters.
    // Set the TextInput flag on the root node.
    private void bindSet(Node node) {
        String name = node.name();
        int n = name.codePointCount(0, name.length());
        if (n == 0) return;
        root.set(TextInput);
        if (n == 1) {
            node.op(Char);
            return;
        }
        for (int i = 0; i<name.length(); ) {
            int c1 = name.codePointAt(i);
            i += Character.charCount(c1);
            for (int j=i; j<name.length(); ) {
                int c2 = name.codePointAt(j);
                j += Character.charCount(c2);
                if (c1 != c2) continue;
                err(node, "set contains duplicate character");
            }
        }
    }

    // Check that a range is non-empty.
    // Set the TextInput flag on the root node.
    private void bindRange(Node node) {
        root.set(TextInput);
        int low = node.low(), high = node.high();
        if (high < low) err(node, "empty range");
    }

    // Check that actions with the same name have the same arities.
    private void bindAct(Node node) {
        String text = node.text();
        String name = node.name();
        if (name.length() == 0) return;
        int arity = node.arity();
        Integer old = arities.get(name);
        if (old == null) arities.put(name, arity);
        else if (arity != old) err(node, "clashes with @" + old + name);
    }

    // Report an error and stop.
    private void err(Node r, String m) {
        int s = r.start();
        int e = r.end();
        root = new Node(Error, source, 0, 0);
        root.note(source.error(s, e, m));
    }
}
