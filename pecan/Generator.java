// Pecan 5 bytecode generator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Converts grammar into bytecode. The normal translations are:

    source    node        bytecode
    ------------------------------
    id = x    RULE x      START STOP <x>              (entry point)
    id        ID          GO n
    x / y     OR x y      EITHER n <x> OR <y>         (one byte length)
    x y       AND x y     BOTH n <x> AND <y>
    x?        OPT x       MAYBE OPT <x>
    x*        MANY x      MAYBE MANY <x>
    x+        SOME x      DO THEN MAYBE MANY <x>
    [x]       TRY x       LOOK TRY <x>
    x&        HAS x       LOOK HAS <x>
    x!        NOT x       LOOK NOT <x>
    @a        ACT a       ACT n                       (one byte index)
    @         DROP        DROP
    #e        MARK e      MARK n
    10        CHAR 10     CHAR 10                     (ascii)
    128       CHAR 128    STRING n "utf-8"
    "a"       STRING "a"  STRING n "bytes"
    'a'       CHAR 'a'    CHAR 'a'                    (ascii)
    "pi"      CHAR 'pi'   STRING n "pi"
    'ab'      SET 'ab'    SET n "ab"
    "a".."z"  RANGE...    GE n "a" LE n "z"
    0.."m"    RANGE...    LE n "m"
    Nd        CAT Nd      CAT Nd
    %id       TAG id      TAG n
    `+`       TAG +       TAG n

    case EXTEND:
        arg = *pc++;
        break;
    case AND:
        arg = (arg << 8) | *pc++;
        pc += arg;
        ...
        if (op != EXTEND) arg = 0;

If a length of <x> is greater than 255, the translation is

    EITHER n <x> OR <y>
    EXTEND n1 EITHER n2 <x> OR <y>

Holds the code, plus:
  the opcodes (may not want to include twice) ?
-  the actions for output
-  the entry points for the rules (and/or rule names) (one output item)?
-  the marker names (or entry points for them)
// TODO markers: is there a lift optimisation?
TODO: multiple entry points
TODO: Standard versions:

EITHER &OR BOTH &AND LE m AND <a/.../m> OR <n/.../z>    (switch)

TODO: switch optimization from
x = a / b / ... / z
first sort if allowed (and group?) then
x = ('a' .. 'm') (a / b / ... / m) / n / ... / z
could use a semi-range 0..'m'


TODO: two versions of each opcode, immediate and remote
EITHER &OR <x> OR...
EITHERr &x OR...
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
        rules = new String[size(root, RULE)];
        tags = new String[size(root, TAG)];
        markers = new String[size(root, MARK)];
        actions = new String[size(root, ACT)];
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
        case RULE: rules[node.value()] = node.text(); break;
        // TODO: `tag`
        case TAG: tags[node.value()] = node.text().substring(1); break;
        case MARK: markers[node.value()] = node.text().substring(1); break;
        case ACT:
            actions[node.value()] = node.ref().text();
            arities[node.value()] = node.ref().value();
            break;
        case STRING: case SET: case CHAR: case RANGE: case CAT: case NOT:
            break;
        }
    }

/*
    // Find maximum new call stack needed per rule, not taking calls to other
    // rules into account.
    private int frame(Node node) {
        switch (node.op()) {
        case RULE:
            return frame(node.left());
        case ID:
            return 0;
        case OR:
            return Math.max(2 + frame(node.left()), frame(node.right()));
        case AND:
            return Math.max(1 + frame(node.left()), frame(node.right()));
        case OPT: case MANY: case SOME:
            return 2 + frame(node.left());
        case NOT: case TRY: case HAS:
            return 3 + frame(node.left());
        case TAG: case ACT: case MARK:
        case STRING: case SET: case CHAR: case RANGE: case CAT: case DROP:
            return 0;
        default: throw new Error("Type " + node.op() + " unimplemented");
        }
    }
*/
/*
    // Find the address of a node. If it is an identifier, use the address of
    // the rule it refers to.
    int address(Node node) {
        if (node.op() == ID) node.PC(node.ref().PC());
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
        case RULE: // id = x  ->  START STOP <x>
            add(START);
            add(STOP);
            encode(node.left());
            break;
        case ID:   // id = x ... id  ->  GO &x
            arg = node.ref().left().PC();
            add(GO, arg);
            break;
        case OR:  // x / y  ->  EITHER n <x> OR <y>
            arg = node.left().LEN();
            add(EITHER, arg);
            encode(node.left());
            add(OR);
            encode(node.right());
            break;
        case AND: // x y  ->  BOTH n <x> AND <y>
            arg = node.left().LEN();
            add(BOTH, arg);
            encode(node.left());
            add(AND);
            encode(node.right());
            break;
        case OPT:  // x?  ->  MAYBE OPT x
            add(MAYBE);
            add(OPT);
            encode(node.left());
            break;
        case MANY: // x*  ->  MAYBE MANY x
            add(MAYBE);
            add(MANY);
            encode(node.left());
            break;
        case SOME: // x+  ->  DO THEN MAYBE MANY <x>
            add(DO);
            add(THEN);
            add(MAYBE);
            add(MANY);
            encode(node.left());
            break;
        x

/*
[x]       TRY x       LOOK TRY <x>
x&        HAS x       LOOK HAS <x>
x!        NOT x       LOOK NOT <x>
@a        ACT a       ACT n                       (one byte index)
@         DROP        DROP
#e        MARK e      MARK n
10        CHAR 10     CHAR 10                     (ascii)
128       CHAR 128    STRING n "utf-8"
"a"       STRING "a"  STRING n "bytes"
'a'       CHAR 'a'    CHAR 'a'                    (ascii)
"pi"      CHAR 'pi'   STRING n "pi"
'ab'      SET 'ab'    SET n "ab"
"a".."z"  RANGE...    GE n "a" LE n "z"
0.."m"    RANGE...    LE n "m"
Nd        CAT Nd      CAT Nd
%id       TAG id      TAG n
`+`       TAG +       TAG n
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
        case HAS: // x&  ->  LOOK HAS x
            add(LOOK);
            add(HAS);
            break;
        case NOT: // x!  ->  LOOK NOT x
            add(LOOK);
            add(NOT);
            break;
        case MARK:  // #e  ->  MARK #e
            add(MARK);
            arg2(node.value());
            break;
        case TAG:   // %a  ->  TAG a
            add(TAG);
            arg1((byte)node.value());
            break;
        case CHAR:  // c  ->  STRING n c
            text = text(node);
            add(STRING);
            string(text);
            break;
        case STRING: // "s"  ->  STRING n s
            text = text(node);
            add(STRING);
            string(text);
            break;
        case SET:  // 's'  ->  SET n s
            text = text(node);
            add(SET);
            string(text);
            break;
        case RANGE:  // RANGE "c1" "c2"
            add(RANGE);
            string(text(node.left()));
            string(text(node.right()));
            // Don't generate any more code for the child nodes
            endLine();
            return;
        case CAT:    // CAT c
            add(CAT); arg1((byte)node.value());
            break;
        case DROP:  // @ -> DROP
            add(DROP);
            break;
            */
        case ACT:   // ACT a
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
        case CHAR:
            int base = node.text().startsWith("0") ? 16 : 10;
            int ch = Integer.parseInt(node.text(), base);
            return Character.toChars(ch);
        case STRING: case SET:
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