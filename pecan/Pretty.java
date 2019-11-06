// Pecan 1.0 pretty printing. Free and open source. See licence.txt.

package pecan;
import java.util.*;
import static pecan.Node.Count.*;

/* Provide a simplified custom approach to pretty-printing. The approach is
informed by, but doesn't closely follow, the paper "A Prettier Printer" by
Wadler. Printing is done twice. The first time, everything is on one line, and
the length of the text generated for each node is measured. The second time, the
measurements are used to make decisions about where to add newlines. The
specifiers in node formats can contain:

    %n    newline (and remove preceding space) if whole node doesn't fit
    %f    newline to fill the line, i.e. if the next operand doesn't fit
    %g    newline if whole group doesn't fit
    %t    tab, if at start of line
    %l    left hand subexpression
    %r    right hand subexpression
    %s    literal name of node
    %c

 */

class Pretty {
    private StringBuilder output = new StringBuilder();
    private int cursor, indent, tab = 2, margin = 80;
    private String escape1, escape2, escape4;

    // Set the tab size from a string of spaces.
    void tab(String s) {
        tab = s.length();
    }

    // Set the escape formats.
    void escapes(String s1, String s2, String s4) {
        escape1 = s1;
        escape2 = s2;
        escape4 = s4;
    }

    // Ask for the text that's been printed, and reset.
    String text() {
        String s = output.toString();
        output.setLength(0);
        cursor = 0;
        indent = 0;
        return s;
    }

    // Print non-newline text, keep track of amount printed on current line.
    private void print(String s) {
        output.append(s);
        cursor += s.length();
    }

    // Print a single character, with escapes.
    private void printChar(int ch) {
        if (' ' <= ch && ch <= '~') print("" + (char)ch);
        else if (ch <= 0xFF && escape1.equals("")) print("" + (char)ch);
        else if (ch <= 0xFF) print(String.format(escape1, ch));
        else if (ch <= 0xFFFF && escape2.equals("")) print("" + (char)ch);
        else if (ch <= 0xFFFF) print(String.format(escape2, ch));
        else if (escape4.equals("")) {
            output.appendCodePoint(ch);
            cursor += 1;
        }
        else print(String.format(escape4, ch));
    }

    // Print literal text, with escapes.
    private void printLiteral(String s) {
        for (int i = 0; i < s.length(); ) {
            int ch = s.codePointAt(i);
            printChar(ch);
            i += Character.charCount(ch);
        }
    }

    // Print strings according to a format containing %s or %n.
    void printf(String f, String... ss) {
        int start = 0, i = f.indexOf('%'), index = 0;
        while (i >= 0) {
            print(f.substring(start, i++));
            char ch = f.charAt(i++);
            switch (ch) {
                case 's': print(ss[index++]); break;
                case 'n': print("\n"); cursor = 0; break;
                default: throw new Error("unexpected specifier %" + ch);
            }
            start = i;
            i = f.indexOf('%', start);
        }
        print(f.substring(start));
    }

    // Print a node twice.
    void printf(Node node) {
        int save = output.length();
        printNode(null, true, node);
        output.setLength(save);
        cursor = 0;
        printNode(null, true, node);
    }

    // Print a node according to its format. Gather length information.
    private void printNode(Op op, boolean fit, Node node) {
        if (node.op() != op) fit = node.get(LEN) <= margin - cursor;
        op = node.op();
        String f = node.format();
        int before = output.length();
        int start = 0, i = f.indexOf('%'), number;
        int indent0 = indent, cursor0 = cursor;
        int seq = 0;
        while (i >= 0) {
            print(f.substring(start, i++));
            char ch = f.charAt(i++);
            if ('0' <= ch && ch <= '9') {
                number = ch - '0';
                ch = f.charAt(i++);
            }
            switch (ch) {
                case 's': printLiteral(node.rawText()); break;
                case 'c': printChar(node.end(seq++)); break;
                case 'n': indent = indent0; printAll(cursor0, node); break;
                case 'f': indent = indent0; printFill(cursor, node); break;
                case 'g': if (! fit) printNew(); break;
                case 't': printTab(); break;
                case 'l': printNode(op,fit,node.left()); break;
                case 'r': printNode(op,fit,node.right()); break;
                default: throw new Error("bad specifier %" + ch);
            }
            start = i;
            i = f.indexOf('%', start);
        }
        indent = indent0;
        print(f.substring(start));
        int after = output.length();
        node.set(LEN, after - before);
    }

