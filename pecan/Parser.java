// Pecan 1.0 parser. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Category.*;
import static pecan.Op.*;
import static pecan.Parser.Marker.*;

/* Parse a Pecan source text, assumed to be in UTF-8 format, producing a tree.

The parser is translated from the grammar rules in the comments. The grammar (a)
has repetitions lifted to the top level as separate rules, (b) has no left-hand
alternative starting with an action and (b) has no try construct containing
error markers or actions. That means one rule corresponds to one function, and
actions don't need to be delayed, just switched off during lookahead. The
functions can be hand-maintained to keep this class self-contained and avoid
bootstrap problems.

The grammar is concrete, i.e. it creates extra nodes for postfix operator
symbols, brackets, and bracketed subexpressions, and then removes them at the
end. This allows the text extent of a node to be found uniformly by combining
the text extents of its children. For example, given an expression (x)y, the
combination of two nodes with extents "x" and "y" is "(x)y" and not "x)y". An
alternative, to increase the  extent "x" to "(x)" wouldn't work when x is an
identifier, because then the node's text would not be the name of the id. */

class Parser implements Testable {
    private Source source;
    private Node[] output;
    private int start, in, out, lookahead, marked;
    private Set<Marker> markers = EnumSet.noneOf(Marker.class);
    private int[] save;
    private int top;
    private Set<String> cats;

    public static void main(String[] args) {
        Test.run(new Parser(), args);
    }

    // Parse the grammar, returning a node (possibly an error node).
    public Node run(Source s) {
        source = s;
        if (output == null) output = new Node[1];
        start = in = out = lookahead = marked = 0;
        markers.clear();
        cats = new HashSet<String>();
        save = new int[100];
        top = 0;
        for (Category cat : Category.values()) cats.add(cat.toString());
        boolean ok = grammar();
        if (! ok) {
            Node err = new Node(Error, s, in, in);
            err.note(s.error(in, in, "expecting " + message()));
            return err;
        }
        return prune(output[0]);
    }

    // Error markers, in alphabetical order.
    enum Marker {
        ATOM, BRACKET, DOT, END_OF_TEXT, EQUALS, GREATER_THAN_SIGN, ID, LETTER,
        NEWLINE, OPERATOR, QUOTE, TAG
    }

    // ---------- Generated by <pecan> from the grammar ------------------------

    // grammar = skip rules #end <>
    private boolean grammar() {
        return skip() && rules() && MARK(END_OF_TEXT) && END();
    }

    // rules = (inclusion / rule) (rules @2list)?
    private boolean rules() {
        return (
            ALT(GO() && inclusion() || OR() && rule()) &&
            OPT(GO() && rules() && ACT2(List))
        );
    }

    // inclusion = string endline @1include skip
    private boolean inclusion() {
        return string() && endline() && ACT1(Include) && skip();
    }

    // rule = #id (id / backquote) equals expression newline @2rule skip
    private boolean rule() {
        return MARK(ID) && ALT(
            GO() && id() ||
            OR() && backquote()
        ) && equals() && exp() && newline() && ACT2(Rule) && skip();
    }

    // expression = term (slash expression @2or)?
    private boolean exp() {
        return term() && OPT(
            GO() && slash() && exp() && ACT2(Or)
        );
    }

    // term = factor (term @2and)?
    private boolean term() {
        return factor() && OPT(GO() && term() && ACT2(And));
    }

    // factor = #atom atom postops
    private boolean factor() {
        return MARK(ATOM) && atom() && postops();
    }

    // postops = (postop postops)?
    private boolean postops() {
        return OPT(GO() && postop() && postops());
    }

    // postop = opt @1opt / any @1any / some @1some / has @1has / not @1not
    private boolean postop() {
        switch (NEXT()) {
            case '?': return (opt() && ACT2(Opt));
            case '*': return (any() && ACT2(Any));
            case '+': return (some() && ACT2(Some));
            case '&': return (has() && ACT2(Has));
            case '!': return (not() && ACT2(Not));
            default: return false;
        }
    }

