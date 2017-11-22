// Part of Pecan 4. Open source - see licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static java.lang.Character.*;

/* Parse a Pecan source text, producing a tree. */

class Parser implements Test.Callable {
    private String source;
    private Node[] output;
    private int start, in, out;

    public static void main(String[] args) { Test.run(args, new Parser()); }

    public String test(String s) throws ParseException { return "" + run(s); }

    // Parse the grammar, returning a node (or error report).
    Node run(String s) throws ParseException {
        source = s;
        if (output == null) output = new Node[1];
        start = in = out = 0;
        pecan();
        return prune(output[0]);
    }

    // pecan = skip rules Uc! #end
    private boolean pecan() throws ParseException {
        skip();
        if (! rules()) err(in, in, "expecting rule");
        if (in >= source.length()) return true;
        err(in, in, "expecting rule or end of text");
        return false;
    }

    // rules = rule (rules @2add)?
    private boolean rules() throws ParseException {
        if (! rule()) return false;
        if (! rules()) return true;
        doAdd();
        return true;
    }

    // rule = id equals expression newline skip @2rule
    private boolean rule() throws ParseException {
        if (! id()) return false;
        if (! infix('=')) err(in, in, "expecting =");
        if (! expression()) err(in, in, "expecting expression");
        if (! newline()) err(in, in, "expecting newline");
        skip();
        doRule();
        return true;
    }

    // expression = term (slash expression @2or)?
    private boolean expression() throws ParseException {
        if (! term()) return false;
        if (! infix('/')) return true;
        if (! expression()) err(in, in, "expecting expression");
        doInfix(OR);
        return true;
    }

    // term = factor (term @2and)?
    private boolean term() throws ParseException {
        if (! factor()) return false;
        if (! term()) return true;
        doInfix(AND);
        return true;
    }

    // factor = atom postop*
    private boolean factor() throws ParseException {
        if (! atom()) return false;
        while (postop()) { }
        return true;
    }

    // postop = opt / any / some / has / not
    private boolean postop() throws ParseException {
        if (postfix('?', OPT)) return true;
        if (postfix('*', MANY)) return true;
        if (postfix('+', SOME)) return true;
        if (postfix('&', HAS)) return true;
        if (postfix('!', NOT)) return true;
        return false;
    }

    // atom = id / action / marker / tag / range / try / bracket
    private boolean atom() throws ParseException {
        if (id()) return true;
        if (action()) return true;
        if (marker()) return true;
        if (tag()) return true;
        if (range()) return true;
        if (try_()) return true;
        if (bracket()) return true;
        return false;
    }

    // id = letter alpha* @id gap
    private boolean id() throws ParseException {
        if (! letter()) return false;
        while (alpha()) { }
        doName(ID);
        gap();
        return true;
    }

    // action = '@' (digit* letter alpha* @act / @drop) gap
    private boolean action() throws ParseException {
        if (! accept('@')) return false;
        if (digit()) {
            while (digit()) { }
            if (! letter()) err(in, in, "expecting letter");
            while (alpha()) { }
            doName(ACT);
        }
        else if (letter()) {
            while (alpha()) { }
            doName(ACT);
        }
        else doName(DROP);
        gap();
        return true;
    }

    // marker = "#" letter alpha* @mark @2marker gap
    private boolean marker() throws ParseException {
        if (! accept('#')) return false;
        if (! letter()) err(in, in, "expecting letter");
        while (true) {
            if (! alpha()) break;
        }
        doName(MARK);
        gap();
        return true;
    }

    // tag = "%" (letter alpha*)? @tag gap / '`' ('`'! ascii)* '`' @tag gap
    private boolean tag() throws ParseException {
        if (accept('%')) {
            if (letter()) {
                while (true) {
                    if (! alpha()) break;
                }
            }
            doName(TAG);
            gap();
            return true;
        }
        else if (accept('`')) {
            while (true) {
                if (accept('`')) break;
                if (visible()) continue;
                err(in, in, "expecting visible character or `");
            }
            doName(TAG);
            gap();
            return true;
        }
        return false;
    }

    // range = text (dots text @2range)?
    private boolean range() throws ParseException {
        if (! text()) return false;
        if (! dots()) return true;
        if (! text()) err(in, in, "expecting character");
        doInfix(RANGE);
        return true;
    }

    // text = number / string / set
    private boolean text() throws ParseException {
        if (number()) return true;
        if (string()) return true;
        if (set()) return true;
        return false;
    }

    // try = sb expression se @3try
    private boolean try_() throws ParseException {
        if (! prefix('[')) return false;
        if (! expression()) err(in, in, "expecting expression");
        if (! postfix(']', null)) err(in, in, "expecting ]");
        doBack();
        return true;
    }

    // bracket = rb expression re @3bracket
    private boolean bracket() throws ParseException {
        if (! prefix('(')) return false;
        if (! expression()) err(in, in, "expecting expression");
        if (! postfix(')', null)) err(in, in, "expecting )");
        doBracket();
        return true;
    }

    // number = (("1".."9") digit* / "0" hex*) @number gap
    private boolean number() throws ParseException {
        if (! digit()) return false;
        boolean isHex = source.charAt(in-1) == '0';
        if (isHex) while (hex()) { }
        else while (digit()) { }
        doName(CHAR);
        gap();
        return true;
    }

    // string = '"' ('"'! visible)* '"' @string gap
    private boolean string() throws ParseException {
        if (! accept('"')) return false;
        while (true) {
            if (accept('"')) break;
            if (visible()) continue;
            err(in, in, "expecting visible character or \"");
        }
        doName(STRING);
        gap();
        return true;
    }

