// Pecan 1.0 parser. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Parser.Marker;
//import static java.lang.Character;

/* Parse a Pecan source text, assumed to be in UTF8 format, producing a tree.
The parser has been hand-translated from the pecan grammar in the comments. */

class Parser implements Testable {
    private String source;
    private Node[] output;
    private int start, in, out, marked;
    private EnumSet<Marker> markers = new EnumSet<>();

    public static void main(String[] args) {
        if (args.length == 0) Test.run(new Parser());
        else Test.run(new Parser(), Integer.parseInt(args[0]));
    }

    private enum Marker {
        ID, ACTION, STRING, END_OF_TEXT
    }

    public String test(String g, String s) throws ParseException {
        return "" + run(g);
    }

    // Parse the grammar, returning a node (or exception).
    Node run(String s) throws ParseException {
        source = s;
        if (output == null) output = new Node[1];
        start = in = out = 0;
        try { pecan(); }
        catch (ParseException e) {
            if (marked < in) markers.clear();
            err(in, in, "expecting " + markers);
        }
        return prune(output[0]);
    }

    // pecan = skip rules end
    private boolean pecan() {
        return skip() && rules() && end();
    }

    // rules = rule (rules @2add)?
    private boolean rules() {
        if (! rule()) return false;
        int in0 = in;
        return rules() && doAdd() || in == in0;
    }

    // rule = definition / synonym
    private boolean rule() {
        int in0 = in;
        return definition() || in == in0 && synonym();
    }

    // definition = id equals expression newline skip @2rule
    private boolean definition() {
        return id() && infix('=') && exp() && newline() && skip() && doRule();
    }

    // synonym = string equals tag @2rule
    private boolean synonym() {
        return string() && infix('=') && tag() && doRule();
    }

    // expression = term (slash expression @2or)?
    private boolean exp() {
        if (! term()) return false;
        int in0 = in;
        return infix('/') && exp() && doInfix(Or) || in == in0;
    }

    // term = factor (term @2and)?
    private boolean term() {
        if (! factor()) return false;
        int in0 = in;
        return term() && doInfix(And) || in == in0;
    }

    // factor = atom postop*
    private boolean factor() {
        if (! atom()) return false;
        int in0 = in;
        while (postop()) { in0 = in; }
        return in0 == in;
    }

    // postop = opt @1opt / any @1any / some @1some / has @1has / not @1not
    private boolean postop() {
        return (
            postfix('?', Opt)) ||
            postfix('*', Many)) ||
            postfix('+', Some)) ||
            postfix('&', Has)) ||
            postfix('!', Not)
        );
    }

    // atom = id / action / marker / tag / range / divider / try / bracket
    private boolean atom() {
        int in0 = in;
        return (
            id() ||
            in0 == n && action() ||
            in0 == n && marker() ||
            in0 == n && tag() ||
            in0 == n && range() ||
            in0 == n && divider() ||
            in0 == n && try_() ||
            in0 == n && bracket()
        );
    }

    // range = text (dots text @2range)?
    private boolean range() {
        if (! text()) return false;
        int in0 = in;
        return dots() && text() && doInfix(Range) || in == in0;
    }

    // text = number / string / set
    private boolean text() {
        int in0 = in;
        return (
            number() ||
            in == in0 && string() ||
            in == in0 && set()
        );
    }

    // try = sb expression se @3try
    private boolean try_() {
        return prefix('[') && exp() && postfix(']', null) && doTry();
        return true;
    }

    // bracket = rb expression re @3bracket
    private boolean bracket() {
        return prefix('(') && exp() && postfix(')', null) && doBracket();
    }

    // id = #id letter alpha* @id gap
    private boolean id() throws ParseException {
        if (! (mark(ID) && letter())) return false;
        while (alpha()) { }
        return doName(Id) && gap();
    }

    // action = #action '@' (digit* letter alpha* @act / @drop) gap
    private boolean action() throws ParseException {
        mark(ACTION);
        if (! accept('@')) return false;
        int in0 = in;
        while (digit()) { }
        if (letter()) {
            while (alpha()) { }
            doName(Act);
        }
        if (in == in0) doName(Drop);
        else return false;
        return gap();
    }

