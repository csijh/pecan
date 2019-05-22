// Pecan 1.0 bytecode generator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Converts grammar into bytecode. The normal translations are:

    source    bytecode
    ------------------------------
    id = x    RULE id START n <x> STOP    (entry point)
    id        GO n    or    BACK n
    x / y     EITHER n <x> OR <y>         (one byte length)
    x y       BOTH n <x> AND <y>
    x?        MAYBE OPT <x>
    x*        MAYBE MANY <x>
    x+        DO AND MAYBE MANY <x>
    [x]       LOOK TRY <x>
    x&        LOOK HAS <x>
    x!        LOOK NOT <x>
    @a        ACT n                       (one byte index)
    @         DROP
    #e        MARK n
    10        CHAR 10                     (ascii)
    128       STRING n "utf-8"
    "a"       STRING n "bytes"
    'a'       CHAR 'a'                    (ascii)
    "pi"      STRING n "pi"
    'ab'      SET n "ab"
    "a".."z"  RANGE m "a" n "z"
    <a>       LT n "a"
    Nd        CAT Nd
    %id       TAG n

By default, only first rule is defined as an entry point (in which case RULE id
is left out). There is an option to ask for more entry points. The bytecode can
be scanned to find entry points.

    case EXTEND:
        arg = *pc++;
        break;
    case AND:
        arg = (arg << 8) | *pc++;
        pc += arg;
        ...
        if (op != EXTEND) arg = 0;

No args: START STOP Or AND MAYBE OPT MAYBE MANY DO THEN LOOK TRY HAS NOT DROP
Arg: GO, EITHER, BOTH, ACT, MARK, CHAR, STRING, SET, GE, LE, CAT, TAG
Arg: GO2, EITHER2, BOTH2, ACT2, MARK2, STRING2, SET2, GE2, LE2, CAT2, TAG2

If a length of <x> is greater than 255, the translation is

    EITHER n <x> Or <y>
    EXTEND n1 EITHER n2 <x> Or <y>

TODO: template-based generation, printf-like:
<"%d", opcodes>     decimal text
<"%s, ", opcode>    with ", " separator
<"%b", opcodes>     binary, one byte
<"%.2b", opcodes>   binary, two bytes, endianness?
<"%lb%b\0", opcode>  byte length, utf-8 bytes, null byte terminator
actions
entries
errors
tags
bytecodes

Holds the code, plus:
  the opcodes (may not want to include twice) ?
-  the actions for output
-  the entry points for the rules (and/or rule names) (one output item)?
-  the marker names (or entry points for them)
// TODO markers: is there a lift optimisation?
TODO: multiple entry points
TODO: Standard versions:

EITHER &Or BOTH &AND LE m AND <a/.../m> OR <n/.../z>    (switch)

TODO: switch optimization from
x = a / b / ... / z
first sort if allowed (and group?) then
x = ('a' .. 'm') (a / b / ... / m) / n / ... / z
could use a semi-range 0..'m'


TODO: two versions of each opcode, immediate and remote
EITHER &Or <x> Or...
EITHERr &x Or...
This design allows each opcode to be either, independently. Use remote when
x is an identifier, to avoid a GO opcode.
*/

class Generator implements Testable {
    private byte[] code;
    private StringBuilder output, comment;
    private String[] rules, tags, actions, markers;
    private int[] arities;
    private int pc, linePc;
    private boolean changed;

    public static void main(String[] args) {
        if (args.length == 0) Checker.main(args);
        if (args.length == 0) Test.run(new Generator());
        else Test.run(new Generator(), Integer.parseInt(args[0]));
    }

    public String test(String g, String s) throws ParseException {
        return run(g);
    }

