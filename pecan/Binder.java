// Pecan 5 binder. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.text.*;
import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;
import static java.lang.Character.*;

/* Carry out binding:
Create cross-references from ids to their definitions.
Recognise unicode category names.
Check for missing or duplicate definitions.
For tags/markers/actions, set the value to a unique sequence number.
Check consistency of action arities, and add a node for the name and arity.
Check that a set consists of distinct ASCII characters.
For matchers which represent single characters, set the value to the code.
Check that numerical character codes are in the range 0..1114111.
Check that both ends of a range are single characters.
Check that a range is non-empty.
Check whether the grammar has text or tokens as input. */

class Binder implements Testable {
    private String source;
    private Set<String> cats;
    private Map<String,Node> rules;
    private Map<String,Integer> tags, markers, actions;

    public static void main(String[] args) {
        if (args.length == 0) Parser.main(args);
        if (args.length == 0) Test.run(new Binder());
        else Test.run(new Parser(), Integer.parseInt(args[0]));
    }

    public String test(String g, String s) throws ParseException {
        return "" + run(g);
    }

    // Run the passes up to the binder on the given source text.
    Node run(String s) throws ParseException {
        source = s;
        Parser parser = new Parser();
        Node root = parser.run(source);
        return bind(root);
    }

    // Bind a grammar. Gather the names, then allocate, then scan the nodes.
    Node bind(Node root) throws ParseException {
        cats = new HashSet<String>();
        for (Category cat : Category.values()) cats.add(cat.toString());
        rules = new LinkedHashMap<String,Node>();
        tags = new LinkedHashMap<String,Integer>();
        markers = new TreeMap<String,Integer>();
        actions = new LinkedHashMap<String,Integer>();
        gather(root);
        allocate();
        scan(root);
        classify(root);
        return root;
    }

    // Gather all rule, tag, marker and action names, top down.
    // Check rule names for unicode ids or duplicates.
    // For rules, set the value to the sequence number.
    // Convert ids which are category names into category nodes.
    // For actions, temporarily record the arity of each name.
    // For actions, create an Id node representing the name and arity.
    // Then for a duplicate action, check that the arity matches.
    private void gather(Node node) throws ParseException {
        String name;
        boolean defined;
        switch (node.op()) {
        case Rule:
            name = node.text();
            if (cats.contains(name)) err(node, name + " is a unicode id");
            defined = rules.get(name) != null;
            if (defined) err(node, name + " is already defined");
            node.value(rules.size());
            rules.put(name, node);
            break;
        case Tag:
            name = node.text().substring(1);
            defined = tags.get(name) != null;
            if (! defined) tags.put(name, 0);
            break;
        case Id:
            name = node.text();
            if (cats.contains(name)) node.op(Cat);
            break;
        case Mark:
            name = node.text().substring(1);
            defined = markers.get(name) != null;
            if (! defined) markers.put(name, 0);
            break;
        case Act:
            name = node.text();
            int p = 1;
            while (Character.isDigit(name.charAt(p))) p++;
            int arity = 0;
            if (p > 1) arity = Integer.parseInt(name.substring(1, p));
            name = name.substring(p);
            int s = node.start();
            node.ref(new Node(Id, source, s+p, s+p + name.length()));
            node.ref().value(arity);
            defined = actions.get(name) != null;
            if (! defined) actions.put(name, arity);
            else {
                int old = actions.get(name);
                if (arity != old) err(node, "clashes with @" + old + name);
            }
            break;
        default:
            break;
        }
        if (node.left() != null) gather(node.left());
        if (node.left() != null && node.right() != null) gather(node.right());
    }