    // atom = bracket / try / id / backquote / act / mark / tag /
    //     codes / code / range / set / string / split / category
    private boolean atom() {
        switch (NEXT()) {
            case '(': return bracket();
            case '[': return try_();
            case '`': return backquote();
            case '@': return act();
            case '#': return mark();
            case '<': return split();
            case '%': return tag();
            case '\'': return ALT(GO() && range() || OR() && set());
            case '"': return string();
            default: return ALT(
                GO() && category() ||
                OR() && id() ||
                OR() && codes() ||
                OR() && code()
            );
        }
    }

    // bracket = open expression close @3bracket
    private boolean bracket() {
        return open() && exp() && close() && ACT3(Bracketed);
    }

    // try = sopen expression sclose @3try
    private boolean try_() {
        return sopen() && exp() && sclose() && ACT3(Try);
    }

    // id = (cat alpha!)! letter alphas @id blank
    private boolean id() {
        return NOT(
            GO() && cat() && NOT(GO() && alpha())
        ) && letter() && alphas() && ACT(Id) && blank();
    }

    // backquote = "`" nobquotes #quote "`" @id blank
    private boolean backquote() {
        return (
            CHAR('`') && nobquotes() && MARK(QUOTE) && CHAR('`') &&
            ACT(Id) && blank()
        );
    }

    // act = '@' decimals alphas @act blank
    private boolean act() {
        return CHAR('@') && decimals() && alphas() && ACT(Act) && blank();
    }

    // mark = "#" initial alphas @mark blank
    private boolean mark() {
        return CHAR('#') && initial() && alphas() && ACT(Mark) && blank();
    }

    // tag = "%" initial alphas @tag blank
    private boolean tag() {
        return CHAR('%') && initial() && alphas() && ACT(Tag) && blank();
    }

    // codes = [digits '.'] #dot '.' digits @range blank
    private boolean codes() {
        return TRY(
            GO() && digits() && CHAR('.')
        ) && MARK(DOT) && CHAR('.') && digits() && ACT(Range) && blank();
    }

    // code = digits @code blank
    private boolean code() {
        return digits() && ACT(Code) && blank();
    }

    // range = ["'" noquote ".."] noquote "'" @range blank
    private boolean range() {
        return TRY(
            GO() && CHAR('\'') && noquote() && STRING("..")
        ) && noquote() && CHAR('\'') && ACT(Range) && blank();
    }

    // set = "'" noquotes #quote "'" @set blank
    private boolean set() {
        return (
            CHAR('\'') && noquotes() && MARK(QUOTE) &&
            CHAR('\'') && ACT(Set) && blank()
        );
    }

    // string = '"' nodquotes #quote '"' @string blank
    private boolean string() {
        return (
            CHAR('"') && nodquotes() && MARK(QUOTE) &&
            CHAR('"') && ACT(String) && blank()
        );
    }

    // split = '<' noangles #gt '>' @split) blank
    private boolean split() {
        return (
            CHAR('<') && noangles() && MARK(GREATER_THAN_SIGN) && CHAR('>') &&
            ACT(Split) && blank()
        );
    }

    // equals = #equals "=" gap
    private boolean equals() {
        return MARK(EQUALS) && CHAR('=') && gap();
    }

    // slash = #op "/" gap
    private boolean slash() {
        return MARK(OPERATOR) && CHAR('/') && gap();
    }

    // has = "&" @op blank
    private boolean has() {
        return CHAR('&') && ACT(Op) && blank();
    }

    // not = "!" @op blank
    private boolean not() {
        return CHAR('!') && ACT(Op) && blank();
    }

    // opt = "?" @op blank
    private boolean opt() {
        return CHAR('?') && ACT(Op) && blank();
    }

    // any = "*" @op blank
    private boolean any() {
        return CHAR('*') && ACT(Op) && blank();
    }

    // some = "+" @op blank
    private boolean some() {
        return CHAR('+') && ACT(Op) && blank();
    }

    // open = "(" @bracket gap
    private boolean open() {
        return CHAR('(') && ACT(Bracket) && gap();
    }

    // sopen = "[" @bracket gap
    private boolean sopen() {
        return CHAR('[') && ACT(Bracket) && gap();
    }

    // close = ")" @bracket blank
    private boolean close() {
        return MARK(BRACKET) && CHAR(')') && ACT(Bracket) && blank();
    }

