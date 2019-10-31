// Pecan 1.0 nodes. Free and open source. See licence.txt.

package pecan;
import java.util.*;
import static pecan.Op.*;

/* A Node represents any parsing expression, together with source text and
annotation information.

A node has an op, up to two subnodes, a source string, some flags, some counts,
and a temporary note also used to store a print format. If the left subnode is
null, the right subnode represents a cross-reference link instead of a child.
For more information about annotations, see the classes which handle them. */

class Node {
    private Op op;
    private Node left, right;
    private Source source;
    private int flags;
    private int[] counts = new int[Count.values().length];
    private String note = "";

    // Flag and count constants.
    public static enum Flag { TI, SN, FN, SP, FP, WF, AA, EE, AB; }
    public static enum Count { NET, NEED, PC, LEN; }

    // Construct a node from a source, with any number of subnodes.
    Node(Op o, Node x, Node y, Source s) {
        op = o; left = x; right = y; source = s;
    }
    Node(Op o, Node x, Source s) { this(o, x, null, s); }
    Node(Op o, Source s) { this(o, null, null, s); }

    // Construct a node from strings (during transforms).
    Node(Op op, String b, Node x, String m, Node y, String a) {
        this(op, x, y, null);
        String sx = (x == null) ? "" : x.text();
        String sy = (y == null) ? "" : y.text();
        source = new Source(b + sx + m + sy + a);
    }
    Node(Op op, String b, Node x, String a) { this(op, b, x, "", null, a); }
    Node(Op op, String a) { this(op, a, null, "", null, ""); }

    // Make a deep copy of a node.
    Node deepCopy() {
        Node copy = new Node(op, left, right, source);
        if (left != null) copy.left = left.deepCopy();
        if (right != null) copy.right = right.deepCopy();
        copy.flags = flags;
        return copy;
    }

    // Get the fields. Formats share the note field.
    Op op() { return op; }
    Node left() { return left; }
    Node right() { return left == null ? null : right; }
    Node ref() { return left != null ? null : right; }
    Source source() { return source; }
    String text() { return source.text(); }
    int flags() { return flags; }
    String note() { return note; }
    String format() { return note; }

    // Set the fields.
    void op(Op o) { op = o; }
    void left(Node x) {
        assert((right == null) || ((x == null) == (left == null)));
        left = x;
    }
    void right(Node y) { assert(left != null); right = y; }
    void ref(Node r) { assert(left == null); right = r; }
    void source(Source s) { source = s; }
    void flags(int fs) { flags = fs; }
    void note(String s) { note = s; }
    void format(String s) { note = s; }

    // Get or set a flag or a count.
    boolean has(Flag f) { return (flags & (1 << f.ordinal())) != 0; }
    void set(Flag f) { flags |= (1 << f.ordinal()); }
    void unset(Flag f) { flags &= ~(1 << f.ordinal()); }
    int get(Count c) { return counts[c.ordinal()]; }
    void set(Count c, int n) { counts[c.ordinal()] = n; }

    // Get the name of a node, i.e. the text without quotes, escapes etc.
    // TODO hyphens and literal names.
    String name() {
        String s = source.rawText();
        char ch = s.charAt(0);
        if (ch == '#' || ch == '%' || ch == '@') s = s.substring(1);
        if (ch == '@') {
            while (s.length() > 0 && '0' <= s.charAt(0) && s.charAt(0) <= '9') {
                s = s.substring(1);
            }
        }
        if (s.length() == 0) return s;
        ch = s.charAt(0);
        if ("\"'`<{".indexOf(ch) >= 0) s = s.substring(1, s.length() - 1);
        return s;
    }

    // For a character, extract the value, i.e. Unicode code point.
    int charCode() {
        assert(op == Char);
        return source.rawCharAt(1);
    }

    // For a range, return the low end (i = 0) or high end (i > 0).
    int end(int i) {
        assert(op == Range);
        if (i == 0) return low();
        return high();
    }

    // For a range, extract the low end.
    int low() {
        assert(op == Range);
        return source.rawCharAt(1);
    }

    // For a range, extract the high end.
    int high() {
        assert(op == Range);
        int len = source.rawLength(1);
        int n = 1 + len + 2;
        return source.rawCharAt(n);
    }

    // For an action or drop, extract the arity.
    int arity() {
        assert(op == Act || op == Drop);
        String s = text();
        int n = 1;
        while (s.length() > n && '0' <= s.charAt(n) && s.charAt(n) <= '9') n++;
        if (n == 1) return 0;
        return Integer.parseInt(s.substring(1, n));
    }

