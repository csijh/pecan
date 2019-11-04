// Pecan 1.0 parser. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Category.*;
import static pecan.Op.*;
import static pecan.Parser.Marker.*;
import java.io.*;

/* Parse a Pecan source text, assumed to be in UTF-8 format, producing a tree.

The parser is translated from the grammar rules in the comments. The grammar (a)
has repetitions lifted to the top level as separate rules, and (b) has no see
construct containing error markers or actions. That means one rule corresponds
to one function. The functions can be hand-maintained to keep this class
self-contained and avoid bootstrap problems.

The grammar is concrete, i.e. it creates extra nodes for postfix operator
symbols, brackets, and bracketed subexpressions, and then removes them at the
end. This allows the text extent of a node to be found uniformly by combining
the text extents of its children. For example, given an expression (x)y, the
combination of two nodes with extents "x" and "y" is "(x)y" and not "x)y".
Increasing the extent of the first node to "(x)" wouldn't work when x is an
identifier, because then the node's text would not be the name of the id. */

class Parser implements Testable {
    private Source input;
    private Node[] output;
    private Set<Marker> markers;
    private int[] saves;
    private Set<String> cats;
    private int start, in, out, look, marked, save;
    private Op Postop = Temp, Bracket = Temp, Bracketed = Temp, Include = Temp;

    // Test Source and Node as well as Parser.
    public static void main(String[] args) {
        if (args.length == 0) Category.main(args);
        if (args.length == 0) Node.main(args);
        Test.run(new Parser(), args);
    }

    // Parse the grammar, returning a node (possibly an error node).
    public Node run(Source s) {
        input = s;
        if (output == null) output = new Node[1];
        if (markers == null) markers = EnumSet.noneOf(Marker.class);
        markers.clear();
        saves = new int[100];
        cats = new HashSet<String>();
        for (Category cat : Category.values()) cats.add(cat.toString());
        start = in = out = look = marked = save = 0;
        boolean ok = grammar();
        if (! ok) {
            Node err = new Node(Error, s.sub(in, in));
            if (marked < in) markers.clear();
            Source point = s.sub(in, in);
            err.note(point.error(message()));
            return err;
        }
        assert(save == 0);
        assert(out == 1);
        Node root = output[0];
        root = prune(root);
        root = merge(root);
        return root;
    }

    // Error markers, in alphabetical order. In messages, their names are made
    // lower case and underscores are replaced with spaces.
    enum Marker {
        ATOM, BRACKET, DIGIT, DOT, END_OF_TEXT, EQUALS, GREATER_THAN_SIGN, ID,
        NAME, NEWLINE, OPERATOR, QUOTE, TAG
    }

    // ---------- Generated by <pecan> from the grammar ------------------------

    // grammar = skip rules #end <>
    private boolean grammar() {
        return skip() && rules() && MARK(END_OF_TEXT) && EOT();
    }

    // rules = (inclusion / rule) (rules / @empty) @2list
    private boolean rules() {
        return (
            ALT(DO() && inclusion() || OR() && rule()) &&
            ALT(DO() && rules() || ACT(Empty)) &&
            ACT2(List)
        );
    }

    // inclusion = include newline @include skip
    private boolean inclusion() {
        return include() && newline() && ACT1(Include) && skip();
    }

    // rule = #id id equals expression newline @2rule skip
    private boolean rule() {
        return MARK(ID) && id() && equals() && exp() && newline() &&
        ACT2(Rule) && skip();
    }

    // expression = term (slash expression @2or)?
    private boolean exp() {
        return term() && OPT(
            DO() && slash() && exp() && ACT2(Or)
        );
    }

    // term = factor (term @2and)?
    private boolean term() {
        return factor() && OPT(DO() && term() && ACT2(And));
    }

    // factor = #atom atom postops
    private boolean factor() {
        return MARK(ATOM) && atom() && postops();
    }

    // postops = postop* = (postop postops)?
    private boolean postops() {
        return OPT(DO() && postop() && postops());
    }

    // postop = opt @2opt / any @2any / some @2some / has @2has / not @2not
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

    // atom = bracketed / see / act / mark / tag / split / range / set /
    //     string / category / id
    private boolean atom() {
        switch (NEXT()) {
            case '(': return bracketed();
            case '[': return see();
            case '@': return act();
            case '#': return mark();
            case '%': return tag();
            case '<': return split();
            case '\'': return ALT(DO() && range() || OR() && set());
            case '"': return string();
            default: return ALT(
                DO() && category() ||
                OR() && id()
            );
        }
    }

    // bracketed = open expression close @3bracketed
    private boolean bracketed() {
        return open() && exp() && close() && ACT3(Bracketed);
    }