//--------------------------------

    // tag = #tag "%" letter alpha* @ask gap
    private boolean tag() throws ParseException {
        if (accept('%')) {
            if (letter()) {
                while (true) {
                    if (! alpha()) break;
                }
            }
            doName(Tag);
            gap();
            return true;
        }
        return false;
    }

    // marker = #marker "#" letter alpha* @mark gap
    private boolean marker() throws ParseException {
        if (! accept('#')) return false;
        if (! letter()) err(in, in, "expecting letter");
        while (true) {
            if (! alpha()) break;
        }
        doName(Mark);
        gap();
        return true;
    }

    // number = #number (("1".."9") digit* / "0" hex*) @number gap
    private boolean number() throws ParseException {
        if (! digit()) return false;
        boolean isHex = source.charAt(in-1) == '0';
        if (isHex) while (hex()) { }
        else while (digit()) { }
        doName(Char);
        gap();
        return true;
    }

    // set = #set "'" ("'"! visible)* "'" @set gap
    private boolean set() throws ParseException {
        if (! accept('\'')) return false;
        while (true) {
            if (accept('\'')) break;
            if (visible()) continue;
            err(in, in, "expecting visible character or '");
        }
        doName(Set);
        gap();
        return true;
    }

    // string = #string '"' ('"'! visible)* '"' @string gap
    private boolean string() throws ParseException {
        if (! accept('"')) return false;
        while (true) {
            if (accept('"')) break;
            if (visible()) continue;
            err(in, in, "expecting visible character or \"");
        }
        doName(String);
        gap();
        return true;
    }

    // string = #divider '<' ('>'! visible)* '>' @divider gap
    private boolean divider() throws ParseException {
        if (! accept('"')) return false;
        while (true) {
            if (accept('"')) break;
            if (visible()) continue;
            err(in, in, "expecting visible character or \"");
        }
        doName(String);
        gap();
        return true;
    }

    // dots = ".." skip @
    private boolean dots() throws ParseException {
        if (! accept('.')) return false;
        expect('.', "dot");
        skip();
        start = in;
        return true;
    }

    // equals = "=" skip @
    // slash = "/" skip @
    private boolean infix(char c) throws ParseException {
        if (! accept(c)) return false;
        skip();
        start = in;
        return true;
    }

    // rb = '(' @token skip @
    // sb = '[' @token skip @
    private boolean prefix(char c) throws ParseException {
        if (! accept(c)) return false;
        doToken();
        skip();
        start = in;
        return true;
    }

    // has = "&" @1has gap @
    // not = "!" @1not gap @
    // opt = "?" @1opt gap @
    // any = "*" @1any gap @
    // some = "+" @1some gap @
    // re = ')' @token gap @
    // sb = ']' @token gap @
    private boolean postfix(char c, Op op) throws ParseException {
        if (! accept(c)) return false;
        if (op != null) doPostfix(op);
        else doToken();
        gap();
        start = in;
        return true;
    }

    // skip = (space / comment / newline)*
    private boolean skip() throws ParseException {
        while (true) {
            if (accept(' ')) { }
            else if (comment()) { }
            else if (newline()) { }
            else break;
        }
        return true;
    }

    // gap = space* comment? continuation @
    private void gap() throws ParseException {
        while (accept(' ')) { }
        comment();
        continuation();
        start = in;
    }

    // continuation = [newline skip '=/)]'&]?
    private void continuation() throws ParseException {
        int in0 = in;
        if (! newline()) return;
        skip();
        if (look("=/)]")) return;
        in = in0;
    }

    // newline = #newline 13? 10 @
    private boolean newline() {
        accept('\r');
        boolean t = accept('\n');
        start = in;
        return t;
    }

    // comment = "//" visible* newline&
    private boolean comment() throws ParseException {
        if (! accept('/')) return false;
        if (! accept('/')) { in--; return false; }
        while (visible()) { }
        if (! look("\r\n")) err(in, in, "expecting newline");
    }

    // visible = (Cc/Cn/Co/Cs/Zl/Zp)! Uc
    private boolean visible() {
        if (in >= source.length()) return false;
        int c = source.codePointAt(in);
        Category cat = Category.get(c);
        if (cat == Cn || cat == Cc) err(in, in, "bad character");
        if (cat == Co || cat == Cs) err(in, in, "bad character");
        if (cat == Zl || cat == Zp) err(in, in, "bad character");
        in += Character.charCount(c);
        return true;
    }