    // Convert the grammar into bytecode
    String run(String grammar) throws ParseException {
        Stacker stacker = new Stacker();
        Node root = stacker.run(grammar);
        rules = new String[size(root, Rule)];
        tags = new String[size(root, Tag)];
        markers = new String[size(root, Mark)];
        actions = new String[size(root, Act)];
        arities = new int[actions.length];
        output = new StringBuilder();
        comment = new StringBuilder();
        strings(root);
        code = new byte[1];
        pc = 0;
        changed = true;
        while (changed) {
            changed = false;
            pc = 0;
            output.setLength(0);
            encode(root);
        }
        code = Arrays.copyOf(code, pc);
        return output.toString() + "\n";
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

    // Gather strings for rules, tags, actions and markers.
    private void strings(Node node) {
        if (node.left() != null) strings(node.left());
        if (node.right() != null) strings(node.right());
        switch (node.op()) {
        case Rule: rules[node.value()] = node.text(); break;
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
    // Find maximum new call stack needed per rule, not taking calls to other
    // rules into account.
    private int frame(Node node) {
        switch (node.op()) {
        case Rule:
            return frame(node.left());
        case Id:
            return 0;
        case Or:
            return Math.max(2 + frame(node.left()), frame(node.right()));
        case And:
            return Math.max(1 + frame(node.left()), frame(node.right()));
        case Opt: case Many: case Some:
            return 2 + frame(node.left());
        case Not: case Try: case Has:
            return 3 + frame(node.left());
        case Tag: case Act: case Mark:
        case String: case Set: case Char: case Range: case Cat: case Drop:
            return 0;
        default: throw new Error("Type " + node.op() + " unimplemented");
        }
    }
*/
/*
    // Find the address of a node. If it is an identifier, use the address of
    // the rule it refers to.
    int address(Node node) {
        if (node.op() == Id) node.PC(node.ref().PC());
        return node.PC();
    }
*/
    // Generate code for a node. Call this twice to get addresses right.
    void encode(Node node) {
        char[] text;
        int arg;
        if (pc != node.PC()) changed = true;
        node.PC(pc);
        if (output.length() > 0) output.append("\n");
        output.append("" + pc + ":");
        switch (node.op()) {
        case Rule: // id = x  ->  START STOP <x>
            add(START);
            add(STOP);
            encode(node.left());
            break;
        case Id:   // id = x ... id  ->  GO &x
            arg = node.ref().left().PC();
            add(GO, arg);
            break;
        case Or:  // x / y  ->  EITHER n <x> Or <y>
            arg = node.left().LEN();
            add(EITHER, arg);
            encode(node.left());
            add(OR);
            encode(node.right());
            break;
        case And: // x y  ->  BOTH n <x> AND <y>
            arg = node.left().LEN();
            add(BOTH, arg);
            encode(node.left());
            add(AND);
            encode(node.right());
            break;
        case Opt:  // x?  ->  MAYBE OPT x
            add(MAYBE);
            add(OPT);
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
        x

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
        case Char:  // c  ->  STRING n c
            text = text(node);
            add(STRING);
            string(text);
            break;
        case String: // "s"  ->  STRING n s
            text = text(node);
            add(STRING);
            string(text);
            break;
        case Set:  // 's'  ->  SET n s
            text = text(node);
            add(SET);
            string(text);
            break;
        case Range:  // RANGE "c1" "c2"
            add(RANGE);
            string(text(node.left()));
            string(text(node.right()));
            // Don't generate any more code for the child nodes
            endLine();
            return;
        case Cat:    // CAT c
            add(CAT); arg1((byte)node.value());
            break;
        case Drop:  // @ -> DROP
            add(DROP);
            break;
            */
        case Act:   // ACT a
            arg = node.value();
            add(ACT, arg);
            break;
        }
        if (node.LEN() != pc - node.PC()) changed = true;
        node.LEN(pc - node.PC());
        System.out.println(node.LEN());
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

    private void add(byte b) {
        if (pc >= code.length) code = Arrays.copyOf(code, pc*2);
        code[pc++] = b;
    }

    private void add(Op op) {
        add((byte)op.ordinal());
        output.append(" " + op.toString());
    }

    // Encode an op and arg. Add prefixes as necessary.
    private void add(Op op, int arg) {
        if (arg > 65535) {
            add(EXTEND);
            add((byte) (arg >> 16));
        }
        if (arg > 255) {
            add(EXTEND);
            add((byte) (arg >> 8));
        }
        add(op);
        add((byte) arg);
        output.append(" " + arg);
    }

    void endLine() {
        if (output.length() > 0) output.append("\n");
        /*
        int n = pc - linePc;
        int a = output.length();
        for (int i=0; i<n; i++) output.append(code[linePc+i] + ", ");
        int b = output.length();
        for (int i=b-a; i<20; i++) output.append(" ");
        output.append(comment);
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