    // sclose = "]" @bracket blank
    private boolean sclose() {
        return MARK(BRACKET) && CHAR(']') && ACT(Bracket) && blank();
    }

    // category = [cat alpha!] @cat blank
    private boolean category() {
        return TRY(
            GO() && cat() && NOT(GO() && alpha())
        ) && ACT(Cat) && blank();
    }

    // cat = "Uc" / "Cc" / "Cf" / "Cn" / "Co" / "Cs" / "Ll" / "Lm" / "Lo" /
    //    "Lt" / "Lu" / "Mc" / "Me" / "Mn" / "Nd" / "Nl" / "No" / "Pc" / "Pd" /
    //    "Pe" / "Pf" / "Pi" / "Po" / "Ps" / "Sc" / "Sk" / "Sm" / "So" / "Zl" /
    //    "Zp" / "Zs"
    // Hand optimised.
    private boolean cat() {
        if (in >= source.length() - 2) return false;
        boolean ok = cats.contains(source.substring(in, in + 2));
        if (ok) in = in + 2;
        return ok;
    }

    // blank = spaces [endline spaces '=/)]' &]? @
    private boolean blank() {
        return spaces() && OPT(GO() && TRY(
            GO() && endline() && spaces() && HAS(GO() && SET("=/)]"))
        )) && ACT();
    }

    // gap = spaces (newline spaces)? @
    private boolean gap() {
        return spaces() && OPT(GO() && newline() && spaces()) && ACT();
    }

    // skip = ((space / comment / newline) @ skip)?
    private boolean skip() {
        return OPT(GO() &&
            ALT(GO() &&
                space() || OR() && comment() || OR() && newline()
            ) && ACT() && skip()
        );
    }

    // comment = "--" visibles newline
    private boolean comment() {
        return STRING("--") && visibles() && newline();
    }

    // newline = #newline endline @
    private boolean newline() {
        return MARK(NEWLINE) && endline() && ACT();
    }

    // space = ' '
    private boolean space() {
        return CHAR(' ');
    }

