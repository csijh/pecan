// Pecan 1.0 bytecode generator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Code.*;
import static pecan.Node.Flag.*;

/* Converts grammar into bytecode. The normal translations are:

    source    bytecode
    ------------------------------
    id = x    START nx {x} STOP
    id = x    RULE id START nx {x} STOP   (if explicit entry point)
    id        GO n    or    BACK n
    x / y     EITHER nx {x} OR {y}
    x y       BOTH nx {x} AND {y}
    x?        MAYBE ONE {x}
    x*        MAYBE MANY {x}
    x+        DO AND MAYBE MANY {x}
    [x]       LOOK TRY {x}
    x&        LOOK HAS {x}
    x!        LOOK NOT {x}
    @a        ACT n
    @         DROP
    #e        MARK n
    10        CHAR 10                     (ascii)
    'a'       CHAR 'a'                    (ascii)
    128       STRING n "utf-8"
    "a"       STRING n "bytes"
    "pi"      STRING n "pi"
    'ab'      SET n "ab"
    "a".."z"  LOW m "a" HIGH n "z"
    <a>       LESS n "a"
    Nd        CAT Nd
    %id       TAG n

By default, there are no explicit entry points, only an implicit entry point at
the start. There is an option to ask for explicit entry points. The bytecode can
be scanned to find entry points.

TODO markers: is there a lift optimisation?
TODO: explicit entry points
*/

class Generator implements Testable {
    private StringBuilder output, comment;
    private String[] tags, actions, markers;
    private int[] arities;
    private int pc, linePc;
    private boolean changed;

    public static void main(String[] args) {
        if (args.length == 0) Checker.main(args);
        if (args.length == 0) Test.run(new Generator());
        else Test.run(new Generator(), Integer.parseInt(args[0]));
    }

    // Each test has a grammar as input, so this method is not used.
    public void grammar(String g) { }

    public String test(String g) {
        return "" + run(g);
    }

    // Convert the grammar into bytecode
    String run(String grammar) {
        Stacker stacker = new Stacker();
        Node root = stacker.run(grammar);
        tags = new String[size(root, Tag)];
        markers = new String[size(root, Mark)];
        actions = new String[size(root, Act)];
        arities = new int[actions.length];
        comment = new StringBuilder();
        strings(root);
        output = new StringBuilder();
        pc = 0;
        changed = true;
        while (changed) {
            changed = false;
            pc = 0;
            output.setLength(0);
            encode(root);
        }
        output.append("\n");
        return output.toString();
    }

    // Measure the number of items of a given node type, according to value.
    private int size(Node node, Op op) {
        int m = 0, n = 0;
        if (node.left() != null) m = size(node.left(), op);
        if (node.right() != null) n = size(node.right(), op);
        m = Math.max(m, n);
        if (node.op() == op) m = Math.max(m, node.value() + 1);
        return m;
    }

