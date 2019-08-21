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

    public static void main(String[] args) { }

// ---------- The annotation fields and methods -------------------------------

    private int value;
    private int flags;
    private int NET, LOW, PC, LEN;
    private BitSet FIRST = new BitSet();
    private BitSet START = new BitSet();
    private BitSet FOLLOW = new BitSet();
    private String note = "";

    // Flag constants. See the relevant analysis classes.
    public static enum Flag {
        TextInput, TokenInput, SN, FN, SP, FP, WF, AA, AB, BP;
        int bit() { return 1 << ordinal(); }
    }

    // Get/set the value.
    int value() { return value; }
    void value(int v) { value = v; }

    // Get, set or unset a flag.
    boolean has(Flag f) { return (flags & f.bit()) != 0; }
    void set(Flag f) { flags |= f.bit(); }
    void unset(Flag f) { flags &= ~f.bit(); }

    // Get/set counts and get bitsets.
    int NET() { return NET; }
    int LOW() { return LOW; }
    void NET(int n) { NET = n; }
    void LOW(int l) { LOW = l; }
    BitSet FIRST() { return FIRST; }
    BitSet START() { return START; }
    BitSet FOLLOW() { return FOLLOW; }

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
    private String source;
    private int start, end;
    private Node left, right;

    // Construct a node.
    Node(Op o, Node r1, Node r2, String s, int b, int e) {
        op = o; left = r1; right = r2; source = s; start = b; end = e;
    }

    // Construct a node with one subnode.
    Node(Op o, Node r, String s, int b, int e) { this(o, r, null, s, b, e); }

    // Construct a node with no subnodes.
    Node(Op o, String s, int b, int e) { this(o, null, null, s, b, e); }

    // Copy a node, with given children. Leave out info except value.
    Node copy(Node x, Node y) {
        Node n = new Node(op, x, y, source, start, end);
        n.value(value());
        return n;
    }

    // Get/set the op and range of text.
    Op op() { return op; }
    int start() { return start; }
    int end() { return end; }
    String text() { return source.substring(start, end); }
    void op(Op o) { op = o; }
    void start(int s) { start = s; }
    void end(int e) { end = e; }

    // The name is the text without decoration.
    String name() {
        switch (op) {
            case Mark: return source.substring(start+1, end);
            case Tag: return source.substring(start+1, end);
            case Act:
                int i = start + 1;
                while (Character.isDigit(source.charAt(i))) i++;
                return source.substring(i, end);
        }
        return text();
    }

    // Get/set the children and the cross-reference link.
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
    // node, and any note attached by one of the passes. Indent a chain of Rule
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
        if (left != null && (op == And || op == Or)) {
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
        if (op == Rule) indent = indent.substring(2);
        if (left != null && right != null) s += "\n" + right.toString(indent);
        return s;
    }

    // Print the source text of a node on one line, e.g. when tracing.
    String trace() {
        int firstLine = row(source, start);
        int lastLine = row(source, end);
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

    // Create an error message based on a source.
    static String err(String source, int start, int end, String error) {
        int firstLineNumber = row(source, start);
        int lastLineNumber = row(source, end);
        int column = column(source, end);
        if (! error.equals("")) error = " " + error;
        String line = line(source, start);
        int col = column(source, start) - 1;
        if (lastLineNumber == firstLineNumber) {
            error = "Error on line " + firstLineNumber + ":" + error + "\n";
            error += line + "\n";
        } else {
            error = "Error on lines " + firstLineNumber + " to " +
                lastLineNumber + ":" + error + "\n";
            error += line + "..." + "\n";
            start = col;
            end = line.length();
        }
        for (int i=0; i<col; i++) error += ' ';
        for (int i=0; i<(end-start); i++) error += '^';
        if (end == start) error += '^';
        return error;
    }

    // Extract the line of text containing a given position.
    private static String line(String text, int p) {
        int row = row(text, p);
        int column = column(text, p);
        int start = p - column + 1, end = p;
        while (end < text.length() && text.charAt(end) != '\n') end++;
        return text.substring(start, end);
    }

    // Find the line number of the line containing a given position.
    private static int row(String text, int p) {
        int row = 1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\r' && ch != '\n') continue;
            if (ch == '\r' && i < text.length()-1) {
                ch = text.charAt(i);
                if (ch == '\n') i++;
            }
            if (i+1 <= p) row++;
            else break;
        }
        return row;
    }

    // Find the column number of a given position, within its line.
    private static int column(String text, int p) {
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\r' && ch != '\n') continue;
            if (ch == '\r' && i < text.length()-1) {
                ch = text.charAt(i+1);
                if (ch == '\n') i++;
            }
            if (i+1 <= p) start = i+1;
            else break;
        }
        return p - start + 1;
    }
}