    // spaces = space*
    private boolean spaces() {
        return OPT(GO() && space() && spaces());
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

    // visibles = visible*
    private boolean visibles() {
        return OPT(GO() && visible() && visibles());
    }

    // alpha = letter / Nd / '_' / '-'
    private boolean alpha() {
        return letter() || CAT(Nd) || CHAR('_') || CHAR('-');
    }

    // alphas = alpha*
    private boolean alphas() {
        return OPT(GO() && alpha() && alphas());
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

    // initial = #letter letter
    private boolean initial() {
        return MARK(LETTER) && letter();
    }

    // decimal = '0..9'
    private boolean decimal() {
        return RANGE('0', '9');
    }

    // decimals = decimal*
    private boolean decimals() {
        return OPT(GO() && decimal() && decimals());
    }

    // hex = decimal / 'ABCDEFabcdef'
    private boolean hex() {
        return ALT(GO() && decimal() || OR() && SET("ABCDEFabcdef"));
    }

    // hexes = hex*
    private boolean hexes() {
        return OPT(GO() && hex() && hexes());
    }

    // digits = ('1..9' decimals) / '0' hexes
    private boolean digits() {
        return ALT(
            GO() && RANGE('1','9') && decimals() || OR() && CHAR('0') && hexes()
        );
    }

    // noquote = "'"! visible
    private boolean noquote() {
        return NOT(GO() && CHAR('\'')) && visible();
    }

    // noquotes = ("'"! visible)*
    private boolean noquotes() {
        return OPT(GO() && NOT(GO() && CHAR('\'')) && visible() && noquotes());
    }

    // nodquotes = ('"'! visible)*
    private boolean nodquotes() {
        return OPT(GO() && NOT(GO() && CHAR('"')) && visible() && nodquotes());
    }

    // nobquotes = ("`"! visible)*
    private boolean nobquotes() {
        return OPT(GO() && NOT(GO() && CHAR('`')) && visible() && nobquotes());
    }

    // noangles = ('>'! visible)*
    private boolean noangles() {
        return OPT(GO() && NOT(GO() && CHAR('>')) && visible() && noangles());
    }

    // endline = 13? 10
    private boolean endline() {
        return (CHAR('\r') || true) && CHAR('\n');
    }

    // ---------- Support functions --------------------------------------------

    // Prepare for a choice or lookahead by recording the input position.
    private boolean GO() {
        save[top++] = in;
        return true;
    }

    // Check an alternative to see whether to try the next one.
    private boolean OR() {
        return in == save[top-1];
    }

    // Check the result of a choice and pop the saved position.
    private boolean ALT(boolean b) {
        --top;
        return b;
    }

    // Check a result, make it success if no progress, and pop saved position.
    private boolean OPT(boolean b) {
        --top;
        return b || in == save[top];
    }

    // Backtrack to saved position.
    private boolean HAS(boolean b) {
        in = save[--top];
        return b;
    }

    // Backtrack to saved position and negate result.
    private boolean NOT(boolean b) {
        in = save[--top];
        return !b;
    }

    // Backtrack on failure.
    private boolean TRY(boolean b) {
        if (b) --top;
        else in = save[--top];
        return b;
    }

    // Record an error marker for the current input position.
    private boolean MARK(Marker m) {
        if (lookahead > 0) return true;
        if (marked > in) throw new Error("marked " + marked + " in " + in);
        if (marked < in) {
            markers.clear();
            marked = in;
        }
        markers.add(m);
        return true;
    }

    // Nd
    private boolean CAT(Category c) {
        if (in >= source.length()) return false;
        int ch = source.codePointAt(in);
        Category cat = Category.get(ch);
        if (cat != c) return false;
        in += Character.charCount(ch);
        return true;
    }

    // @
    private boolean ACT() {
        start = in;
        return true;
    }

    // Check if a character (ascii) appears next in the input.
    private boolean CHAR(char ch) {
        if (in >= source.length()) return false;
        if (source.charAt(in) != ch) return false;
        in++;
        return true;
    }

    // Check if a character (ascii) in a given range appears next in the input.
    private boolean RANGE(char first, char last) {
        if (in >= source.length()) return false;
        if (source.charAt(in) < first || source.charAt(in) > last) return false;
        in++;
        return true;
    }

    // Check for the given (ascii) string next in the input.
    private boolean STRING(String s) {
        if (in + s.length() > source.length()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (source.charAt(in + i) != s.charAt(i)) return false;
        }
        in += s.length();
        return true;
    }

    // Check if a character (ascii) in a given range appears next in the input.
    private boolean SET(String s) {
        if (in >= source.length()) return false;
        char ch = source.charAt(in);
        boolean found = false;
        for (int i = 0; i < s.length() && ! found; i++) {
            if (ch == s.charAt(i)) found = true;
        }
        if (found) in++;
        return found;
    }

    // Return the next character in the input.
    private char NEXT() {
        if (in >= source.length()) return '\0';
        return source.charAt(in);
    }

    private boolean END() {
        return in >= source.length();
    }


    // @...
    private boolean ACT(Op op) {
        if (out >= output.length) {
            output = Arrays.copyOf(output, output.length * 2);
        }
        output[out++] = new Node(op, source, start, in);
        start = in;
        return true;
    }

    // @1...
    private boolean ACT1(Op op) {
        Node x = output[--out];
        Node y = new Node(op, x, source, x.start(), x.end());
        output[out++] = y;
        return true;
    }

    // @2...
    private boolean ACT2(Op op) {
        Node y = output[--out];
        Node x = output[--out];
        Node r = new Node(op, x, y, source, x.start(), y.end());
        output[out++] = r;
        return true;
    }

    // @3... used for bracketed subexpressions (x) or [x], discarding brackets.
    private boolean ACT3(Op op) {
        Node close = output[--out];
        Node x = output[--out];
        Node open = output[--out];
        Node y = new Node(op, x, source, open.start(), close.end());
        output[out++] = y;
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

    // Remove postfix operator, bracket, and bracketed expression nodes.
    private Node prune(Node r) {
        if (r == null) return null;
        Op op = r.op();
        r.left(prune(r.left()));
        if (op == Opt || op == Any || op == Some || op == Has || op == Not) {
            r.right(null);
        }
        else r.right(prune(r.right()));
        if (op == Bracketed) return r.left();
        return r;
    }
}
