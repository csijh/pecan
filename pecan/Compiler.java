// Pecan 1.0 compiler. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Compile a Pecan grammar into a given target language, using a simplified
approach to pretty-printing.

The compile function is run twice. The first time, the margin is set large, each
rule is printed out on one line, the length of the text generated for each node
is stored in the node, and the output is discarded. The second time, the margin
is set small, and the lengths in the nodes are used to decide how to print each
node. */

// tab = 4
// indent = 0
// margin = 80
// comment = // %s
// function-start = bool %s() {
// function-body = return %s;
// function-end = }
// call = ()
// true = true
// false = false
// or = ||
// and = &&
// char = '%s'
// string = "%s"
// ALT = ALT
// DO = DO
// OR = OR
// OPT = OPT
// TRY = TRY
// HAS = HAS
// NOT = NOT
// TAG = TAG
// END = END
// CHAR = CHAR
// CODE = CODE
// STRING = STRING
// SET = SET
// RANGE = RANGE
// CODES = CODES
// SPLIT = SPLIT
// CAT = CAT
// MARK = MARK
// DROP = DROP
// ACT = ACT
// ACT1 = ACT1
// ACT2 = ACT2
// ACT3 = ACT3

// -- C ->
// // C
//
// X = P ->
// bool X() {
//     return P;
// }
//
// X -> X()
// P / Q -> P || Q
// P / Q -> ALT(DO() && P || OR() && Q())
//
// P Q -> P && Q

public class Compiler implements Testable {
    private boolean switchTest;
    private StringBuilder output = new StringBuilder();
    private int tab = 4, indent, margin = 80, cursor;
    private boolean startLine;

    // Do unit testing on the Stacker class, then check the switch is complete,
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
        transformer.expandTry(root);
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

    // Generate text from a node. Measure how much was generated.
    private void compile(Node node) {
        int start = output.length();
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
            case Try: compileTry(node); break;
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
    }