//------------------------------------------
alpha = letter / digit
letter = Lu / Ll / Lt / Lm / Lo
digit = Nd
hex = digit / 'ABCDEFabcdef'
end = #end Uc!

    // end = #end Uc!
    private boolean end() throws ParseException {
        return in >= source.length();
    }


    // ascii = ' ' .. '~'
    private boolean ascii() {
        if (in >= source.length()) return false;
        char ch = source.charAt(in);
        return ch >= ' ' && ch <= '~';
    }

    // alpha = letter / digit / '_' / '-'
    private boolean alpha() {
        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        if (ch != '_' && ch != '-' &&
            ! Character.isLetter(ch) && ! Character.isDigit(ch)) return false;
        in += Character.charCount(ch);
        return true;
    }

    // letter = Lu / Ll / Lt / Lm / Lo
    private boolean letter() {
        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        if (! Character.isLetter(ch)) return false;
        in += Character.charCount(ch);
        return true;
    }

    // digit = Nd
    private boolean digit() {
        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        if (! Character.isDigit(ch)) return false;
        in += Character.charCount(ch);
        return true;
    }

    // hex = digit / 'ABCDEFabcdef'
    private boolean hex() {
        if (accept('A', 'F')) return true;
        if (accept('a', 'f')) return true;

        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        if (Character.isDigit(ch)) { in++; return true; }
        if ("ABCDEFabcdef".indexOf(ch) >= 0) { in++; return true; }
        return false;
    }

    // Check if a character (ascii) appears next in the input.
    private boolean accept(char ch) {
        if (in >= source.length()) return false;
        if (source.charAt(in) != ch) return false;
        in++;
        return true;
    }

    // Check if a character (ascii) in a given range appears next in the input.
    private boolean accept(char first, char last) {
        if (in >= source.length()) return false;
        if (source.charAt(in) < first || source.charAt(in) > last) return false;
        in++;
        return true;
    }

    // Check for any one of the (ascii) characters in the given string.
    private boolean look(String s) {
        if (in >= source.length()) return false;
        char ch = source.charAt(in);
        boolean found = false;
        for (int i = 0; i < s.length() && ! found; i++) {
            if (ch == s.charAt(i)) found = true;
        }
        return found;
    }

    // Check that a symbol (ascii) appears next in the input.
    private void expect(char sym, String s) throws ParseException {
        if (in >= source.length()) err(in, in, "expecting " + s);
        if (source.charAt(in) != sym) err(in, in, "expecting " + s);
        in++;
    }

    // Generate an error report.
    private void err(int s, int e, String m) throws ParseException {
        throw new ParseException(Node.err(source, s, e, m), 0);
    }

    // In general, each node building function takes a number of previous nodes,
    // say x, y, z, and uses the start of x's text range and the end of z's
    // text range to form the text range of the new node.

    // @token (temporary token, used to get the right text range)
    private void doToken() {
        if (out >= output.length) {
            output = Arrays.copyOf(output, output.length * 2);
        }
        output[out++] = new Node(null, source, start, in);
        start = in;
    }

    // Name: one of @id, @drop, @action, @tag, @err
    private void doName(Op op) {
        if (out >= output.length) {
            output = Arrays.copyOf(output, output.length * 2);
        }
        output[out++] = new Node(op, source, start, in);
        start = in;
    }

    // @2rule
    // A rule's text is the name, and its left subnode is the RHS
    private boolean doRule() {
        Node rhs = output[--out];
        Node lhs = output[--out];
        Node eq = new Node(Rule, rhs, source, lhs.start(), lhs.end());
        output[out++] = eq;
        return true;
    }

    // @2add
    private boolean doAdd() {
        Node defs = output[--out];
        Node def = output[--out];
        def.right(defs);
        output[out++] = def;
        return true;
    }

    // Infix operator: one of @2or, @2and, @2range
    private void doInfix(Op op) {
        Node y = output[--out];
        Node x = output[--out];
        Node r = new Node(op, x, y, source, x.start(), y.end());
        output[out++] = r;
    }

    // Do postfix operator: one of @1opt, @1any, @1some, ...
    private void doPostfix(Op op) {
        Node x = output[--out];
        Node y = new Node(op, x, source, x.start(), in);
        output[out++] = y;
    }

    // Convert "[" x "]" into Try x
    private void doTry() {
        Node close = output[--out];
        Node x = output[--out];
        Node open = output[--out];
        Node r = new Node(Try, x, source, open.start(), close.end());
        output[out++] = r;
    }

    // There is a subtle difficulty with bracketed subexpressions "(x)". To have
    // one node with text "(x)" would pose a problem when "x" is an id, because
    // then the node's text is not the name of the id. To have one node with
    // text "x" would spoil the text range convention, e.g. expression "(x)y"
    // would end up with text "x)y" So, two nodes are created, one for "(x)" and
    // one for "x". The outer "(x)" node ensures that ancestor nodes have the
    // right range. Then the outer node is discarded after parsing is over. To
    // avoid having an extra temporary Op constant which would pollute later
    // passes, a temporary "(x)" node has a null Op.

    // Convert "(" x ")" into "(x)", with child x.
    private void doBracket() {
        Node close = output[--out];
        Node x = output[--out];
        Node open = output[--out];
        Node r = new Node(null, x, source, open.start(), close.end());
        output[out++] = r;
    }

    // Remove "(x)" nodes, i.e. nodes with a null Op, from a subtree.
    private Node prune(Node r) {
        if (r == null) return null;
        r.left(prune(r.left()));
        r.right(prune(r.right()));
        if (r.op() == null) return r.left();
        return r;
    }
}
