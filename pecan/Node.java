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
        Source s = new Source("127 'a' @add @2add 'a..z' 0..127", "file");
        Node n = new Node(Code, s, 0, 3);
        assert(n.name().equals("127"));
        assert(n.charCode() == 127);
        n = new Node(Char, s, 4, 7);
        assert(n.name().equals("a"));
        assert(n.charCode() == 97);
        n = new Node(Act, s, 8, 12);
        assert(n.name().equals("add"));
        assert(n.arity() == 0);
        n = new Node(Act, s, 13, 18);
        assert(n.name().equals("add"));
        assert(n.arity() == 2);
        n = new Node(Range, s, 19, 25);
        assert(n.low() == 97);
        assert(n.high() == 97+25);
        n = new Node(Codes, s, 26, 32);
        assert(n.low() == 0);
        assert(n.high() == 127);
    }

// ---------- The annotation fields and methods -------------------------------

    private int flags;
    private int NET = Integer.MIN_VALUE, NEED, PC, LEN;
    private String note = "";

    // Flag constants (char input, token input, success or fail without or with
    // progress, well formed, has actions, ends with errors, has actions at the
    // beginning). See the relevant analysis classes.
    public static enum Flag {
        Changed, CI, TI, SN, FN, SP, FP, WF, AA, EE, AB;
        int bit() { return 1 << ordinal(); }
    }

    // Get, set or unset a flag. Set Changed if any other flag changes.
    boolean has(Flag f) {
        return (flags & f.bit()) != 0;
    }
    void set(Flag f) {
        if (! has(f)) flags = flags | Flag.Changed.bit() | f.bit();
    }
    void unset(Flag f) {
        if (! has(f)) return;
        flags = flags & ~f.bit();
        if (f != Flag.Changed) flags = flags | Flag.Changed.bit();
    }

    // Get/set counts and get bitsets. Set changed as appropriate.
    int NET() { return NET; }
    int NEED() { return NEED; }
    void NET(int n) { NET = n; }
    void NEED(int n) { NEED = n; }

    // Get/set the note.
    String note() { return note; }
    void note(String n) { note = n; }

    // Get/set PC = address in bytecode.
    int PC() { return PC; }
    void PC(int i) { PC = i; }

    // Get/set LEN = number of bytes of bytecode or characters of compiled code.
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
    void source(Source s) { source = s; }

    // The name is the text, without decoration, i.e. x = e -> x, #x -> x,
    // %x -> x, @2add -> add, "ab" -> ab, 'ab' -> ab, <ab> -> ab, 'a..z' -> a..z
    String name() {
        switch (op) {
        case Rule: return left().name();
        case Mark: return source.substring(start+1, end);
        case Tag: return source.substring(start+1, end);
        case Act:
            int i = start + 1;
            while (Character.isDigit(source.charAt(i))) i++;
            return source.substring(i, end);
        case String: case Set: case Split: case Char: case Range: case Temp:
            return source.substring(start+1, end-1);
        }
        return text();
    }

    // For a character, extract the value, i.e. Unicode code point.
    int charCode() {
        switch(op) {
        case Char:
            return name().codePointAt(0);
        case Code:
            if (name().charAt(0) != '0') return Integer.parseInt(name());
            else return Integer.parseInt(name(), 16);
        case Id:
            return ref().charCode();
        case Rule:
            return left().ref().charCode();
        }
        return -1;
    }

    // For a range, extract the low end.
    int low() {
        switch(op) {
        case Range:
            return name().codePointAt(0);
        case Codes:
            return Integer.parseInt(text().substring(0,text().indexOf('.')));
        }
        return -1;
    }

    // For a range, extract the high end.
    int high() {
        switch(op) {
        case Range:
            int n = Character.charCount(name().codePointAt(0));
            return name().substring(n+2).codePointAt(0);
        case Codes:
            return Integer.parseInt(text().substring(text().indexOf("..")+2));
        }
        return -1;
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
        String s = indent + op;
        String text = text();
        if (text.length() > 0 || note().length() > 0) s += " ";
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
