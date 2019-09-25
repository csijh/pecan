// Pecan 1.0 nodes. Free and open source. See licence.txt.

package pecan;
import java.util.*;
import static pecan.Op.*;

/* A Node represents any parsing expression, together with annotation
information which is gathered about it during the various passes.

A node has an op, a text range in the source, and up to two child nodes. If the
left node is null, the right node represents a cross-reference link instead of a
child node. For more information about annotations, see the Pecan class which
handles them. */

class Node {

    public static void main(String[] args) {
        Source s = new Source("127", "file", 1);
        Node n = new Node(Code, s, 0, 3);
        n.set(Flag.Char);
        assert(n.name().equals("127"));
        assert(n.charCode() == 127);
        s = new Source("'a'", "file", 1);
        n = new Node(Set, s, 0, 3);
        n.set(Flag.Char);
        assert(n.name().equals("a"));
        assert(n.charCode() == 97);
        s = new Source("@add", "file", 1);
        n = new Node(Act, s, 0, 4);
        assert(n.name().equals("add"));
        assert(n.arity() == 0);
        s = new Source("@2add", "file", 1);
        n = new Node(Act, s, 0, 5);
        assert(n.name().equals("add"));
        assert(n.arity() == 2);
    }

// ---------- The annotation fields and methods -------------------------------

    private int flags;
    private int NET, LOW, PC, LEN;
    private String note = "";

    // Flag constants. See the relevant analysis classes.
    public static enum Flag {
        TextInput, TokenInput, Char, SN, FN, SP, FP, WF, AA, AB, BP;
        int bit() { return 1 << ordinal(); }
    }

    // Get, set or unset a flag.
    boolean has(Flag f) { return (flags & f.bit()) != 0; }
    void set(Flag f) { flags |= f.bit(); }
    void unset(Flag f) { flags &= ~f.bit(); }

    // Get/set counts and get bitsets.
    int NET() { return NET; }
    int LOW() { return LOW; }
    void NET(int n) { NET = n; }
    void LOW(int l) { LOW = l; }

    // Get/set the note.
    String note() { return note; }
    void note(String n) { note = n; }

    // Get/set PC = address in bytecode.
    int PC() { return PC; }
    void PC(int i) { PC = i; }

    // Get/set LEN = number of bytes of bytecode.
    int LEN() { return LEN; }
    void LEN(int n) { LEN = n; }

// ---------- The structural fields and methods -------------------------------

    private Op op;
    private Source source;
    private int start, end;
    private Node left, right;

    // Construct a node.
    Node(Op o, Node r1, Node r2, Source s, int b, int e) {
        op = o; left = r1; right = r2; source = s; start = b; end = e;
    }

    // Construct a node with one subnode.
    Node(Op o, Node r, Source s, int b, int e) { this(o, r, null, s, b, e); }

    // Construct a node with no subnodes.
    Node(Op o, Source s, int b, int e) { this(o, null, null, s, b, e); }

    // Copy a node, with given children. Leave out info.
    Node copy(Node x, Node y) {
        return new Node(op, x, y, source, start, end);
    }

    // Get/set the op and range of text.
    Op op() { return op; }
    int start() { return start; }
    int end() { return end; }
    Source source() { return source; }
    String text() { return source.substring(start, end); }
    void op(Op o) { op = o; }
    void start(int s) { start = s; }
    void end(int e) { end = e; }

    // The name is the text, without decoration, i.e. #x -> x, %x -> x,
    // @2add -> add, "ab" -> ab, 'ab' -> ab, <ab> -> ab
    String name() {
        switch (op) {
        case Mark: return source.substring(start+1, end);
        case Tag: return source.substring(start+1, end);
        case Act:
            int i = start + 1;
            while (Character.isDigit(source.charAt(i))) i++;
            return source.substring(i, end);
        case String: case Set: case Split:
            return source.substring(start+1, end-1);
        }
        return text();
    }

    // For a character, extract the value, i.e. Unicode code point.
    int charCode() {
        if (! has(Flag.Char)) return -1;
        String name = name();
        int ch = -1;
        switch(op) {
        case Code:
            if (name.charAt(0) != '0') ch = Integer.parseInt(name);
            else ch = Integer.parseInt(name, 16);
            break;
        case Set: case String:
            ch = name.codePointAt(0);
            break;
        case Id: case Rule:
            ch = left().charCode();
            break;
        }
        return ch;
    }

    // For an action, extract the arity.
    int arity() {
        int n = start + 1;
        while (Character.isDigit(source.charAt(n))) n++;
        if (n == start + 1) return 0;
        return Integer.parseInt(source.substring(start + 1, n));
    }

    // Get/set the children and the cross-reference link. A cross reference link
    // is recognized as a right child without a left child.
    Node left() { return left; }
    Node right() { return left == null ? null : right; }
    Node ref() { return left != null ? null : right; }
    void left(Node x) {
        if (x == null && right != null) throw new Error("right without left");
        left = x;
    }
    void right(Node y) {
        if (y != null && left == null) throw new Error("right without left");
        right = y;
    }
    void ref(Node r) {
        if (r != null && left != null) throw new Error("ref with left");
        right = r;
    }

    // Return a node tree as multi-line text. Then clear the notes, ready
    // for the next pass.
    public String toString() {
        String s = toString("") + "\n";
        clear();
        return s;
    }

    // Clear the notes.
    private void clear() {
        note("");
        if (left != null) left.clear();
        if (left != null && right != null) right.clear();
    }

    // Return a node tree as text, with the given indent for each line. Avoid
    // following cross references. Include the range of text covered by the
    // node, and any note attached by one of the passes. Indent a list of Rule
    // nodes, or subnodes of a chain of And or Or nodes, by the same amount.
    private String toString(String indent) {
        if (op == Error) return note();
        String s = indent + op + " ";
        String text = text();
        if (! text.contains("\n")) s += text;
        else {
            int n = text.indexOf('\n');
            s += text.substring(0, n) + "...";
        }
        if (note().length() > 0) s += " " + note();
        indent += "  ";
        if (left != null && (op == List || op == And || op == Or)) {
            s += "\n" + left.toString(indent);
            Node x = right;
            while (x.op == op) {
                s += "\n" + x.left.toString(indent);
                x = x.right;
            }
            s += "\n" + x.toString(indent);
            return s;
        }
        if (left != null) s += "\n" + left.toString(indent);
        if (left != null && right != null) s += "\n" + right.toString(indent);
        return s;
    }

    // Print the source text of a node on one line, e.g. when tracing.
    String trace() {
        int firstLine = source.lineNumber(start);
        int lastLine = source.lineNumber(end);
        if (lastLine == firstLine) {
            return "P" + firstLine + ": " + source.substring(start, end);
        }
        String s = "P" + firstLine + "-" + lastLine + ": ";
        int pos = start + 10;
        int newline = source.indexOf('\n', start);
        if (newline >= 0 && newline < pos) pos = newline;
        s += source.substring(start, pos);
        s += "...";
        pos = end - 10;
        newline = source.indexOf('\n', pos);
        if (newline >= 0 && newline < end) pos = newline + 1;
        s += source.substring(pos, end);
        return s;
    }
}
