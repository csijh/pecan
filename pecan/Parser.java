// Pecan 1.0 parser. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Category.*;
import static pecan.Op.*;
import static pecan.Parser.Marker.*;

/* Parse a Pecan source text, assumed to be in UTF8 format, producing a tree.

The parser has been hand-translated from the pecan grammar in the comments, to
make this class self-contained. The grammar (a) has no left-hand alternative
starting with an action and (b) has no lookahead construct containing error
markers or actions. That means actions don't need to be delayed.

Each parser method returns success or failure. The translations used are:

    x y    ->  x() && y()
    x / y  ->  either(n) && x() || or(n) && y()
    x?     ->  either(n) && x() || or(n)
    [x]    ->  maybe(n, look(n) && x())
    x&     ->  has(n, look(n) && x())
    x!     ->  not(n, look(n) && x())

Here, n is the nesting depth of the expression, to ensure that expressions which
are under way simultaneously are distinguihed. */

class Parser implements Testable {
    private Source source;
    private Node[] output;
    private int start, in, out, lookahead, marked;
    private Set<Marker> markers = EnumSet.noneOf(Marker.class);
    private int[] save = new int[10];
    private Set<String> cats;

    public static void main(String[] args) {
        Test.run(new Parser(), args);
    }

    // Error markers, in alphabetical order.
    enum Marker {
        ATOM, BRACKET, DOT, END_OF_TEXT, EQUALS, LETTER, NEWLINE, QUOTE, TAG
    }

    public String test(String g) {
        return run(new Source(g, null, 1)).toString();
    }

    // Record an error marker for the current input position.
    private boolean mark(Marker m) {
        if (lookahead > 0) return true;
        if (marked > in) throw new Error("marked " + marked + " in " + in);
        if (marked < in) {
            markers.clear();
            marked = in;
        }
        markers.add(m);
        return true;
    }

    // Produce an error message from the markers at the current input position.
    private String message() {
        String s = "";
        if (marked < in) markers.clear();
        for (Marker m : markers) {
            if (s.length() > 0) s = s + ", ";
            s = s + m.toString().toLowerCase();
        }
        return s;
    }

    // Prepare for a choice by recording the input position. The number n is the
    // nesting depth of the choice construct in the grammar.
    private boolean either(int n) {
        save[n] = in;
        return true;
    }

    // Check whether progress has already been made before continuing with the
    // second or subsequent alternative of a choice.
    private boolean or(int n) {
        return in == save[n];
    }

    // Start a lookahead. Save the input position. Assume that lookahead
    // expressions don't include error markers or actions.
    private boolean look(int n) {
        save[n] = in;
        return true;
    }

    // Implement [x] as maybe(n, look(n) && x())
    private boolean maybe(int n, boolean b) {
        if (! b) in = save[n];
        return b;
    }

    // Implement x& as has(n, look(n) && x())
    private boolean has(int n, boolean b) {
        in = save[n];
        return b;
    }

    // Implement x! as not(n, look(n) && x())
    private boolean not(int n, boolean b) {
        in = save[n];
        return ! b;
    }

    // Parse the grammar, returning a node (possibly an error node).
    Node run(Source s) {
        source = s;
        if (output == null) output = new Node[1];
        start = in = out = lookahead = marked = 0;
        markers.clear();
        cats = new HashSet<String>();
        for (Category cat : Category.values()) cats.add(cat.toString());
        boolean ok = pecan();
        if (! ok) {
            Node err = new Node(Error, s, in, in);
            err.note(s.error(in, in, "expecting " + message()));
            return err;
        }
        return prune(output[0]);
    }

    // pecan = skip rules end
    private boolean pecan() {
        return skip() && rules() && end();
    }

    // rules = rule (rules @2list)?
    private boolean rules() {
        return rule() && (
            either(0) && rules() && doList() ||
            or(0)
        );
    }

    // rule = inclusion / id equals expression endline skip @2rule
    private boolean rule() {
        return either(0) && inclusion() ||
        or(0) && id() && equals() && exp() && endline() && skip() && doRule();
    }

    // inclusion = string endline skip @1include
    private boolean inclusion() {
        return string() && endline() && skip() && doInclude();
    }

    // expression = term (slash expression @2or)?
    private boolean exp() {
        return term() && (
            either(0) && infix('/') && exp() && doInfix(Or) ||
            or(0)
        );
    }

