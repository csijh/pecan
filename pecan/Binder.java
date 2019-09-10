// Pecan 1.0 binder. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;
import static java.lang.Character.*;

/* Carry out binding:
Check for missing or duplicate definitions, or definitions of category names.
Create cross-references from ids to their definitions, recognise category names.
Set the value of actions to their arities, and check consistency of arities.
Check that a set consists of distinct characters.
Recognize ranges 'a..z' and create string subnodes.
Check that both ends of ranges are single characters, and ranges are non-empty.
For nodes which represent characters numerically, set the value to the code.
Check that numerical character codes are in the range 0..1114111.
Check whether the grammar has text or tokens as input. */

class Binder implements Testable {
    private String source;
    private Node root;
    private Set<String> cats;
    private HashMap<String,Node> rules;
    private Map<String,Integer> arities;

    public static void main(String[] args) {
        if (args.length == 0) Parser.main(args);
        if (args.length == 0) Test.run(new Binder());
        else Test.run(new Parser(), Integer.parseInt(args[0]));
    }

    public String test(String g) {
        return "" + run(g);
    }

    // Run the passes up to the binder on the given source text.
    Node run(String s) {
        source = s;
        Parser parser = new Parser();
        root = parser.run(source);
        if (root.op() == Error) return root;
        try { return bind(root); }
        catch (Exception e) {
            Node err = new Node(Error, s, 0, 0);
            err.note(e.getMessage());
            return err;
        }
    }

    // Bind a grammar. Gather the categories, scan the nodes, classify.
    private Node bind(Node root) throws Exception {
        cats = new HashSet<String>();
        for (Category cat : Category.values()) cats.add(cat.toString());
        rules = new HashMap<String,Node>();
        arities = new HashMap<String,Integer>();
        scan(root);
        if (root.has(TextInput) && root.has(TokenInput)) {
            err(root, "there is both text and token input");
        }
        return root;
    }

    // Traverse the tree, bottom up, and check each node.
    private void scan(Node node) throws Exception {
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
        switch(node.op()) {
        case And: case Or: case Opt: case Any: case Some: case Drop:
        case Has: case Not: case Try: case Mark: case End:
            break;
        case Cat: bindCat(node); break;
        case Tag: bindTag(node); break;
        case Rule: bindRule(node); break;
        case Id: bindId(node); break;
        case Number: bindNumber(node); break;
        case String: bindString(node); break;
        case Divider: bindDivider(node); break;
        case Set: bindSet(node); break;
        case Range: bindRange(node); break;
        case Act: bindAct(node); break;
        default: throw new Error("Type " + node.op() + " not implemented");
        }
    }

    // Set the TokenInput flag on the root node.
    private void bindCat(Node node) {
        root.set(TokenInput);
    }

    // Set the TokenInput flag on the root node.
    private void bindTag(Node node) {
        root.set(TokenInput);
    }

    // Check that a rule name is not a unicode id or duplicate.
    private void bindRule(Node node) throws Exception {
        String name = node.name();
        if (cats.contains(name)) err(node, name + " is a unicode id");
        Node dup = rules.get(name);
        if (dup != null) err(dup, name + " is already defined");
        rules.put(name, node);
    }

    // Convert ids which are category names into category nodes.
    // Bind an id to its defining rule, creating a cross-reference.
    private void bindId(Node node) throws Exception {
        String name = node.text();
        if (cats.contains(name)) {
            node.op(Cat);
            root.set(TextInput);
        }
        else {
            Node rule;
            for (rule = root; rule != null; rule = rule.right()) {
                if (name.equals(rule.name())) break;
            }
            if (rule == null) err(node, "unknown name");
            node.ref(rule);
        }
    }

    // Find the character code represented by a number, and check in range.
    // Set the TextInput flag on the root node.
    private void bindNumber(Node node) throws Exception {
        node.set(Char);
        int ch = node.charCode();
        if (ch > 1114111) err(node, "number too big");
        root.set(TextInput);
    }

    // Set the TextInput flag on the root node.
    // For a single character, set the value to the character code (for ranges).
    private void bindString(Node node) throws Exception {
        int n = node.name().codePointCount(0, node.name().length());
        if (n != 0) root.set(TextInput);
        if (n == 1) node.set(Char);
    }

    // Set the TextInput flag on the root node.
    private void bindDivider(Node node) throws Exception {
        int n = node.name().length();
        if (n != 0) root.set(TextInput);
    }

    // Check whether a set is a single character.
    // Recognize ranges 'a..z' and create subnodes.
    // Check that a set consists of distinct characters.
    // Set the TextInput flag on the root node.
    private void bindSet(Node node) throws Exception {
        String name = node.name();
        int n = name.codePointCount(0, name.length());
        if (n == 0) return;
        root.set(TextInput);
        if (n == 1) {
            node.set(Char);
            return;
        }
        int dots = name.indexOf("..");
        if (name.startsWith("...")) dots = 1;
        if (dots >= 0) {
            if (n != 4 || dots == 0 || dots == name.length() - 2) {
                err(node, "range must have one character at each end");
            }
            node.op(Range);
            int s = node.start();
            int e = node.end();
            node.left(new Node(Set, source, s, s + 1 + dots + 1));
            node.right(new Node(Set, source, s + 1 + dots + 1, e));
            node.left().set(Char);
            node.right().set(Char);
            int from = node.left().charCode();
            int to = node.right().charCode();
            if (to < from) err(node, "empty range");
            return;
        }
        int nb = bytes(node.name()).length;
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
    private void bindRange(Node node) throws Exception {
        root.set(TextInput);
        if (node.left().op() != Number) err(node, "Bad range");
        node.left().set(Char);
        if (node.right().op() != Number) err(node, "Bad range");
        node.right().set(Char);
        int from = node.left().charCode();
        int to = node.right().charCode();
        if (to < from) err(node, "empty range");
    }

    // Check that actions with the same name have the same arities.
    // Set the value of an action node to its arity.
    private void bindAct(Node node) throws Exception {
        String text = node.text();
        String name = node.name();
        int arity = node.arity();
        Integer old = arities.get(name);
        if (old == null) arities.put(name, arity);
        else if (arity != old) err(node, "clashes with @" + old + name);
    }

    // Convert string to UTF8 byte array
    private byte[] bytes(String s) {
        try { return s.getBytes("UTF8"); }
        catch (Exception e) { throw new Error(e); }
    }

    // Report an error and stop.
    private void err(Node r, String m) throws Exception {
        int s = r.start();
        int e = r.end();
        throw new Exception(Node.err(source, s, e, m));
    }
}