    // Print a newline. If the previous line ends with a space, remove it.
    private void printNew() {
        int n = output.length();
        if (n > 0 && output.charAt(n - 1) == ' ') output.setLength(n - 1);
        print("\n");
        cursor = 0;
        for (int i = 0; i < indent; i++) print(" ");
    }

    // Print a newline if the whole node doesn't fit. If the previous line ends
    // with a space, remove it.
    private void printAll(int cursor0, Node node) {
        boolean fit = node.get(LEN) <= margin - cursor0;
        if (! fit) printNew();
    }

    // Print a newline if the next operand doesn't fit. If the previous line
    // ends with a space, remove it.
    private void printFill(int cursor, Node node) {
        int len;
        Node r = node.right();
        if (r.op() != node.op()) len = r.get(LEN);
        else len = r.left().get(LEN) + 3;
        boolean fit = len <= margin - cursor;
        if (! fit) printNew();
    }

    // Print a tab and increase the indent, if at the start of a line.
    private void printTab() {
        if (cursor > indent) return;
        for (int i = 0; i < tab; i++) print(" ");
        indent += tab;
    }
/*
    void printf(String t, String s, int n, int n2, Node e) {
        int start = 0, i = t.indexOf('%');
        int save = indent;
        while (i >= 0) {
            print(t.substring(start, i));
            i++;
            char ch = t.charAt(i++);
            switch (ch) {
                case 's': print(s); break;
                case 'c': print("" + (char)n); n = n2; break;
                case 'd': print("" + n); n = n2; break;
                case 'x': case 'X':
                    String f = (n <= 0xFFFF) ? "%4" + ch : "%8" + ch;
                    print(java.lang.String.format(f, n));
                    n = n2;
                    break;
                case 'e': break;
                case 'n':
                    indent = save;
                    while (i < t.length() && t.charAt(i) == ' ') {
                        indent++;
                        i++;
                    }
                    print("\n");
                    break;
                default: check(false, t, ch);
            }
            start = i;
            i = t.indexOf('%', start);
        }
        indent = save;
        print(t.substring(start));
    }
*/
    private void check(boolean ok, String t, char ch) {
        if (ok) return;
        throw new Error("Unexpected specifier %" + ch + " in " + t);
    }

/*
    // Print according to a given format. Further arguments are matched up with
    // % specifiers in the template. Matching is by type rather than position,
    // except for the case of two integers. Spaces at the start of a line indent
    // everything generated from that line, but the indent reverts at the next
    // newline.
    private void printT(String t, String s, int n, int n2, Node e) {
        int start = 0, i = t.indexOf('%');
        int save = indent;
        while (i >= 0) {
            print(t.substring(start, i));
            i++;
            char ch = t.charAt(i++);
            switch (ch) {
                case 's': print(s); break;
                case 'c': print("" + (char)n); n = n2; break;
                case 'd': print("" + n); n = n2; break;
                case 'x': case 'X':
                    String f = (n <= 0xFFFF) ? "%4" + ch : "%8" + ch;
                    print(java.lang.String.format(f, n));
                    n = n2;
                    break;
                case 'e': compile(e); break;
                case 'n':
                    indent = save;
                    while (i < t.length() && t.charAt(i) == ' ') {
                        indent++;
                        i++;
                    }
                    newline(0);
                    break;
                default: check(false, t, ch);
            }
            start = i;
            i = t.indexOf('%', start);
        }
        indent = save;
        print(t.substring(start));
    }

    // Cover all the patterns of call.
    private void printT(String t, String s, Node e) { printT(t,s,-1,-1,e); }
    private void printT(String t, String s) { printT(t,s,-1,-1,null); }
    private void printT(String t, int n) { printT(t,null,n,-1,null); }
    private void printT(String t, int n, String s) { printT(t,s,n,-1,null); }
    private void printT(String t, int n, int n2) { printT(t,null,n,n2,null); }
*/
    private String flatten(String s) {
        return s.replaceAll("%n *", " ");
    }


    public static void main(String[] args) {
        Pretty f = new Pretty();
        System.out.println("Pretty class OK");
    }
}