    // term = factor (term @2and)?
    private boolean term() {
        return factor() && (
            either(0) && term() && doInfix(And) ||
            or(0)
        );
    }

    // factor = atom postop*
    // Changed postop* to postops
    private boolean factor() {
        return atom() && postops();
    }

    // postops = (postop postops)?
    // Extra rule for postop*
    private boolean postops() {
        do { either(0); } while (postop());
        return or(0);
    }

    // postop = opt @1opt / any @1any / some @1some / has @1has / not @1not
    private boolean postop() {
        return (
            postfix('?', Opt) ||
            postfix('*', Any) ||
            postfix('+', Some) ||
            postfix('&', Has) ||
            postfix('!', Not)
        );
    }

    // atom = text / id / action / marker / tag / try / bracket
    private boolean atom() {
        return (
            either(0) && text() ||
            or(0) && id() ||
            or(0) && action() ||
            or(0) && marker() ||
            or(0) && tag() ||
            or(0) && try_() ||
            or(0) && bracket()
        );
    }

    // text = string / set / number / divider / category
    private boolean atom() {
        return (
            either(0) && string() ||
            or(0) && set() ||
            or(0) && number() ||
            or(0) && divider() ||
            or(0) && category()
        );
    }

    // category = [cat alpha!] @category gap
    private boolean category() {
        return maybe(0, look(0) &&
            cat() && not(1, look(1) && alpha())
        ) && doCategory() && gap();
    }

    // id = #atom (cat alpha!)! letter alpha* @id gap / backquote
    // Changed alpha* to alphas
    private boolean id() {
        return either(0) && (
            mark(ATOM) && not(1, look(1) &&
                cat() && not(2, look(2) && alpha())) &&
            letter() && alphas && doName(Id) && gap();
        ) ||
        or(0) && backquote();
    }

    // alpha* = (alpha alphas)?
    // Extra rule for postop*
    private boolean alphas() {
        do { either(0); } while (alpha());
        return or();
    }

    // backquote = "`" ("`"! visible)* #quote "`" @id gap
    // Changed  ("`"! visible)*  to bqvisibles
    private boolean backquote() {
        return accept('`') && bqvisibles && mark(QUOTE) && accept('`') &&
        doName(Id) && gap();
    }

    // bqvisibles* = ("`"! visible bqvisibles)?
    // Extra rule for ("`"! visible)*
    private boolean bqvisibles() {
        do { either(0); } while (! next('`') && visible());
        return or();
    }

    // action = '@' (decimal* #letter letter alpha* @act / @drop) gap
    // Changed decimal* to decimals and alpha* to alphas
    private boolean action() {
        accept('@') && (
            either(0) && decimal() && mark(LETTER) && alphas() && doName(Act) ||
            or(0) && doName(Drop)
        ) && gap();
    }

    // decimals* = (decimal decimals)?
    // Extra rule for decimal*
    private boolean decimals() {
        do { either(0); } while (decimal());
        return or();
    }

    // marker = "#" #letter letter alpha* @mark gap
    // Changed alpha* to alphas
    private boolean marker() {
        return accept('#') && mark(LETTER) && letter() && alphas() &&
        doName(Mark) && gap();
    }

    // tag = "%" #letter letter alpha* @tag gap
    // Changed alpha* to alphas
    private boolean tag() {
        return accept('%') && mark(LETTER) && letter() && alphas() &&
        doName(Tag) && gap();
    }

    // divider = '<' ('>' @end / ('>'! visible)+ '>' @divider) gap
    // changed  ('>'! visible)+  to  visible dvisibles
    private boolean divider() {
        return accept('<') && (
            either(0) && accept('>') && doName(End) ||
            or(0) && visible() && dvisibles() && accept('>') && doName(Divider)
        ) && gap();
    }

    // dvisibles* = ('>'! visible dvisibles)?
    // Extra rule for ('>'! visible)*
    private boolean dvisibles() {
        do { either(0); } while (! next('>') && visible());
        return or();
    }

    // set = range / "'" ("'"! visible)* #quote "'" @set gap
    // Changed  ("'"! visible)*  to sqvisible
    private boolean set() {
        return either(0) && range() ||
        or(0) && (
            accept('\'') && sqvisible() && mark(QUOTE) && accept('\'') &&
            doName(Set) && gap()
        );
    }