    // Allocate sequence numbers to tags, markers and actions.
    // Sort actions by arity first.
    private void allocate() {
        int seq = 0;
        for (String name : tags.keySet()) tags.put(name, seq++);
        seq = 0;
        for (String name : markers.keySet()) markers.put(name, seq++);
        seq = 0;
        String[] acts = new String[actions.size()];
        actions.keySet().toArray(acts);
        for (int i=1; i<acts.length; i++) {
            String s = acts[i];
            int a = actions.get(s);
            int j=i;
            for (; j>0 && actions.get(acts[j-1]) > a; j--) acts[j] = acts[j-1];
            acts[j] = s;
        }
        for (String s : acts) actions.put(s, seq++);
    }

    // Traverse the tree, bottom up, and check each node.
    // For tags, markers and actions, set value to sequence number.
    private void scan(Node node) throws ParseException {
        if (node.left() != null) scan(node.left());
        if (node.right() != null) scan(node.right());
        switch(node.op()) {
        case Rule: case And: case Or: case Opt: case Many:
        case Some: case Drop: case Has: case Not: case Try: break;
        case Id: bindId(node); break;
        case Char: bindChar(node); break;
        case String: bindString(node); break;
        case Set: bindSet(node); break;
        case Range: bindRange(node); break;
        case Cat: bindCat(node); break;
        case Mark: bindMark(node); break;
        case Act: bindAct(node); break;
        case Tag: bindTag(node); break;
        default: throw new Error("Type " + node.op() + " not implemented");
        }
    }

    // Calculate the TextInput and TokenInput flags.
    void classify(Node node)  throws ParseException {
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
        case String:
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

    // Bind an id to its defining rule, creating a cross-reference.
    private void bindId(Node node) throws ParseException {
        String name = node.text();
        Node rule = rules.get(name);
        if (rule == null) err(node, "unknown name");
        node.ref(rule);
    }

    // Find the character code represented by a number and check in range.
    private void bindChar(Node node) throws ParseException {
        int ch;
        if (node.text().charAt(0) != '0') ch = Integer.parseInt(node.text());
        else ch = Integer.parseInt(node.text(), 16);
        if (ch > 1114111) err(node, "number too big");
        node.value(ch);
        node.note("" + ch);
    }

    // Check whether a string is a character.
    private void bindString(Node node) {
        node.value(1);
        int n = node.text().codePointCount(1, node.text().length() - 1);
        if (n != 1) return;
        node.value(node.text().codePointAt(1));
        node.note("" + node.value());
    }

    // Check that a set has distinct ASCII characters.
    // Check whether it is a single character.
    private void bindSet(Node node) throws ParseException {
        String chars = node.text();
        chars = chars.substring(1, chars.length() - 1);
        for (int i=0; i<chars.length(); ) {
            int c1 = chars.codePointAt(i);
            if (c1 >= 128) err(node, "set contains non-ascii character");
            i += Character.charCount(c1);
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
    private void bindRange(Node node) throws ParseException {
        int from = node.left().value();
        int to = node.right().value();
        if (from < 0) err(node.left(), "expecting single character");
        if (to < 0) err(node.right(), "expecting single character");
        if (to < from) err(node, "empty range");
    }

    // Bind a category: the value is a bit or bits.
    private void bindCat(Node node) throws ParseException {
        Category cat = Category.valueOf(node.text());
        node.value((cat == Category.Uc) ? 0xEFFFFFFF : (1 << cat.ordinal()));
        node.note("" + node.value());
    }

    // Bind a marker.
    private void bindMark(Node node) throws ParseException {
        node.value(markers.get(node.text().substring(1)));
        node.note("" + node.value());
    }

    // Bind a tag.
    private void bindTag(Node node) throws ParseException {
        node.value(tags.get(node.text().substring(1)));
        node.note("" + node.value());
    }

    // Bind an action
    private void bindAct(Node node) throws ParseException {
        String name = node.ref().text();
        node.value(actions.get(name));
        node.note("" + node.value());
    }

    // Report an error and stop.
    private void err(Node r, String m) throws ParseException {
        int s = r.start();
        int e = r.end();
        throw new ParseException(Node.err(source, s, e, m), 0);
    }
}