    // Compile a list of rules. The first time, clear the text of each rule.
    private void compileList(Node node) {
        if (switchTest) return;
        String[] lines = node.left().text().split("\n");
        for (String line : lines) {
            print("// ");
            print(line);
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
//        if (node.right().op() == Any) { compileAny(node); return; }
//        if (node.right().op() == Some) { compileSome(node); return; }
        boolean fit = node.LEN() <= margin - cursor;
        print("bool ");
        print(node.left().name());
        print("() { ");
        if (! fit) newline(+1);
        print("return ");
        compile(node.right());
        print(";");
        if (! fit) newline(-1);
        print(" }");
        newline(0);
    }
/*
    // Compile a lifted rule xs = x* = (x xs)?
    private void compileAny(Node node) {
        boolean fit = node.LEN() <= margin - cursor;
        if (node.left().has(FP)) {
            print("OPT(");
            if (! fit) newline(+1);
            print("DO() && ");
            compile(node.left());
            if (! fit) newline(-1);
            print(")");
        } else {
            print("(");
            compile(node.left());
            print(" || true)");
        }
    }
*/
    // Compile x
    private void compileId(Node node) {
        if (switchTest) return;
        print(node.name());
        print("()");
    }

    // Compile p / q / ...
    private void compileOr(Node node) {
        if (switchTest) return;
        boolean complex = false, needOR = false;
        for (Node n = node; n.op() == Or; n = n.right()) {
            if (n.left().has(FP)) complex = true;
        }
        if (complex) {
            boolean fit = node.LEN() <= margin - cursor;
            print("ALT(");
            if (! fit) newline(+1);
            print("DO() && ");
            compile(node.left());
            print(" || ");
            if (! fit) newline(0);
            needOR = node.left().has(FP);
            node = node.right();
            while (node.op() == Or) {
                if (needOR) print("OR() && ");
                compile(node.left());
                print(" || ");
                if (! fit) newline(0);
                needOR = node.left().has(FP);
                node = node.right();
            }
            if (needOR) print("OR() && ");
            compile(node);
            if (! fit) newline(-1);
            print(")");
        } else {
            compile(node.left());
            print(" || ");
            Node next = node.right();
            if (next.op() == Or) next = next.left();
            boolean fit = next.LEN() <= margin - cursor;
            if (! fit) newline(0);
            compile(node.right());
        }
    }

    // Compile p q
    private void compileAnd(Node node) {
        if (switchTest) return;
        boolean bracketsL = node.left().op() == Or;
        if (bracketsL) print("(");
        compile(node.left());
        if (bracketsL) print(")");
        print(" && ");
        boolean bracketsR = node.right().op() == Or;
        Node next = node.right();
        if (next.op() == And) next = next.left();
        boolean fit = next.LEN() <= margin - cursor;
        if (! bracketsR && ! fit) newline(0);
        if (bracketsR) print("(");
        compile(node.right());
        if (bracketsR) print(")");
    }

    // Compile p?
    private void compileOpt(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        if (node.left().has(FP)) {
            print("OPT(");
            if (! fit) newline(+1);
            print("DO() && ");
            compile(node.left());
            if (! fit) newline(-1);
            print(")");
        } else {
            print("(");
            compile(node.left());
            print(" || true)");
        }
    }

    // Compile [p] when p has no actions or errors.
    private void compileTry(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        print("TRY(");
        if (! fit) newline(+1);
        print("DO() && ");
        compile(node.left());
        if (! fit) newline(-1);
        print(")");
    }

    // Compile p&
    private void compileHas(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        print("HAS(");
        if (! fit) newline(+1);
        print("DO() && ");
        compile(node.left());
        if (! fit) newline(-1);
        print(")");
    }

    // Compile p!
    private void compileNot(Node node) {
        if (switchTest) return;
        boolean fit = node.LEN() <= margin - cursor;
        print("NOT(");
        if (! fit) newline(+1);
        print("DO() && ");
        compile(node.left());
        if (! fit) newline(-1);
        print(")");
    }

    // Compile %t
    private void compileTag(Node node) {
        if (switchTest) return;
        print("TAG('" + node.name() + "')");
    }

    // Compile ""
    private void compileSuccess(Node node) {
        if (switchTest) return;
        print("true");
    }

    // Compile ''
    private void compileFail(Node node) {
        if (switchTest) return;
        print("false");
    }

    // Compile <>
    private void compileEnd(Node node) {
        if (switchTest) return;
        print("END()");
    }

    // Compile 'a' or "a"
    private void compileChar(Node node) {
        if (switchTest) return;
        print("CHAR('" + node.name() + "')");
    }

    // Compile 127
    private void compileCode(Node node) {
        if (switchTest) return;
        print("CODE(" + node.name() + ")");
    }

    // Compile "abc"
    private void compileString(Node node) {
        if (switchTest) return;
        print("STRING(\"" + node.name() + "\")");
    }

    // Compile 'abc'
    private void compileSet(Node node) {
        if (switchTest) return;
        print("SET(\"" + node.name() + "\")");
    }

    // Compile 'a..z'
    // TODO: sort out unicode.
    private void compileRange(Node node) {
        if (switchTest) return;
        char c1 = node.name().charAt(0);
        char c2 = node.name().charAt(node.name().length() - 1);
        print("RANGE('" + c1 + "','" + c2 + "'\")");
    }

    // Compile 0..127
    private void compileCodes(Node node) {
        if (switchTest) return;
        print("CODES(" + node.low() + "," + node.high() + ")");
    }

    // Compile <abc>
    private void compileSplit(Node node) {
        if (switchTest) return;
        print("SPLIT(\"" + node.name() + "\")");
    }

    // Compile Nd
    private void compileCat(Node node) {
        if (switchTest) return;
        print("CAT(\"" + node.name() + "\")");
    }

    // Compile #m
    private void compileMark(Node node) {
        if (switchTest) return;
        print("MARK(" + node.name() + ")");
    }

    // Compile @3
    private void compileDrop(Node node) {
        if (switchTest) return;
        print("DROP(" + node.arity() + ")");
    }

    // Compile @3a
    private void compileAct(Node node) {
        if (switchTest) return;
        String a = node.arity() == 0 ? "" : "" + node.arity();
        print("ACT" + a + "(" + node.name() + ")");
    }

/*
    // Check whether a node is compound, i.e. able to split itself across lines.
    private boolean compound(Node node) {
        Op op = node.op();
        switch (op) {
            case Rule: case Or: case And: case Opt: case Any: case Some:
            case Try: case Has: case Not: return true;
            default: return false;
        }
    }
*/
    // Remove all the output and reset.
    private void clear() {
        output.setLength(0);
        cursor = 0;
        indent = 0;
        startLine = true;
    }

    // Print non-newline text, keep track of amount printed on current line.
    // Remove initial space if starting a new line.
    private void print(String s) {
        if (startLine && s.startsWith(" ")) s = s.substring(1);
        output.append(s);
        cursor += s.length();
        startLine = false;
    }

    // Print newline, removing any trailing space, possibly changing indent.
    private void newline(int change) {
        if (output.charAt(output.length() - 1) == ' ') {
            output.setLength(output.length() - 1);
        }
        indent = indent + tab * change;
        output.append("\n");
        for (int i=0; i<indent; i++) output.append(' ');
        cursor = indent;
        startLine = true;
    }
}