    // range = numbers / ["'" ("'"! visible) ".."] ("'"! visible) "'" @range gap
    private boolean range() {

    }

    // numbers = [digits '.'] #dot '.' digits @range gap
    // string = '"' ('"'! visible)* #quote '"' @string gap
    // number = digits @number gap
    // try = sb expression se @3try
    // bracket = rb expression re @3bracket

    // dots = '.' #dot '.' skip @
    // equals = #equals "=" skip @
    // slash = #op "/" skip @
    // has = #op "&" gap @
    // not = #op "!" gap @
    // opt = #op "?" gap @
    // any = #op "*" gap @
    // some = #op "+" gap @
    // rb = "(" @token skip @
    // sb = "[" @token skip @
    // re = #bracket ")" @token gap @
    // se = #bracket "]" @token gap @

    // skip = (space / comment / newline)*
    // gap = space* comment? continuation @
    // space = ' '
    // continuation = [newline skip '=/)]'&]?
    // newline = #newline 13? 10 @
    // comment = ["//"] visible* newline
    // visible = (Cc/Cn/Co/Cs/Zl/Zp)! Uc
    // alpha = letter / Nd / '_' / '-'
    // letter = Lu / Ll / Lt / Lm / Lo
    // decimal = '0..9'
    // hex = decimal / 'ABCDEFabcdef'
    // digits = ('1..9' decimal*) / '0' hex*
    // code = "Uc" / "Cc" / ...
    // end = #end <>

    // Hand optimised.
    private boolean cat() {
        if (in >= source.length() - 2) return false;
        return cats.contains(source.substring(in, in + 2));
    }



    // range = number (dots number @2range)?
    private boolean range() {
        if (! number()) return false;
        int in0 = in;
        return dots() && number() && doInfix(Range) || in == in0;
    }

    // try = sb expression se @3try
    private boolean try_() {
        return open('[') && exp() && close(']') && doTry();
    }

    // bracket = rb expression re @3bracket
    private boolean bracket() {
        return open('(') && exp() && close(')') && doBracket();
    }

    // number = (('1..9') decimal* / "0" hex*) @number gap
    private boolean number() {
        if (! decimal()) return false;
        boolean isHex = source.charAt(in-1) == '0';
        if (isHex) while (hex()) { }
        else while (decimal()) { }
        return doName(Number) && gap();
    }


    // string = '"' ('"'! visible)* #quote '"' @string gap
    private boolean string() {
        if (! accept('"')) return false;
        while (! next('"') && visible()) { }
        return mark(QUOTE) && accept('"') && doName(String) && gap();
    }

    // dots = '.' #dot '.' skip @
    private boolean dots() {
        return accept('.') && mark(DOT) && accept('.') && skip() && drop();
    }

    // equals = #equals "=" skip @
    private boolean equals() {
        return mark(EQUALS) && accept('=') && skip() && drop();
    }

    // slash = "/" skip @
    private boolean infix(char c) {
        return accept(c) && skip() && drop();
    }

    // rb = "(" @token skip @
    // sb = "[" @token skip @
    private boolean open(char c) {
        return accept(c) && doToken() && skip() && drop();
    }

    // re = #bracket ")" @token gap @
    // se = #bracket "]" @token gap @
    private boolean close(char c) {
        return mark(BRACKET) && accept(c) && doToken() && gap() && drop();
    }

    // has = "&" @1has gap @
    // not = "!" @1not gap @
    // opt = "?" @1opt gap @
    // any = "*" @1any gap @
    // some = "+" @1some gap @
    private boolean postfix(char c, Op op) {
        return accept(c) && doPostfix(op) && gap() && drop();
    }

    // skip = (space / comment / newline)*
    private boolean skip() {
        int in0 = in;
        while (accept(' ') || comment() || in == in0 && newline()) { in0 = in; }
        return in == in0;
    }

    // gap = space* comment? continuation @
    private boolean gap() {
        while (accept(' ')) { }
        int in0 = in;
        comment();
        if (in != in0) return false;
        return continuation() && drop();
    }

    // continuation = [newline skip '=/)]'&]?
    private boolean continuation() {
        int in0 = in;
        lookahead++;
        boolean t = newline() && skip() && (
            next('=') || next('/') || next(')') || next(']')
        );
        lookahead--;
        if (! t) in = in0;
        return true;
    }

