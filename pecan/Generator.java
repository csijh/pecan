// Pecan 5 bytecode generator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;

/* Converts grammar into bytecode. The translation is:

    source    node        bytecode
    ------------------------------
    id = x    RULE x      RULE <x>                    (entry point)
    x         ID          ID &rhs                     (two byte offset)
    x / y     OR x y      EITHER &OR <x> OR <y>       (one unsigned byte offset)
    x y       AND x y     BOTH &AND <x> AND <y>
    x?        OPT x       MAYBE &OPT <x> OPT
    x*        MANY x      MAYBE &MANY <x> MANY
    x+        SOME x      DO &x THEN MAYBE <x> MANY
    [x]       TRY x       LOOK &TRY <x> TRY
    x&        HAS x       LOOK &HAS <x> HAS
    x!        NOT x       LOOK &NOT <x> NOT
    @a        ACT a       ACT n                       (one unsigned byte index)
    @         DROP        DROP
    #e        MARK e      MARK n
    10        CHAR 10     CHAR 10                     (ascii)
    128       CHAR 128    STRING n "utf-8"
    "a"       STRING "a"  STRING n "bytes"
    'a'       CHAR 'a'    CHAR 'a'                    (ascii)
    "pi"      CHAR 'pi'   STRING n "pi"
    'ab'      SET 'ab'    SET n "ab"
    "a".."z"  RANGE...    RANGE n "a" LE n "z"
    0.."m"    RANGE...    LE n "m"
    Nd        CAT Nd      CAT Nd
    %id       TAG id      TAG n
    `+`       TAG +       TAG n
                          STOP


Holds the code, plus:
  the opcodes (may not want to include twice) ?
-  the actions for output
-  the entry points for the rules (and/or rule names) (one output item)?
-  the marker names (or entry points for them)
// TODO markers: is there a lift optimisation?

TODO: Standard versions:

STRING n "utf-8 bytes" (256 limit!)
SET n "ascii bytes"
GE "utf-8 char"     (split range)
LE "utf-8 char"
CAT c (one byte)
EITHER &OR BOTH &AND LE m AND <a/.../m> OR <n/.../z>    (switch)
TAG t
START
STOP

// 10       ->  CHAR 10
// 128      ->  STRING n "utf-8 bytes"
// "a"      ->  CHAR 'a'
// 'a'      ->  CHAR 'a'
// "pi"     ->  STRING n "utf-8 bytes"
// 'ab'     ->  SET n "bytes"
// "a".."z" ->  RANGE &AND "a" AND LE "z"
LE,      // 0.."m"   ->  LE "m"
// Nd       ->  CAT Nd
// %id      ->  TAG n
// `+`      ->  TAG n
// @        ->  DROP
// @a       ->  ACT n

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
        encode(root);
        code = Arrays.copyOf(code, pc);
        pc = 0;
        linePc = 0;
        output.setLength(0);
        encode(root);
        System.out.println(Arrays.toString(code));
//        return Arrays.toString(code) + "\n";
//        return showCode() + "\n";
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

    // Find the address of a node. If it is an identifier, the address of the
    // rule it refers to can be used. Set the BP (bypassed) flag.
    int address(Node node) {
        if (node.op() == ID) { node.PC(node.ref().PC()); node.set(BP); }
        return node.PC();
    }

    // Generate code for a node. Call this twice to get addresses right.
    // If the second argument is true, code is required, not just a reference.
    void encode(Node node) {
        char[] text;
        node.PC(pc);
        comment.append("// " + pc + ":");
        switch (node.op()) {
        case RULE: // r = x  ->  RULE frame x
            // Create an entry point.
            if (node.NET() == 1) {
                opcode($START);
                opcode($STOP);
                endLine();
                comment.append("// " + pc + ":");
            }
            node.PC(pc);
            opcode($RULE);
            arg2(frame(node));
            break;
        case ID:   // r  ->  GO &r   (if not already bypassed)
            if (node.has(BP)) break;
            opcode($GO); arg2(node.ref().PC());
            break;
        case OR:  // x / y  ->  EITHER OR &y x y
            opcode($EITHER); opcode($OR); arg2(address(node.right()));
            break;
        case AND: // x y  ->  BOTH AND &y x y
            opcode($BOTH); opcode($AND); arg2(address(node.right()));
            break;
        case OPT:  // x?  ->  REPEAT ONCE x
            opcode($REPEAT);
            opcode($ONCE);
            break;
        case MANY: // x*  ->  REPEAT MANY x
            opcode($REPEAT);
            opcode($MANY);
            break;
        case SOME:
            opcode($DO);
            opcode($THEN);
            opcode($REPEAT);
            opcode($MANY);
            break;
        case TRY: // [x]  ->  LOOK TRY x
            if (! node.has(AA)) {
                opcode($LOOK);
                opcode($TRY);
            } else {  // [x]  ->  BOTH AND &x LOOK TRY x
                opcode($BOTH);
                opcode($AND);
                arg2(pc+2);
                opcode($LOOK);
                opcode($TRY);
            }
            break;
        case HAS: // x&  ->  LOOK HAS x
            opcode($LOOK);
            opcode($HAS);
            break;
        case NOT: // x!  ->  LOOK NOT x
            opcode($LOOK);
            opcode($NOT);
            break;
        case MARK:  // #e  ->  MARK #e
            opcode($MARK);
            arg2(node.value());
            break;
        case TAG:   // %a  ->  TAG a
            opcode($TAG);
            arg1((byte)node.value());
            break;
        case CHAR:  // c  ->  STRING n c
            text = text(node);
            opcode($STRING);
            string(text);
            break;
        case STRING: // "s"  ->  STRING n s
            text = text(node);
            opcode($STRING);
            string(text);
            break;
        case SET:  // 's'  ->  SET n s
            text = text(node);
            opcode($SET);
            string(text);
            break;
        case RANGE:  // RANGE "c1" "c2"
            opcode($RANGE);
            string(text(node.left()));
            string(text(node.right()));
            // Don't generate any more code for the child nodes
            endLine();
            return;
        case CAT:    // CAT c
            opcode($CAT); arg1((byte)node.value());
            break;
        case DROP:  // @ -> DROP
            opcode($DROP);
            break;
        case ACT:   // ACT a
            opcode($ACT);
            arg2(node.value());
            break;
        }
        endLine();
        if (node.left() != null) encode(node.left());
        if (node.right() != null) encode(node.right());
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

    void add(byte b) {
        if (pc >= code.length) code = Arrays.copyOf(code, pc*2);
        code[pc++] = b;
    }
    void opcode(Opcode op) {
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
    void endLine() {
        if (output.length() > 0) output.append("\n");
        int n = pc - linePc;
        int a = output.length();
        for (int i=0; i<n; i++) output.append(code[linePc+i] + ", ");
        int b = output.length();
        for (int i=b-a; i<20; i++) output.append(" ");
        output.append(comment);
        comment.setLength(0);
        linePc = pc;
    }

    void add(Opcode op) { add((byte)op.ordinal()); }
    void add(int arg) { add((byte)(arg >> 8)); add((byte)(arg & 0xFF)); }
    void add(Opcode op, int arg) { add(op); add(arg); }

    void add(char[] chars) {
        add((byte) chars.length);
        for (int i=0; i<chars.length; i++) add((byte) chars[i]);
    }
}