    // see = sopen expression sclose @3see
    private boolean see() {
        return sopen() && exp() && sclose() && ACT3(See);
    }

    // id = (cat alpha!)! name @id blank
    private boolean id() {
        return NOT(
            DO() && cat() && NOT(DO() && alpha())
        ) && name() && ACT(Id) && blank();
    }

    // act = '@' decimals name? @act blank
    private boolean act() {
        return CHAR('@') && decimals() && OPT(DO() && name()) && ACT(Act) &&
        blank();
    }

    // mark = "#" #name name @mark blank
    private boolean mark() {
        return CHAR('#') && MARK(NAME) && name() && ACT(Mark) && blank();
    }

    // tag = "%" #name name @tag blank
    private boolean tag() {
        return CHAR('%') && MARK(NAME) && name() && ACT(Tag) && blank();
    }

    // range = ["'" noquote ".."] noquote #quote "'" @range blank
    private boolean range() {
        return TRY(
            DO() && CHAR('\'') && noquote() && STRING("..")
        ) && noquote() && MARK(QUOTE) && CHAR('\'') && ACT(Range) && blank();
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
            CHAR('"') && ACT(Text) && blank()
        );
    }

    // split = '<' noangles #gt '>' @split blank
    private boolean split() {
        return (
            CHAR('<') && noangles() && MARK(GREATER_THAN_SIGN) && CHAR('>') &&
            ACT(Split) && blank()
        );
    }

    // include = "{" nocurlies #bracket "}" @string blank
    private boolean include() {
        return CHAR('{') && nocurlies() && MARK(BRACKET) && CHAR('}') &&
        ACT(Text) && blank();
    }

    // equals = #equals "=" gap
    private boolean equals() {
        return MARK(EQUALS) && CHAR('=') && gap();
    }

    // slash = #operator "/" gap
    private boolean slash() {
        return MARK(OPERATOR) && CHAR('/') && gap();
    }

    // has = "&" @postop blank
    private boolean has() {
        return CHAR('&') && ACT(Postop) && blank();
    }

    // not = "!" @postop blank
    private boolean not() {
        return CHAR('!') && ACT(Postop) && blank();
    }

    // opt = "?" @postop blank
    private boolean opt() {
        return CHAR('?') && ACT(Postop) && blank();
    }

    // any = "*" @postop blank
    private boolean any() {
        return CHAR('*') && ACT(Postop) && blank();
    }

    // some = "+" @postop blank
    private boolean some() {
        return CHAR('+') && ACT(Postop) && blank();
    }

    // open = "(" @bracket gap
    private boolean open() {
        return CHAR('(') && ACT(Bracket) && gap();
    }

    // sopen = "[" @bracket gap
    private boolean sopen() {
        return CHAR('[') && ACT(Bracket) && gap();
    }

    // close = #bracket ")" @bracket blank
    private boolean close() {
        return MARK(BRACKET) && CHAR(')') && ACT(Bracket) && blank();
    }

    // sclose = #bracket "]" @bracket blank
    private boolean sclose() {
        return MARK(BRACKET) && CHAR(']') && ACT(Bracket) && blank();
    }

    // category = [cat alpha!] @cat blank
    private boolean category() {
        return TRY(
            DO() && cat() && NOT(DO() && alpha())
        ) && ACT(Cat) && blank();
    }

    // cat = "Uc" / "Cc" / "Cf" / "Cn" / "Co" / "Cs" / "Ll" / "Lm" / "Lo" /
    //    "Lt" / "Lu" / "Mc" / "Me" / "Mn" / "Nd" / "Nl" / "No" / "Pc" / "Pd" /
    //    "Pe" / "Pf" / "Pi" / "Po" / "Ps" / "Sc" / "Sk" / "Sm" / "So" / "Zl" /
    //    "Zp" / "Zs"
    // Hand optimised.
    private boolean cat() {
        if (in >= input.length() - 2) return false;
        boolean ok = cats.contains(input.substring(in, in + 2));
        if (ok) in = in + 2;
        return ok;
    }

    // blank = spaces [endline spaces '=/)]' &]? @
    private boolean blank() {
        return spaces() && OPT(DO() && TRY(
            DO() && endline() && spaces() && HAS(DO() && SET("=/)]"))
        )) && ACT();
    }

    // gap = spaces (newline spaces)? @
    private boolean gap() {
        return spaces() && OPT(DO() && newline() && spaces()) && ACT();
    }

    // skip = (...)* = ((space / comment / newline) @ skip)?
    private boolean skip() {
        return OPT(DO() &&
            ALT(DO() &&
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

    // spaces = space* = (space spaces)?
    private boolean spaces() {
        return OPT(DO() && space() && spaces());
    }

    // literal = escape / visible
    private boolean literal() {
        return ALT((DO() && escape()) || (OR() && visible()));
    }

    // literals = literal* = (literal literals)?
    private boolean literals() {
        return OPT(DO() && literal() && literals());
    }

    // visible = (Cc/Cn/Co/Cs/Zl/Zp)! Uc
    private boolean visible() {
        return NOT(DO() && CATS(Cc,Cn,Co,Cs,Zl,Zp)) && CATS(Uc);
    }

    // visibles = visible* = (visible visibles)&
    private boolean visibles() {
        return OPT(DO() && visible() && visibles());
    }

    // escape = backslash (digits ';'? / 'rnqdb')
    private boolean escape() {
        return backslash() && (
        (digits() && (CHAR(';') || true)) || SET("rnqdb"));
    }

    // backslash = '\'
    private boolean backslash() {
        return CHAR('\\');
    }

    // alpha = letter / Nd / '-' / '_'
    private boolean alpha() {
        return letter() || CATS(Nd) || CHAR('-') || CHAR('_');
    }

    // alphas = alpha* = (alpha alphas)?
    private boolean alphas() {
        return OPT(DO() && alpha() && alphas());
    }

    // letter = Lu / Ll / Lt / Lm / Lo
    private boolean letter() {
        return CATS(Lu, Ll, Lt, Lm, Lo);
    }

    // name = letter alphas / '`' nobquotes #quote '`'
    private boolean name() {
        return (
            (letter() && alphas()) ||
            (CHAR('`') && nobquotes() && MARK(QUOTE) && CHAR('`'))
        );
    }

    // decimal = '0..9'
    private boolean decimal() {
        return RANGE('0', '9');
    }

    // decimals = decimal* = (decimal decimals)?
    private boolean decimals() {
        return OPT(DO() && decimal() && decimals());
    }

    // hex = decimal / 'ABCDEFabcdef'
    private boolean hex() {
        return ALT(DO() && decimal() || OR() && SET("ABCDEFabcdef"));
    }

    // hexes = hex* = (hex hexes)?
    private boolean hexes() {
        return OPT(DO() && hex() && hexes());
    }

    // digits = ('1..9' decimals) / '0' hexes
    private boolean digits() {
        return ALT(
            DO() && RANGE('1','9') && decimals() || OR() && CHAR('0') && hexes()
        );
    }

    // noquote = "'"! literal
    private boolean noquote() {
        return NOT(DO() && CHAR('\'')) && literal();
    }

    // noquotes = noquote* = (noquote noquotes)?
    private boolean noquotes() {
        return OPT(DO() && noquote() && noquotes());
    }

    // nodquotes = ('"'! literal)* = (... nodquotes)?
    private boolean nodquotes() {
        return OPT(DO() && NOT(DO() && CHAR('"')) && literal() && nodquotes());
    }

    // nobquotes = ('`'! literal)* = (... nobquotes)?
    private boolean nobquotes() {
        return OPT(DO() && NOT(DO() && CHAR('`')) && literal() && nobquotes());
    }

    // noangles = ('>'! literal)* = (... noangles)?
    private boolean noangles() {
        return OPT(DO() && NOT(DO() && CHAR('>')) && literal() && noangles());
    }

    // nocurlies = ('}'! literal)* = (... nocurlies)?
    private boolean nocurlies() {
        return OPT(DO() && NOT(DO() && CHAR('}')) && literal() && nocurlies());
    }

    // endline = '\13'? '\10'
    private boolean endline() {
        return (CHAR('\r') || true) && CHAR('\n');
    }

    // ---------- Support functions --------------------------------------------

    // Prepare for a choice or lookahead by recording the input position.
    private boolean DO() {
        if (save >= saves.length) {
            saves = Arrays.copyOf(saves, saves.length * 2);
        }
        saves[save++] = in;
        return true;
    }

    // Check an alternative to see whether to try the next one.
    private boolean OR() {
        return in == saves[save-1];
    }

    // Pop a saved position, and return the result of a choice.
    private boolean ALT(boolean b) {
        --save;
        return b;
    }

    // After parsing x in x?, pop saved position, and adjust the result.
    private boolean OPT(boolean b) {
        --save;
        return b || in == saves[save];
    }

    // Backtrack to saved position, and return result of lookahead.
    private boolean HAS(boolean b) {
        in = saves[--save];
        return b;
    }

    // Backtrack to saved position and negate result.
    private boolean NOT(boolean b) {
        in = saves[--save];
        return !b;
    }

    // Backtrack on failure.
    private boolean TRY(boolean b) {
        if (b) --save;
        else in = saves[--save];
        return b;
    }

    // Record an error marker for the current input position.
    private boolean MARK(Marker m) {
        if (look > 0) return true;
        if (marked > in) throw new Error("marked " + marked + " in " + in);
        if (marked < in) {
            markers.clear();
            marked = in;
        }
        markers.add(m);
        return true;
    }

    // Choice of categories, e.g. Lu / Ll / Lt / Lm / Lo
    private boolean CATS(Category... cs) {
        if (in >= input.length()) return false;
        int len = input.nextLength(in);
        int ch = input.nextChar(in, len);
        Category cat = Category.get(ch);
        boolean ok = false;
        for (Category c : cs) if (cat == c || c == Uc) ok = true;
        if (! ok) return false;
        in += len;
        return true;
    }

    // Check if a character (ascii) appears next in the input.
    private boolean CHAR(char ch) {
        if (in >= input.length()) return false;
        int len = input.nextLength(in);
        int code = input.nextChar(in, len);
        if (code != ch) return false;
        in += len;
        return true;
    }

    // Check if a character in a given range appears next in the input.
    private boolean RANGE(char first, char last) {
        if (in >= input.length()) return false;
        int len = input.nextLength(in);
        int code = input.nextChar(in, len);
        if (code < first || code > last) return false;
        in += len;
        return true;
    }

    // Check for the given string next in the input.
    private boolean STRING(String s) {
        int n = input.match(s, in);
        in += n;
        return (n > 0);
    }

    // Check if a character (ascii) in a given range appears next in the input.
    private boolean SET(String s) {
        if (in >= input.length()) return false;
        int ch = input.charAt(in);
        boolean found = false;
        for (int i = 0; i < s.length() && ! found; i++) {
            if (ch == s.charAt(i)) found = true;
        }
        if (found) in++;
        return found;
    }

    // Return the next character in the input.
    private char NEXT() {
        if (in >= input.length()) return '\0';
        return (char) input.charAt(in);
    }

    private boolean EOT() {
        return in >= input.length();
    }

    // @
    private boolean ACT() {
        if (look > 0) return true;
        start = in;
        return true;
    }

    // @a
    private boolean ACT(Op op) {
        if (look > 0) return true;
        if (out >= output.length) {
            output = Arrays.copyOf(output, output.length * 2);
        }
        output[out++] = new Node(op, input.sub(start, in));
        start = in;
        return true;
    }

    // @1a. Do inclusion of sub-grammar here.
    private boolean ACT1(Op op) {
        if (look > 0) return true;
        start = in;
        Node x = output[--out];
        if (op == Include) {
            String file = input.relativePath(x.name());
            Source s2 = new Source(new File(file));
            Parser parser2 = new Parser();
            Node g = parser2.run(s2);
            if (g.op() == Error) {
                System.out.println(g.note());
                System.exit(1);
            }
            Node include = new Node(Include, g, x.source());
            output[out++] = include;
            return true;
        }
        Node y = new Node(op, x, x.source());
        output[out++] = y;
        return true;
    }

    // @2a
    private boolean ACT2(Op op) {
        if (look > 0) return true;
        start = in;
        Node y = output[--out];
        Node x = output[--out];
        Node r = new Node(op, x, y, input.sub(x.source(), y.source()));
        output[out++] = r;
        return true;
    }

    // @3a used for bracketed subexpressions (x) or [x], discarding brackets.
    private boolean ACT3(Op op) {
        if (look > 0) return true;
        start = in;
        Node close = output[--out];
        Node x = output[--out];
        Node open = output[--out];
        Node y = new Node(op, x, input.sub(open.source(), close.source()));
        output[out++] = y;
        return true;
    }

    // Produce an error message from the markers at the current input position.
    private String message() {
        if (markers.size() == 0) return "";
        String s = "expecting ";
        boolean first = true;
        for (Marker m : markers) {
            if (! first) s = s + ", ";
            first = false;
            s = s + m.toString().toLowerCase().replaceAll("_"," ");
        }
        return s;
    }

    // Remove temporary nodes (brackets, postix, inclusions)
    private Node prune(Node r) {
        if (r == null) return null;
        Op op = r.op();
        r.left(prune(r.left()));
        if (r.left() == null) r.ref(prune(r.ref()));
        else r.right(prune(r.right()));
        if (r.right() != null && r.right().op() == Temp) r.right(null);
        if (op == Temp) return r.left();
        return r;
    }

    // Merge lists caused by inclusions.
    private Node merge(Node r) {
        if (r.op() == Empty) return r;
        assert(r.op() == List);
        if (r.left().op() == Rule) { merge(r.right()); return r; }
        Node n = r.left();
        assert(n.op() == List);
        r.left(n.left());
        if (n.right().op() == Empty) return r;
        n.left(n.right());
        n.right(r.right());
        r.right(n);
        merge(r);
        return r;
    }
}