    // newline = #newline 13? 10 @
    private boolean newline() {
        mark(NEWLINE);
        return (accept('\r') || true) && accept('\n') && drop();
    }

    // comment = "//" visible* newline
    private boolean comment() {
        if (! next("//")) return false;
        accept('/');
        accept('/');
        while (visible()) { }
        return newline();
    }

    // visible = (Cc/Cn/Co/Cs/Zl/Zp)! Uc
    private boolean visible() {
        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        Category cat = Category.get(ch);
        if (cat == Cn || cat == Cc || cat == Co) return false;
        if (cat == Cs || cat == Zl || cat == Zp) return false;
        in += Character.charCount(ch);
        return true;
    }

    // @
    private boolean drop() {
        start = in;
        return true;
    }

    // alpha = letter / digit / '_' / '-'
    private boolean alpha() {
        return letter() || digit() || accept('_') || accept('-');
    }

    // hex = decimal / 'ABCDEFabcdef'
    private boolean hex() {
        return decimal() || accept('A', 'F') || accept('a', 'f');
    }

    // decimal = '0..9'
    private boolean decimal() {
        return accept('0', '9');
    }

    // letter = Lu / Ll / Lt / Lm / Lo
    private boolean letter() {
        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        Category cat = Category.get(ch);
        boolean ok = (
            cat == Lu || cat == Ll || cat == Lt || cat == Lm || cat == Lo
        );
        if (ok) in += Character.charCount(ch);
        return ok;
    }

    // end = #end <>
    private boolean end() {
        return in >= source.length();
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

    // Check for the given (ascii) character next in the input.
    private boolean look(char c) {
        if (in >= source.length()) return false;
        return source.charAt(in) == c;
    }

    // Check for the given (ascii) string next in the input.
    private boolean look(String s) {
        if (in + s.length() > source.length()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (source.charAt(in + i) != s.charAt(i)) return false;
        }
        return true;
    }

    // In general, each node building function takes a number of previous nodes,
    // say x, y, z, and uses the start of x's text range and the end of z's
    // text range to form the text range of the new node.

    // @token (temporary token, used to get the right text range)
    private boolean doToken() {
        if (out >= output.length) {
            output = Arrays.copyOf(output, output.length * 2);
        }
        output[out++] = new Node(null, source, start, in);
        start = in;
        return true;
    }

    // Name: one of @id, @drop, @action, @tag, @err, @end
    private boolean doName(Op op) {
        if (out >= output.length) {
            output = Arrays.copyOf(output, output.length * 2);
        }
        output[out++] = new Node(op, source, start, in);
        start = in;
        return true;
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
    private boolean doInfix(Op op) {
        Node y = output[--out];
        Node x = output[--out];
        Node r = new Node(op, x, y, source, x.start(), y.end());
        output[out++] = r;
        return true;
    }

    // Do postfix operator: one of @1opt, @1any, @1some, ...
    private boolean doPostfix(Op op) {
        Node x = output[--out];
        Node y = new Node(op, x, source, x.start(), in);
        output[out++] = y;
        return true;
    }

    // Convert "[" x "]" into Try x
    private boolean doTry() {
        Node close = output[--out];
        Node x = output[--out];
        Node open = output[--out];
        Node r = new Node(Try, x, source, open.start(), close.end());
        output[out++] = r;
        return true;
    }

    // Bracketed subexpressions have explicit nodes to represent them. This is
    // so that the text extent of a node can always be found by combining the
    // text extents of its children, e.g. in expressions such as (x)y. Note that
    // increasing the text extent of x to (x) wouldn't work when x is an
    // identifier, because then the node's text would not be the name of the id.
    // Bracket nodes are removed later, after parsing.

    // Create a bracket node for ( x ).
    private boolean doBracket() {
        Node close = output[--out];
        Node x = output[--out];
        Node open = output[--out];
        Node r = new Node(Bracket, x, source, open.start(), close.end());
        output[out++] = r;
        return true;
    }

    // Remove bracket nodes from a subtree.
    private Node prune(Node r) {
        if (r == null) return null;
        r.left(prune(r.left()));
        r.right(prune(r.right()));
        if (r.op() == Bracket) return r.left();
        return r;
    }
}
