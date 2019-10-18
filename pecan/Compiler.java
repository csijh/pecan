// Pecan 1.0 compiler. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Compile a Pecan grammar into a given target language, using a simplified
custom approach to pretty-printing. The approach is informed by, but doesn't
closely follow, the paper "A Prettier Printer" by Wadler.

The compile function is run twice for each rule. The first time, the margin is
unlimited, the rule is printed out on one line, the length of the text generated
for each node is stored in the node, and the output is discarded. The second
time, a margin is set, and the lengths stored in the nodes are used to decide
how to print each node.

A template file is read in, and then written back out with the compiled parser
functions inserted into it. The compiled functions are customised for a specific
target language via printf-style strings defined in a <pecan> tag embedded in
the template file. Examples for C are:

<pecan
    comment  = "// %s"
    indent   = ""
    rule     = "bool %s(parser *p) {%n    return %e;%n}"
    rule     = "private boolean %s() {%n    return %e;%n}"
    call     = "%s(p)"
    true     = "true"
    false    = "false"
    or       = "||"
    and      = "&&"
    char     = "'%c'"
    string   = '"%s"'
    unicode2 = "\ u%x"
    unicode4 = "\ U%x"
    tag      = "tag(p,%s)"
    tag      = "(tag(token(p,%d))==%s)"
    act0     = "%s(p)"
    act0     = "push(%s(string(p))"
    act0     = "push(token(%s,string(p)))"
    act2     = "%s(p,%d)"
    act2     = "push2(%s(top1(p),top(p))"
    act      = "act%d(p,%s)"
    go       = "GO"
>

[x ||%n y ||%n z]        one group, all or none.
[[x &&%n y] &&%n z]      many groups, 'fill'

Input = p->ins; posn = p->start, length = p->in - p->start

Use spaces in rule template to determine initial indent and tab.

For each example, the items printed are shown. */


public class Compiler implements Testable {
    private boolean switchTest;
    private StringBuilder output = new StringBuilder();
    private int indent, margin = 80, cursor;
    private boolean startLine;

    private String
        COMMENT = "// %s",
        TAB = "    ",
        RULE = "bool %s() {%n    return %e;%n}",
        RULE1 = "bool %s() { return %e; }",
        CALL = "%s()",
        OR = " || ",
        AND = " && ",
        GO = "go",
        OK = "ok",
        ALT = "alt",
        OPT = "opt",
        SEE = "see",
        HAS = "has",
        NOT = "not",
        TAG = "tag",
        END = "end",
        TRUE = "true",
        FALSE = "false",
        TEXT = "text(\"%s\")",
        SET = "set(\"%s\")",
        SPLIT = "split(\"%s\")",
        RANGE = "range('%c','%c')",
        DROP = "drop(%d)",
        CAT = "cat(%s)",
        MARK = "mark(%s)",
        ACT  = "act%d(%s)";
    String[] ACTS = new String[10];

// ascii visible char, code, uni visible char, uni code


/*
        static bool has(parser *p, bool b);
        static bool not(parser *p, bool b);
        static bool mark(parser *p, int m);
        static bool cat(parser *p, int c);
        static bool range(parser *p, int low, int high);
        static bool text(parser *p, char *s);
        static bool set(parser *p, char *s);
        static bool drop(parser *p, int n);
        static bool end(parser *p);
*/

// print(template, fit, named-params)