    // set = "'" ("'"! ascii)* "'" @set gap
    private boolean set() throws ParseException {
        if (! accept('\'')) return false;
        while (true) {
            if (accept('\'')) break;
            if (visible()) continue;
            err(in, in, "expecting visible character or '");
        }
        doName(SET);
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

    // Parse an (ascii) infix symbol, which can be discarded.
    // equals = "=" infix
    // slash = "/" infix
    // infix = skip @
    private boolean infix(char c) throws ParseException {
        if (in >= source.length()) return false;
        if (source.charAt(in) != c) return false;
        in++;
        skip();
        start = in;
        return true;
    }

    // Parse a prefix symbol, and push on the stack as a temporary node
    // to mark its position
    // rb = '(' prefix
    // sb = '[' prefix
    // prefix = @token skip @
    private boolean prefix(char c) throws ParseException {
        if (in >= source.length()) return false;
        if (source.charAt(in) != c) return false;
        in++;
        doToken();
        skip();
        start = in;
        return true;
    }

    // Parse a postfix symbol and push on the stack as a temporary node
    // to mark its position
    // opt = "?" @1opt gap @
    // any = "*" @1any gap @
    // some = "+" @1some gap @
    // has = "&" @1has gap @
    // not = "!" @1not gap @
    // re = ')' @token gap @
    // sb = ']' @token gap @
    private boolean postfix(char c, Op op) throws ParseException {
        if (in >= source.length()) return false;
        if (source.charAt(in) != c) return false;
        in++;
        if (op != null) doPostfix(op);
        else doToken();
        gap();
        start = in;
        return true;
    }

    // Skip is an optional sequence of white space or comments
    // skip = (space / comment / newline)*
    // space = ' '
    private void skip() throws ParseException {
        while (true) {
            if (in < source.length() && source.charAt(in) == ' ') {
                in++;
            }
            else if (comment()) { }
            else if (newline()) { }
            else break;
        }
    }

    // A gap follows a token which can end a rule.  It looks ahead for a
    // possible continuation to see if the next newline should be skipped.
    // gap = space* comment? continuation @
    private void gap() throws ParseException {
        while (in < source.length() && source.charAt(in) == ' ') in++;
        comment();
        continuation();
        start = in;
    }

    // continuation = [newline skip &'=/)]']?
    private void continuation() throws ParseException {
        int saveIn = in;
        if (! newline()) return;
        skip();
        if (in < source.length() && "=/)]".indexOf(source.charAt(in)) >= 0) {
            return;
        }
        in = saveIn;
    }

    // newline = (10 / 133 / 8232 / 13 (10/133)?) @
    // NEL = 133
    // LS = 8232
    boolean first = true;
    private boolean newline() {
        if (in >= source.length()) return false;
        if (source.charAt(in) == 10) in++;
        else if (source.charAt(in) == 133) in++;
        else if (source.charAt(in) == 8232) in++;
        else if (source.charAt(in) == 13) {
            in++;
            if (in < source.length()) {
                if (source.charAt(in) == 10) in++;
                else if (source.charAt(in) == 133) in++;
            }
        }
        else return false;
        start = in;
        return true;
    }

    // comment = "//" visible* &newline
    private boolean comment() throws ParseException {
        if (! accept('/')) return false;
        if (! accept('/')) { in--; return false; }
        while (true) {
            if (in >= source.length()) err(in, in, "expecting newline");
            int c = source.codePointAt(in);
            if (c == '\r' || c == '\n') return true;
            int type = Character.getType(c);
            if (type == UNASSIGNED
             || type == CONTROL) err(in, in, "bad character");
            if (type == PRIVATE_USE) err(in, in, "bad character");
            if (type == SURROGATE) err(in, in, "bad character");
            if (type == LINE_SEPARATOR) err(in, in, "bad character");
            if (type == PARAGRAPH_SEPARATOR) err(in, in, "bad character");
            in += Character.charCount(c);
        }
    }

    // visible = (Cc/Cn/Co/Cs/Zl/Zp)! Uc
    private boolean visible() {
        if (in >= source.length()) return false;
        int c = source.codePointAt(in);
        int type = Character.getType(c);
        if (type == UNASSIGNED || type == CONTROL || type == PRIVATE_USE ||
            type == SURROGATE || type == LINE_SEPARATOR ||
            type == PARAGRAPH_SEPARATOR) return false;
        in += Character.charCount(c);
        return true;
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
        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        if (Character.isDigit(ch)) { in++; return true; }
        if ("ABCDEFabcdef".indexOf(ch) >= 0) { in++; return true; }
        return false;
    }

    // Check whether a character (ascii) appears next in the input.
    private boolean accept(char ch) {
        if (in >= source.length()) return false;
        if (source.charAt(in) != ch) return false;
        in++;
        return true;
    }

    // Check that a symbol appears next in the input.
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
    private void doRule() {
        Node rhs = output[--out];
        Node lhs = output[--out];
        Node eq = new Node(RULE, rhs, source, lhs.start(), lhs.end());
        output[out++] = eq;
    }

    // @2add
    private void doAdd() {
        Node defs = output[--out];
        Node def = output[--out];
        def.right(defs);
        output[out++] = def;
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

    // Convert "[" x "]" into TRY x
    private void doBack() {
        Node close = output[--out];
        Node x = output[--out];
        Node open = output[--out];
        Node r = new Node(TRY, x, source, open.start(), close.end());
        output[out++] = r;
    }

    // There is a subtle difficulty with bracketed subexpressions "(x)".
    // To have one node with text "(x)" poses a problem when "x" is an id,
    // because then the node's text is not the name of the id.
    // To have one node with text "x" spoils the text range convention, e.g.
    // "(x)y" could end up with text "x)y"
    // So, two nodes are created, one for "(x)" and one for "x", and the outer
    // "(x)" node is discarded after parsing is over.
    // To avoid having an extra temporary Op constant which would pollute later
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
