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
For actions, set the value to the arity, and check consistency of the arities.
Check that a set consists of distinct characters of the same UTF8 length.
For matchers which represent single characters, set the value to the code.
Check that numerical character codes are in the range 0..1114111.
Check that both ends of a range are single characters.
Check that a range is non-empty.
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
        classify(root);
        return root;
    }

    // Traverse the tree, bottom up, and check each node.
    private void scan(Node node) throws Exception {
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
        switch(node.op()) {
        case And: case Or: case Opt: case Many: case Some: case Drop:
        case Has: case Not: case Try: case Mark: case Tag: case Cat:
            break;
        case Rule: bindRule(node); break;
        case Id: bindId(node); break;
        case Char: bindChar(node); break;
        case String: bindString(node); break;
        case Divider: bindDivider(node); break;
        case Set: bindSet(node); break;
        case Range: bindRange(node); break;
        case Act: bindAct(node); break;
        default: throw new Error("Type " + node.op() + " not implemented");
        }
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
        if (cats.contains(name)) node.op(Cat);
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
    private void bindChar(Node node) throws Exception {
        int ch;
        if (node.text().charAt(0) != '0') ch = Integer.parseInt(node.text());
        else ch = Integer.parseInt(node.text(), 16);
        if (ch > 1114111) err(node, "number too big");
        node.value(ch);
        node.note("" + ch);
    }

    // Check whether a string is a character.
    private void bindString(Node node) {
        node.value(-1);
        int n = node.text().codePointCount(1, node.text().length() - 1);
        if (n != 1) return;
        node.value(node.text().codePointAt(1));
        node.note("" + node.value());
    }

    // Check whether a divider is a character.
    private void bindDivider(Node node) {
        bindString(node);
    }

    // Check that a set consists of distinct characters of the same UTF8 length.
    // Check whether it is a single character.
    private void bindSet(Node node) throws Exception {
        String chars = node.text();
        chars = chars.substring(1, chars.length() - 1);
        int len = 0;
        for (int i=0; i<chars.length(); ) {
            int c1 = chars.codePointAt(i);
            int c1n = Character.charCount(c1);
            if (len == 0) len = c1n;
            else if (c1n != len) err(node, "set not UTF8 balanced");
            i += c1n;
            for (int j=i; j<chars.length(); ) {
                int c2 = chars.codePointAt(j);
                j += Character.charCount(c2);
                if (c1 != c2) continue;
                err(node, "set contains duplicate character");
            }
        }
        node.value(-1);
        int n = node.text().codePointCount(1, node.text().length() - 1);
        if (n != 1) return;
        node.value(node.text().codePointAt(1));
        node.note("" + node.value());
    }

    // Check that a range has a single character at each end and is non-empty.
    private void bindRange(Node node) throws Exception {
        int from = node.left().value();
        int to = node.right().value();
        if (from < 0) err(node.left(), "expecting single character");
        if (to < 0) err(node.right(), "expecting single character");
        if (to < from) err(node, "empty range");
    }

    // Check that actions with the same name have the same arities.
    // Set the value of an action node to its arity.
    private void bindAct(Node node) throws Exception {
        String text = node.text();
        String name = node.name();
        int p = 1;
        while (Character.isDigit(text.charAt(p))) p++;
        int arity = 0;
        if (p > 1) arity = Integer.parseInt(text.substring(1, p));
        node.value(arity);
        Integer old = arities.get(name);
        if (old == null) arities.put(name, arity);
        else if (arity != old) err(node, "clashes with @" + old + name);
    }

    // Calculate the TextInput and TokenInput flags.
    private void classify(Node node)  throws Exception {
        boolean xTxt = false, yTxt = false, xTok = false, yTok = false;
        Node x = node.left(), y = node.right();
        if (x != null) {
            classify(x);
            xTxt = x.has(TextInput);
            xTok = x.has(TokenInput);
        }
        if (y != null) {
            classify(y);
            yTxt = y.has(TextInput);
            yTok = y.has(TokenInput);
        }
        if (xTxt || yTxt) node.set(TextInput);
        if (xTok || yTok) node.set(TokenInput);
        switch (node.op()) {
        case Id:
            if (node.ref().has(TextInput)) node.set(TextInput);
            if (node.ref().has(TokenInput)) node.set(TokenInput);
            break;
        case Tag:
            node.set(TokenInput);
            break;
        case Char: case Range: case Cat:
            node.set(TextInput);
            break;
        case String: case Divider:
            if (! node.text().equals("\"\"")) node.set(TextInput);
            break;
        case Set:
            if (! node.text().equals("''")) node.set(TextInput);
            break;
        default: break;
        }
        if (node.has(TextInput) && node.has(TokenInput)) {
            err(node, "there is both text and token input");
        }
        if (node.op() == Rule && node.value() == 0) {
            if (! node.has(TokenInput)) node.set(TextInput);
        }
    }

    // Report an error and stop.
    private void err(Node r, String m) throws Exception {
        int s = r.start();
        int e = r.end();
        throw new Exception(Node.err(source, s, e, m));
    }
}