    // Do unit testing on previous classes, then check the switch is complete,
    // then run the Compiler unit tests.
    public static void main(String[] args) {
        if (args.length == 0) Stacker.main(args);
        Compiler compiler = new Compiler();
        compiler.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, 0, 1);
            compiler.compile(node);
        }
        compiler.switchTest = false;
        Test.run(compiler, args);
    }

    // Compile the grammar twice.
    public String run(Source grammar) {
        Stacker stacker = new Stacker();
        Node root = stacker.run(grammar);
        if (root.op() == Error) return "Error: " + root.note();
        Transformer transformer = new Transformer();
        transformer.expandSee(root);
        transformer.lift(root);
        int old = margin;
        margin = Integer.MAX_VALUE;
        clear();
        compile(root);
        margin = old;
        clear();
        compile(root);
        return output.toString();
    }

    private int indent(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != ' ') return i;
        }
        return s.length();
    }

    // Generate text from a node. Measure how much was generated. Indents within
    // a node are relative, so restore the indent afterwards.
    private void compile(Node node) {
        int start = output.length();
        int saveIndent = indent;
        switch (node.op()) {
            case Error: case Temp:
            case List: compileList(node); break;
            case Empty: compileEmpty(node); break;
            case Rule: compileRule(node); break;
            case Id: compileId(node); break;
            case Or: compileOr(node); break;
            case And: compileAnd(node); break;
            case Opt: compileOpt(node); break;
            case Any: compileEmpty(node); break;
            case Some: compileEmpty(node); break;
            case See: compileSee(node); break;
            case Has: compileHas(node); break;
            case Not: compileNot(node); break;
            case Tag: compileTag(node); break;
            case Success: compileSuccess(node); break;
            case Fail: compileFail(node); break;
            case End: compileEnd(node); break;
            case Char: compileChar(node); break;
            case Code: compileCode(node); break;
            case String: compileString(node); break;
            case Set: compileSet(node); break;
            case Range: compileRange(node); break;
            case Codes: compileCodes(node); break;
            case Split: compileSplit(node); break;
            case Cat: compileCat(node); break;
            case Mark: compileMark(node); break;
            case Drop: compileDrop(node); break;
            case Act: compileAct(node); break;
            default: throw new Error("Not implemented " + node.op());
        }
        int end = output.length();
        node.LEN(end - start);
        indent = saveIndent;
    }

    // Compile a node, with brackets if the first argument is true.
    private void compileBracket(boolean yes, Node node) {
        if (yes) print("(");
        compile(node);
        if (yes) print(")");
    }

    // Compile a node, with brackets if the node is of the given type.
    private void compileBracket(Op op, Node node) {
        if (node.op() == op) print("(");
        compile(node);
        if (node.op() == op) print(")");
    }

    // Compile a list of rules. The first time, clear the text of each rule.
    private void compileList(Node node) {
        if (switchTest) return;
        String[] lines = node.left().text().split("\n");
        for (String line : lines) {
            printT(COMMENT, line);
            newline(0);
        }
        compile(node.left());
        if (node.right().op() != Empty) newline(0);
        if (margin == Integer.MAX_VALUE) clear();
        compile(node.right());
    }

    // Reached end of list of rules.
    private void compileEmpty(Node node) {
        if (switchTest) return;
    }

    // Compile x = p
    private void compileRule(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        if (fit) printT(RULE1, node.left().name(), node.right());
        else printT(RULE, node.left().name(), node.right());
        newline(0);
    }

    // Compile x
    private void compileId(Node node) {
        if (switchTest) return;
        printT(CALL, node.name());
    }

    // Compile p / q / ...
    private void compileOr(Node node) {
        if (switchTest) return;
        boolean complex = false;
        for (Node n = node; n.op() == Or; n = n.right()) {
            if (n.left().has(FP)) complex = true;
        }
        if (complex) compileComplexOr(node);
        else compileSimpleOr(node);
    }

    // Compile p / q / ... where no alternative can fail after progress. Fill as
    // many alternatives on a line as possible before breaking.
    private void compileSimpleOr(Node node) {
        compileBracket(And, node.left());
        print(OR);
        Node next = node.right();
        if (next.op() == Or) next = next.left();
        boolean fit = next.LEN() <= margin - cursor;
        if (! fit) newline(0);
        compileBracket(And, node.right());
    }

    // Compile p / q / ... where at least one alternative can fail after
    // progress. Add GO to the first alternative and OK to those that need it,
    // and make the sequence a single group so that it is all on one line, or
    // has a line per alternative.
    private void compileComplexOr(Node node) {
        boolean needOK = false;
        boolean fit = node.LEN() <= margin - cursor;
        print(ALT, "(");
        if (! fit) newline(+1);
        print("(");
        printT(CALL, GO);
        print(AND);
        compileBracket(Or, node.left());
        print (")");
        print(OR);
        if (! fit) newline(0);
        needOK = node.left().has(FP);
        node = node.right();
        while (node.op() == Or) {
            if (needOK) {
                print("(");
                printT(CALL, OK);
                print(AND);
                compileBracket(Or, node.left());
                print(")");
            }
            else compileBracket(And, node.left());
            print(OR);
            if (! fit) newline(0);
            needOK = node.left().has(FP);
            node = node.right();
        }
        if (needOK) {
            print("(");
            printT(CALL, OK);
            print(AND);
            compileBracket(Or, node);
            print(")");
        }
        else compileBracket(And, node);
        if (! fit) newline(-1);
        print(")");
    }

    // Compile p q
    private void compileAnd(Node node) {
        if (switchTest) return;
        compileBracket(Or, node.left());
        print(AND);
        Node next = node.right();
        if (next.op() == And) next = next.left();
        boolean fit = next.LEN() <= margin - cursor;
        if (! fit) newline(0);
        compileBracket(Or, node.right());
    }

    // Compile p?
    private void compileOpt(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        if (node.left().has(FP)) {
            print(OPT, "(");
            if (! fit) newline(+1);
            printT(CALL, GO);
            print(AND);
            compileBracket(Or, node.left());
            if (! fit) newline(-1);
            print(")");
        } else {
            print("(");
            compileBracket(And, node.left());
            print(OR, TRUE, ")");
        }
    }

    // Compile [p] when p has no actions or errors.
    private void compileSee(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        print(SEE, "(");
        if (! fit) newline(+1);
        printT(CALL, GO);
        print(AND);
        compileBracket(Or, node.left());
        if (! fit) newline(-1);
        print(")");
    }

    // Compile p&
    private void compileHas(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        print(HAS, "(");
        if (! fit) newline(+1);
        printT(CALL, GO);
        print(AND);
        compileBracket(Or, node.left());
        if (! fit) newline(-1);
        print(")");
    }

    // Compile p!
    private void compileNot(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        print(NOT, "(");
        if (! fit) newline(+1);
        printT(CALL, GO);
        print(AND);
        compileBracket(Or, node.left());
        if (! fit) newline(-1);
        print(")");
    }

    // Compile %t
    private void compileTag(Node node) {
        if (switchTest) return;
        print(TAG, "('", node.name(), "')");
    }

    // Compile ""
    private void compileSuccess(Node node) {
        if (switchTest) return;
        print(TRUE);
    }

    // Compile ''
    private void compileFail(Node node) {
        if (switchTest) return;
        print(FALSE);
    }

    // Compile <>
    private void compileEnd(Node node) {
        if (switchTest) return;
        printT(CALL, END);
    }

    // Compile 'a' or "a"
    private void compileChar(Node node) {
        if (switchTest) return;
        printT(TEXT, node.name());
    }

    // Compile 127
    private void compileCode(Node node) {
        if (switchTest) return;
        print("CODE(" + node.name() + ")");
    }

    // Compile "abc"
    private void compileString(Node node) {
        if (switchTest) return;
        printT(TEXT, node.name());
    }

    // Compile 'abc'
    private void compileSet(Node node) {
        if (switchTest) return;
        printT(SET, node.name());
    }

    // Compile 'a..z'
    // TODO: sort out unicode.
    private void compileRange(Node node) {
        if (switchTest) return;
        char c1 = node.name().charAt(0);
        char c2 = node.name().charAt(node.name().length() - 1);
        printT(RANGE, c1, c2);
    }

    // Compile 0..127
    private void compileCodes(Node node) {
        if (switchTest) return;
        print("CODES(" + node.low() + "," + node.high() + ")");
    }

    // Compile <abc>
    private void compileSplit(Node node) {
        if (switchTest) return;
        print(SPLIT, node.name());
    }

    // Compile Nd
    private void compileCat(Node node) {
        if (switchTest) return;
        print(CAT, node.name());
    }

    // Compile #m
    private void compileMark(Node node) {
        if (switchTest) return;
        printT(MARK, node.name());
    }

    // Compile @3
    private void compileDrop(Node node) {
        if (switchTest) return;
        printT(DROP, node.arity());
    }

    // Compile @3a
    private void compileAct(Node node) {
        if (switchTest) return;
        printT(ACT, node.arity(), node.name());
    }

    // Remove all the output and reset.
    private void clear() {
        output.setLength(0);
        cursor = 0;
        indent = 0;
        startLine = true;
    }

    // Check whether a node is And or Or or both.
    private boolean and(Node node) { return node.op() == And; }
    private boolean or(Node node) { return node.op() == Or; }
    private boolean andOr(Node node) { return and(node) || or(node); }

    // Print text. On newline, remove any trailing spaces, insert the current
    // indent, and increase the indent if there are spaces at the start of the
    // next line.
    private void printI(String s) {
        int n = s.indexOf('\n');
        while (n >= 0) {
            output.append(s.substring(0, n));
        }
    }

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

    private void check(boolean ok, String t, char ch) {
        if (ok) return;
        throw new Error("Unexpected specifier %" + ch + " in " + t);
    }

    // Print non-newline text, keep track of amount printed on current line.
    // Remove initial space if starting a new line.
    private void print(String... ss) {
        for (String s : ss) {
            if (startLine && s.startsWith(" ")) s = s.substring(1);
            output.append(s);
            cursor += s.length();
            startLine = false;
        }
    }

    // Print newline, removing any trailing space, possibly changing indent.
    private void newline(int change) {
        if (output.charAt(output.length() - 1) == ' ') {
            output.setLength(output.length() - 1);
        }
        indent = indent + TAB.length() * change;
        output.append("\n");
        for (int i=0; i<indent; i++) output.append(' ');
        cursor = indent;
        startLine = true;
    }

    // Insert the code into the file.
    private void insert(String file, String code) {
        List<String> lines = null;
        Path p = Paths.get(file);
        PrintStream out = null;
        try {
            lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            out = new PrintStream(new File(file));
        }
        catch (Exception e) { throw new Error(e); }
        boolean skipping = false, templates = false, done = false;
        for (String line : lines) {
            if (line.indexOf("</pecan>") >= 0) skipping = false;
            if (! skipping) out.println(line);
            if (line.indexOf("<pecan") >= 0) {

                //readTemplates();
                skipping = true;
                out.print(code);
            }
        }
        out.close();
    }
}
