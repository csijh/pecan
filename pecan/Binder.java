// Pecan 1.0 binder. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;
import static java.lang.Character.*;

/* Carry out binding:
Replace empty Text by Success, empty Set by Fail, empty Split by Eot.
Replace Text or Set with single character by Char.
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
    private Source source;
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
            Node node = new Node(op, null, null);
            binder.scanNode(node);
        }
        binder.switchTest = false;
        Test.run(binder, args);
    }

    // Run the binder on the given source, check type of input.
    public Node run(Source src) {
        source = src;
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
    // Check whether the parser is token-based.
    private void collect(Node node) {
        if (node.op() == Rule) {
            String id = node.left().rawText();
            if (rules.get(id) == null) rules.put(id, node);
        }
        if (node.op() == Tag) root.set(TI);
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
        case Point: case Cat: scanCat(node); break;
        case Rule: scanRule(node); break;
        case Id: scanId(node); break;
        case Text: case Char: scanText(node); break;
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
        if (root.has(TI)) err(node, "text matcher in a token parser");
    }

    // Check that a rule name is not a duplicate.
    private void scanRule(Node node) {
        if (switchTest) return;
        if (! checkEscapes(node.left())) return;
        String name = node.left().rawText();
        Node first = rules.get(name);
        if (node != first) err(node.left(), name + " is already defined");
    }

    // Bind an id to its defining rule, creating a cross-reference.
    private void scanId(Node node) {
        if (switchTest) return;
        if (! checkEscapes(node)) return;
        String name = node.rawText();
        Node rule = rules.get(name);
        if (rule == null) err(node, "undefined identifier");
        node.ref(rule);
    }

    // Set "" to Success, "x" to Char.
    private void scanText(Node node) {
        if (switchTest) return;
        if (! checkEscapes(node)) return;
        int n = node.rawText().codePointCount(0, node.rawText().length());
        if (n == 0) node.op(Success);
        else if (n == 1) node.op(Char);
        if (n > 0 && root.has(TI)) err(node, "text matcher in a token parser");
    }

    // Set <> to Eot. If non-empty, check not token input.
    private void scanSplit(Node node) {
        if (switchTest) return;
        if (! checkEscapes(node)) return;
        int n = node.rawText().length();
        if (n == 0) node.op(Eot);
        else if (root.has(TI)) err(node, "text matcher in a token parser");
    }

    // Set '' to Fail, 'x' to Char, and check distinct characters.
    private void scanSet(Node node) {
        if (switchTest) return;
        if (! checkEscapes(node)) return;
        String name = node.rawText();
        int n = name.codePointCount(0, name.length());
        if (n == 0) node.op(Fail);
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
        if (n > 0 && root.has(TI)) err(node, "text matcher in a token parser");
    }

    // Check that a range is non-empty. Check not token input.
    private void scanRange(Node node) {
        if (switchTest) return;
        if (! checkEscapes(node)) return;
        if (root.has(TI)) err(node, "text matcher in a token parser");
        int low = node.low(), high = node.high();
        if (high < low) err(node, "empty range");
    }

    // Turn @ or @2 to Drop, check consistent arities (?)
    private void scanAct(Node node) {
        if (switchTest) return;
        if (! checkEscapes(node)) return;
        String text = node.text();
        String name = node.rawText();
        if (name.length() == 0) { node.op(Drop); return; }
        int arity = node.arity();
        Integer old = arities.get(name);
        if (old == null) arities.put(name, arity);
        else if (arity != old) {
            if (old == 0) err(node, "clashes with @" + name);
            else err(node, "clashes with @" + old + name);
        }
    }

    // Check that any escapes in the text are 0..1114111
    private boolean checkEscapes(Node node) {
        Source s = node.source();
        boolean ok = true;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                int len = s.rawLength(i);
                int code = s.rawChar(i, len);
                if (code < 0 || code > 1114111) {
                    err(node, "code too big");
                    ok = false;
                }
            }
        }
        return ok;
    }

    // Report an error.
    private void err(Node r, String m) {
        root = new Node(Error, r.source());
        root.note(r.source().error(m));
    }
}