    // Clear the notes.
    private void clear() {
        note("");
        if (left != null) left.clear();
        if (left != null && right != null) right.clear();
    }

    // Return a node tree as multi-line text. Then clear the notes, ready
    // for the next pass.
    public String toString() {
        String s = toString("") + "\n";
        clear();
        return s;
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
        int end = source.length();
        int lineNumber = source.lineNumber();
        int newline = source.indexOf("\n");
        if (newline < 0) {
            return "P" + lineNumber + ": " + source.text();
        }
        String s = "P" + lineNumber + "... : ";
        int pos = 10;
        if (newline < pos) pos = newline;
        s += source.substring(0, pos);
        s += "...";
        pos = end - 10;
        newline = source.lastIndexOf("\n") + 1;
        if (newline >= 0 && newline > pos) pos = newline;
        s += source.substring(pos, end);
        return s;
    }

/*
// TODO move this stuff to Source or Pretty.

    private static String[] charMap = new String[128];
    static { fillCharMap(); }

    // Translate the name of an id, tag, mark or action to make it suitable as a
    // target language identifier. This includes translating hyphens in ordinary
    // identifers as well as non-alpha characters in literal names.
    String translate() {
        String name = name();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); ) {
            int ch = name.codePointAt(i);
            if (ch < 128) sb.append(charMap[ch]);
            else sb.append("U" + ch);
            i += Character.charCount(ch);
        }
        return sb.toString();
    }

    // For each visible ascii character, define how it is translated when it
    // appears in a literal name.
    private static void fillCharMap() {
        for (char ch = 'a'; ch <= 'z'; ch++) charMap[ch] = "" + ch;
        for (char ch = 'A'; ch <= 'Z'; ch++) charMap[ch] = "" + ch;
        for (char ch = '0'; ch <= '9'; ch++) charMap[ch] = "" + ch;
        for (char ch = ' '; ch < '~'; ch++) switch (ch) {
            case ' ': charMap[ch] = "Sp"; break;
            case '!': charMap[ch] = "Em"; break;
            case '"': charMap[ch] = "Dq"; break;
            case '#': charMap[ch] = "Hs"; break;
            case '$': charMap[ch] = "Dl"; break;
            case '%': charMap[ch] = "Pc"; break;
            case '&': charMap[ch] = "Am"; break;
            case '\'': charMap[ch] = "Sq"; break;
            case '(': charMap[ch] = "Ob"; break;
            case ')': charMap[ch] = "Cb"; break;
            case '*': charMap[ch] = "St"; break;
            case '+': charMap[ch] = "Pl"; break;
            case ',': charMap[ch] = "Cm"; break;
            case '-': charMap[ch] = "Mi"; break;
            case '.': charMap[ch] = "Dt"; break;
            case '/': charMap[ch] = "Sl"; break;
            case ':': charMap[ch] = "Cl"; break;
            case ';': charMap[ch] = "Sc"; break;
            case '<': charMap[ch] = "Lt"; break;
            case '=': charMap[ch] = "Eq"; break;
            case '>': charMap[ch] = "Gt"; break;
            case '?': charMap[ch] = "Qm"; break;
            case '@': charMap[ch] = "At"; break;
            case '[': charMap[ch] = "Os"; break;
            case '\\': charMap[ch] = "Bs"; break;
            case ']': charMap[ch] = "Cs"; break;
            case '^': charMap[ch] = "Ht"; break;
            case '_': charMap[ch] = "Us"; break;
            case '`': charMap[ch] = "Bq"; break;
            case '{': charMap[ch] = "Oc"; break;
            case '|': charMap[ch] = "Vb"; break;
            case '}': charMap[ch] = "Cc"; break;
            case '~': charMap[ch] = "Ti"; break;
        }
    }
*/
    public static void main(String[] args) {
        Source s = new Source("'a' '\\127' @add @2mul 'a..z' `<=`");
        Node n = new Node(Char, s.sub(0,3));
        assert(n.name().equals("a"));
        assert(n.charCode() == 97);
        n = new Node(Char, s.sub(4, 10));
        assert(n.name().equals("\177"));
        n = new Node(Act, s.sub(11, 15));
        assert(n.name().equals("add"));
        assert(n.arity() == 0);
        n = new Node(Act, s.sub(16, 21));
        assert(n.name().equals("mul"));
        assert(n.arity() == 2);
        n = new Node(Range, s.sub(22, 28));
        assert(n.name().equals("a..z"));
        assert(n.low() == 97);
        assert(n.high() == 97+25);
        n = new Node(Id, s.sub(29, 33));
        assert(n.name().equals("<="));
    }
}
