// Pecan 1.0 compiler. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;
import static pecan.Node.Count.*;
import static pecan.Formats.Attribute.*;

/* Compile a Pecan grammar into a given target language. A template file is read
in, printf-style format strings are extracted from the attributes in its <pecan>
tag, and then the file is written back out with the compiled parser functions
inserted into it.

The Formats class is used to set up the print formats. For each node, a print
format is attached and the BR flag is set if brackets are required, then the
Pretty class is used to print out the functions. */

class Compiler implements Testable {
    private boolean switchTest;
    private Formats formats;
    private Pretty pretty;

    // Do unit testing on previous classes, then check the switch is complete,
    // then run the Compiler unit tests, using C-like formats.
    public static void main(String[] args) {
        if (args.length == 0) Stacker.main(args);
        if (args.length == 0) Formats.main(args);
        if (args.length == 0) Pretty.main(args);
        if (args.length == 0) Transformer.main(args);
        Compiler compiler = new Compiler();
        compiler.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, null);
            compiler.compile(node);
        }
        compiler.switchTest = false;
        Formats formats = new Formats();
        formats.readLine(1, "file", "declare = 'bool %s();'");
        formats.readLine(1, "file", "comment = '// %s'");
        formats.readLine(1, "file",
            "define = 'bool %s() { %n return %r; %n}'");
        formats.readLine(1, "file", "escape1 = '\\%3o'");
        formats.readLine(1, "file", "escape2 = '\\u%4x'");
        formats.readLine(1, "file", "escape4 = '\\U%8x'");
        formats.fillDefaults(1, "file");
        compiler.formats(formats);
        Test.run(compiler, args);
    }

    // Set the print formats for compiling.
    void formats(Formats fs) { formats = fs; }

    public String run(Source grammar) {
        pretty = new Pretty();
        pretty.tab(formats.get(TAB));
        pretty.escapes(
            formats.get(ESCAPE1),
            formats.get(ESCAPE2),
            formats.get(ESCAPE4)
        );
        Stacker stacker = new Stacker();
        Node root = stacker.run(grammar);
        if (root.op() == Error) return "Error: " + root.note();
        int net = root.left().get(NET);
        if (net != 1) {
            return "Error: first rule produces " + net + " items\n";
        }
        if (root.left().get(NEED) > 0) {
            return "Error: first rule can cause underflow\n";
        }
        String e = checkChoices(root);
        if (e != null) return e;
        if (formats.get(DEFINE).equals("")) {
            return "Error: no print format found for definitions\n";
        }
        Transformer transformer = new Transformer();
        transformer.expandSee(root);
        transformer.lift(root);
        Checker checker = new Checker();
        checker.apply(root);
        declare(root);
        compile(root);
        return pretty.text();
    }

    // Check for and report inaccessible alternatives or actions at the start of
    // left hand alternatives. These are not reported earlier, so that arbitrary
    // transformations are not prevented.
    private String checkChoices(Node node) {
        if (node == null) return null;
        if (node.op() == Or && ! node.left().has(FN)) {
            return node.right().source().error(
            "inaccessible alternative") + "\n";
        }
        else if (node.op() == Or && node.left().has(AB)) {
            return node.left().source().error(
            "left alternative starts with action") + "\n";
        }
        else {
            String e = checkChoices(node.left());
            if (e != null) return e;
            e = checkChoices(node.right());
            if (e != null) return e;
        }
        return null;
    }

    // Print forward declarations for the functions.
    private void declare(Node node) {
        String declare = formats.get(DECLARE);
        if (declare.equals("")) return;
        while (node.op() == List) {
            Node rule = node.left();
            String name = rule.left().rawText();
            printf(declare + "%n", name);
            node = node.right();
        }
        printf("%n");
    }

    // Compile a node by giving it a print format.
    private void compile(Node node) {
        switch (node.op()) {
            case Error: case Temp: break;
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
            case Eot: compileEot(node); break;
            case Char: compileChar(node); break;
            case Text: compileText(node); break;
            case Set: compileSet(node); break;
            case Range: compileRange(node); break;
            case Split: compileSplit(node); break;
            case Cat: compileCat(node); break;
            case Mark: compileMark(node); break;
            case Drop: compileDrop(node); break;
            case Act: compileAct(node); break;
            default: assert false : "Unexpected node type " + node.op(); break;
        }
    }

    // Compile a list of rules.
    private void compileList(Node node) {
        if (switchTest) return;
        String comment = formats.get(COMMENT);
        String[] lines = node.left().text().split("\n");
        for (String line : lines) printf(comment + "%n", line);
        compile(node.left());
        pretty.printf(node.left());
        printf("%n");
        if (node.right().op() != Empty) printf("%n");
        compile(node.right());
    }

    // Reached end of list of rules.
    private void compileEmpty(Node node) {
        if (switchTest) return;
    }

    // Compile x = p
    private void compileRule(Node node) {
        if (switchTest) return;
        node.left().format("%s");
        compile(node.right());
        String define = formats.get(DEFINE);
        define = define.replace("%s","%l");
        define = define.replace("%e","%r");
        node.format(define);
    }

    // Compile x
    private void compileId(Node node) {
        if (switchTest) return;
        node.format(formats.get(ID));
    }

    // Compile p q [%f is a 'fill' version of %n]
    private void compileAnd(Node node) {
        if (switchTest) return;
        compile(node.left());
        compile(node.right());
        bracket(Or, node.left());
        bracket(Or, node.right());
        String and = formats.get(AND);
        node.format("%l " + and + " %f%r");
    }

    // Compile p / q / ...
    private void compileOr(Node node) {
        if (switchTest) return;
        boolean simple = true;
        for (Node n = node; n.op() == Or; n = n.right()) {
            if (n.left().has(FP)) simple = false;
        }
        if (simple) compileSimpleOr(node);
        else compileComplexOr(node);
    }

    // Compile p / q / ... where no alternative can fail after progress. Fill as
    // many alternatives on a line as possible before breaking.
    private void compileSimpleOr(Node node) {
        compile(node.left());
        compile(node.right());
        bracket(And, node.left());
        bracket(And, node.right());
        String or = formats.get(OR);
        node.format("%l " + or + " %f%r");
    }

    // Compile p / q / ... where at least one alternative can fail after
    // progress. Add GO to the first alternative and OK to those that need it,
    // and make the sequence a single group so that it is all on one line, or
    // has a line per alternative.
    private void compileComplexOr(Node node) {
        String alt = formats.get(ALT);
        String go = formats.get(GO);
        String ok = formats.get(OK);
        String or = formats.get(OR);
        compile(node.left());
        bracket(Or, node.left());
        prefix(go, node.left(), true);
        node.format(alt.replace("%l", "%n%t%l " + or + " %g%r%n"));
        boolean needOK = node.left().has(FP);
        node = node.right();
        while (node.op() == Or) {
            compile(node.left());
            if (needOK) {
                bracket(Or, node.left());
                prefix(ok, node.left(), true);
            }
            else bracket(And, node.left());
            node.format("%l " + or + " %g%r");
            needOK = node.left().has(FP);
            node = node.right();
        }
        compile(node);
        if (needOK) {
            bracket(Or, node);
            prefix(ok, node, true);
        }
        else bracket(And, node);
    }

    // Compile p?
    private void compileOpt(Node node) {
        if (switchTest) return;
        String opt = formats.get(OPT);
        String go = formats.get(GO);
        String or = formats.get(OR);
        String t = formats.get(TRUE);
        compile(node.left());
        if (node.left().has(FP)) {
            bracket(Or, node.left());
            prefix(go, node.left(), false);
            node.format(opt.replace("%l","%n%t%l%n"));
        }
        else {
            bracket(And, node.left());
            node.format("(%l " + or + " " + t + ")");
        }
    }

    // Compile [p] when p has no actions or errors.
    private void compileSee(Node node) {
        if (switchTest) return;
        String see = formats.get(SEE);
        String go = formats.get(GO);
        compile(node.left());
        bracket(Or, node.left());
        prefix(go, node.left(), false);
        node.format(see.replace("%l","%n%t%l%n"));
    }

    // Compile p&
    private void compileHas(Node node) {
        if (switchTest) return;
        String has = formats.get(HAS);
        String go = formats.get(GO);
        compile(node.left());
        bracket(Or, node.left());
        prefix(go, node.left(), false);
        node.format(has.replace("%l","%n%t%l%n"));
    }

    // Compile p!
    private void compileNot(Node node) {
        if (switchTest) return;
        String not = formats.get(NOT);
        String go = formats.get(GO);
        compile(node.left());
        bracket(Or, node.left());
        prefix(go, node.left(), false);
        node.format(not.replace("%l","%n%t%l%n"));
    }

    // Compile %t
    private void compileTag(Node node) {
        if (switchTest) return;
        node.format(formats.get(TAG));
    }

    // Compile ""
    private void compileSuccess(Node node) {
        if (switchTest) return;
        node.format(formats.get(TRUE));
    }

    // Compile ''
    private void compileFail(Node node) {
        if (switchTest) return;
        node.format(formats.get(FALSE));
    }

    // Compile <>
    private void compileEot(Node node) {
        if (switchTest) return;
        node.format(formats.get(EOT));
    }

    // Compile 'a' or '\10' or "a"
    private void compileChar(Node node) {
        if (switchTest) return;
        node.format(formats.get(STRING));
    }

    // Compile "abc"
    private void compileText(Node node) {
        if (switchTest) return;
        node.format(formats.get(STRING));
    }

    // Compile 'abc'
    private void compileSet(Node node) {
        if (switchTest) return;
        node.format(formats.get(SET));
    }

    // Compile 'a..z'
    private void compileRange(Node node) {
        if (switchTest) return;
        node.format(formats.get(RANGE));
    }

    // Compile <abc>
    private void compileSplit(Node node) {
        if (switchTest) return;
        node.format(formats.get(SPLIT));
    }

    // Compile Nd
    private void compileCat(Node node) {
        if (switchTest) return;
        node.format(formats.get(CAT));
    }

    // Compile #m
    private void compileMark(Node node) {
        if (switchTest) return;
        node.format(formats.get(MARK));
    }

    // Compile @3
    private void compileDrop(Node node) {
        if (switchTest) return;
        node.format(formats.get(DROP).replace("%d", "" + node.arity()));
    }

    // Compile @3a
    private void compileAct(Node node) {
        if (switchTest) return;
        int a = node.arity();
        node.format(formats.getAct(a).replace("%d", "" + a));
    }

    // Add brackets to the format for a node, if it has the given op.
    private void bracket(Op op, Node node) {
        if (node.op() != op) return;
        node.format("(" + node.format() + ")");
    }

    // Prefix a format with a given call.
    private void prefix(String call, Node node, boolean brackets) {
        String and = formats.get(AND);
        String f = node.format();
        if (brackets) node.format("(" + call + " " + and + " " + f + ")");
        else node.format(call + " " + and + " " + f);
    }

    private void printf(String f, String... ss) { pretty.printf(f, ss); }
}
