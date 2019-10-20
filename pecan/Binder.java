// Pecan 1.0 binder. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;
import static java.lang.Character.*;

/* Carry out binding:
Replace empty String by Success, empty Set by Fail, empty Split by Eot.
Replace String or Set by Id in a token-based parser
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
    private boolean switchTest;
    private Node root;
    private HashMap<String,Node> rules = new HashMap<String,Node>();
    private Map<String,Integer> arities = new HashMap<String,Integer>();

    // Do unit testing on the Parser class, then check the switch is complete,
    // then run the Binder unit tests.
    public static void main(String[] args) {
        if (args.length == 0) Parser.main(args);
        Binder binder = new Binder();
        binder.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, 0, 1);
            binder.scanNode(node);
        }
        binder.switchTest = false;
        Test.run(binder, args);
    }

    // Run the binder on the given source, check type of input.
    public Node run(Source source) {
        Parser parser = new Parser();
        root = parser.run(source);
        if (root.op() == Error) return root;
        rules.clear();
        arities.clear();
        collect(root);
        scan(root);
        return root;
    }

    // Collect information. Put the rules in a map, ignoring duplicates for now.
    // Check whether the parser is token-based or text-based.
    private void collect(Node node) {
        if (node.op() == Rule) {
            String id = node.left().name();
            if (rules.get(id) == null) rules.put(id, node);
        }
        if (node.op() == Tag) root.set(TokenInput);
        if (node.left() != null) collect(node.left());
        if (node.right() != null) collect(node.right());
    }

    // Traverse the tree, top down, and check each node.
    private void scan(Node node) {
        scanNode(node);
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
    }

    // The main switch.
    private void scanNode(Node node) {
        switch(node.op()) {
        case Error: case Temp: case List: case Empty: case Eot: break;
        case And: case Or: case Opt: case Any: case Some: case Drop: break;
        case Has: case Not: case See: case Mark: case Tag: break;
        case Success: case Fail: break;
        case Cat: scanCat(node); break;
        case Rule: scanRule(node); break;
        case Id: scanId(node); break;
        case Code: scanCode(node); break;
        case Codes: scanRange(node); break;
        case String: case Char: scanString(node); break;
        case Split: scanSplit(node); break;
        case Set: scanSet(node); break;
        case Range: scanRange(node); break;
        case Act: scanAct(node); break;
        default: assert false : "Unexpected node type " + node.op(); break;
        }
    }

    // Check not token input.
    private void scanCat(Node node) {
        if (switchTest) return;
        if (root.has(TokenInput)) err(node, "text matcher in a token parser");
    }

    // Check that a rule name is not a duplicate.
    private void scanRule(Node node) {
        if (switchTest) return;
        String name = node.left().name();
        Node first = rules.get(name);
        if (node != first) err(node.left(), name + " is already defined");
    }

    // Bind an id to its defining rule, creating a cross-reference.
    private void scanId(Node node) {
        if (switchTest) return;
        String name = node.name();
        Node rule = rules.get(name);
        if (rule == null) err(node, "undefined identifier");
        node.ref(rule);
    }

    // Find the character code represented by a number, and check in range.
    // Check not token input.
    private void scanCode(Node node) {
        if (switchTest) return;
        int ch = node.charCode();
        if (ch > 1114111) err(node, "code too big");
        if (root.has(TokenInput)) err(node, "text matcher in a token parser");
    }

    // Set "" to Success, "->" to Id if token input, "x" to Char.
    private void scanString(Node node) {
        if (switchTest) return;
        int n = node.name().codePointCount(0, node.name().length());
        if (n == 0) node.op(Success);
        else if (root.has(TokenInput)) { node.op(Id); scanId(node); }
        else if (n == 1) node.op(Char);
    }

    // Set <> to Eot. If non-empty, check not token input.
    private void scanSplit(Node node) {
        if (switchTest) return;
        int n = node.name().length();
        if (n == 0) { node.op(Eot); return; }
        if (root.has(TokenInput)) err(node, "text matcher in a token parser");
    }

    // Set '' to Fail, '->' to id if token input, 'x' to Char, and
    // check that the set consists of distinct characters.
    private void scanSet(Node node) {
        if (switchTest) return;
        String name = node.name();
        int n = name.codePointCount(0, name.length());
        if (n == 0) node.op(Fail);
        else if (root.has(TokenInput)) { node.op(Id); scanId(node); }
        else if (n == 1) node.op(Char);
        else for (int i = 0; i<name.length(); ) {
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

    // Check that a range is non-empty. Check not token input.
    private void scanRange(Node node) {
        if (switchTest) return;
        if (root.has(TokenInput)) err(node, "text matcher in a token parser");
        int low = node.low(), high = node.high();
        if (high < low) err(node, "empty range");
    }

    // Check that actions with the same name have the same arities?
    private void scanAct(Node node) {
        if (switchTest) return;
        String text = node.text();
        String name = node.name();
        if (name.length() == 0) { node.op(Drop); return; }
        int arity = node.arity();
        Integer old = arities.get(name);
        if (old == null) arities.put(name, arity);
        else if (arity != old) {
            if (old == 0) err(node, "clashes with @" + name);
            else err(node, "clashes with @" + old + name);
        }
    }

    // Report an error.
    private void err(Node r, String m) {
        int s = r.start();
        int e = r.end();
        root = new Node(Error, r.source(), 0, 0);
        root.note(r.source().error(s, e, m));
    }
}