    // Gather strings for tags, actions and markers.
    private void strings(Node node) {
        if (node.left() != null) strings(node.left());
        if (node.right() != null) strings(node.right());
        switch (node.op()) {
        // TODO: `tag`
        case Tag: tags[node.value()] = node.text().substring(1); break;
        case Mark: markers[node.value()] = node.text().substring(1); break;
        case Act:
            actions[node.value()] = node.ref().text();
            arities[node.value()] = node.ref().value();
            break;
        case String: case Set: case Char: case Range: case Cat: case Not:
            break;
        }
    }

/*
    // Find the address of a node. If it is an identifier, use the address of
    // the rule it refers to.
    int address(Node node) {
        if (node.op() == Id) node.PC(node.ref().PC());
        return node.PC();
    }
*/
    // Generate code for a node. Call this repeatedly to get addresses right.
    void encode(Node node) {
        int arg;
        if (pc != node.PC()) changed = true;
        node.PC(pc);
        System.out.println("" + pc + ": " + node.op() + output);
        switch (node.op()) {
        case Rule:
            add(START, node.left().LEN());
            encode(node.left());
            add(STOP);
            break;
        case Id:
            int target = node.ref().left().PC();
            int offset = target - (pc + node.LEN());
            if (offset >= 0) add(GO, offset);
            else add(BACK, -offset);
            break;
        case Or:
            arg = node.left().LEN();
            add(EITHER, arg);
            encode(node.left());
            add(OR);
            encode(node.right());
            break;
        case And:
            arg = node.left().LEN();
            add(BOTH, arg);
            encode(node.left());
            add(AND);
            encode(node.right());
            break;
        case Opt:  // x?  ->  MAYBE ONE x
            add(MAYBE);
            add(ONE);
            encode(node.left());
            break;
        case Many: // x*  ->  MAYBE MANY x
            add(MAYBE);
            add(MANY);
            encode(node.left());
            break;
        case Some: // x+  ->  DO THEN MAYBE MANY <x>
            add(DO);
            add(THEN);
            add(MAYBE);
            add(MANY);
            encode(node.left());
            break;
        case Char:
            int ch = node.value();
            if (ch <= 255) add(CHAR, ch);
            else {
                throw new Error("Not yet");
            }
            break;
        case String:
            char[] text = text(node);
            if (text.length == 1) add(CHAR, text[0]);
            else add(STRING, text(node));
            break;
        case Range:
            add(LOW);
            add(text(node.left()));
            add(HIGH);
            add(text(node.right()));
            break;


/*
[x]       Try x       LOOK TRY <x>
x&        Has x       LOOK HAS <x>
x!        Not x       LOOK NOT <x>
@a        Act a       ACT n                       (one byte index)
@         Drop        DROP
#e        Mark e      MARK n
10        Char 10     CHAR 10                     (ascii)
128       Char 128    STRING n "utf-8"
"a"       String "a"  STRING n "bytes"
'a'       Char 'a'    CHAR 'a'                    (ascii)
"pi"      Char 'pi'   STRING n "pi"
'ab'      Set 'ab'    SET n "ab"
"a".."z"  Range...    GE n "a" LE n "z"
0.."m"    Range...    LE n "m"
Nd        Cat Nd      CAT Nd
%id       Tag id      TAG n
`+`       Tag +       TAG n
        case TRY: // [x]  ->  LOOK TRY x
            if (! node.has(AA)) {
                add(LOOK);
                add(TRY);
            } else {  // [x]  ->  BOTH AND &x LOOK TRY x
                add(BOTH);
                add(AND);
                arg2(pc+2);
                add(LOOK);
                add(TRY);
            }
            break;
        case Has: // x&  ->  LOOK HAS x
            add(LOOK);
            add(HAS);
            break;
        case Not: // x!  ->  LOOK NOT x
            add(LOOK);
            add(NOT);
            break;
        case Mark:  // #e  ->  MARK #e
            add(MARK);
            arg2(node.value());
            break;
        case Tag:   // %a  ->  TAG a
            add(TAG);
            arg1((byte)node.value());
            break;
        case Set:  // 's'  ->  SET n s
            text = text(node);
            add(SET);
            string(text);
            break;
        case Cat:    // CAT c
            add(CAT); arg1((byte)node.value());
            break;
        case Drop:  // @ -> DROP
            add(DROP);
            break;
            */
        case Act:   // ACT a
            add(node.text().substring(1));
//            arg = node.value();
//            add(ACT, arg);
            break;
        }
        if (node.LEN() != pc - node.PC()) changed = true;
        node.LEN(pc - node.PC());
//        if (node.left() != null) encode(node.left());
//        if (node.right() != null) encode(node.right());
    }

    // Find the character array for a char/string/set node
    char[] text(Node node) {
        switch (node.op()) {
        case Char:
            int base = node.text().startsWith("0") ? 16 : 10;
            int ch = Integer.parseInt(node.text(), base);
            return Character.toChars(ch);
        case String: case Set:
            String s = node.text();
            s = s.substring(1, s.length() - 1);
            return s.toCharArray();
        default: throw new Error("Not implemented");
        }
    }

    private void add(String s) {
        if (output.length() > 0) output.append(", ");
        output.append(s);
        pc++;
    }

    private void add(Code op) {
        add(op.toString());
    }

    private void add(int n) {
        add("" + n);
    }

    private void add(char[] text) {
        byte[] bytes;
        try { bytes = new String(text).getBytes("UTF8"); }
        catch (Exception e) { throw new Error(e); }
        add(bytes.length);
        for (byte b : bytes) add(b);
    }

    private void add(Code op, char[] text) {
        add(op.toString());
        add(text);
    }

    // Encode an op and arg.
    private void add(Code op, int arg) {
        if (arg > 65535) throw new Error("code too large");
        if (arg > 255) {
            add(op.toString() + "2");
            add(arg/256);
            add(arg%256);
        }
        else {
            add(op.toString());
            add(arg);
        }
    }

    void endLine() {
        /*
        int n = pc - linePc;
        int a = .length();
        for (int i=0; i<n; i++) .append(code[linePc+i] + ", ");
        int b = .length();
        for (int i=b-a; i<20; i++) .append(" ");
        .append(comment);
        comment.setLength(0);
        linePc = pc;
        */
    }
/*
    void opcode(Op op) {
        add((byte)op.ordinal());
        comment.append(" " + op.toString().substring(1));
    }
    void arg1(int n) {
        add((byte) n);
        comment.append(" " + n);
    }
    void arg2(int n) {
        add((byte)(n >> 8));
        add((byte)(n & 0xFF));
        comment.append(" " + n);
    }
    void string(char[] s) {
        add((byte) s.length);
        for (int i=0; i<s.length; i++) add((byte) s[i]);
        comment.append(" " + '"');
        for (int i=0; i<s.length; i++) comment.append(s[i]);
        comment.append('"');
    }

    void add(int arg) { add((byte)(arg >> 8)); add((byte)(arg & 0xFF)); }
    void add(Op op, int arg) { add(op); add(arg); }

    void add(char[] chars) {
        add((byte) chars.length);
        for (int i=0; i<chars.length; i++) add((byte) chars[i]);
    }
    */
}
