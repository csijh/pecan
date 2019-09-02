// Pecan 1.0 bytecode generator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Code.*;
import static pecan.Node.Flag.*;

/* Convert a grammar into bytecode.
TODO markers: is there a lift optimisation?
*/

class Generator implements Testable {
    private StringBuilder output;
    private int pc, line;
    private boolean changed;

    public static void main(String[] args) {
        if (args.length == 0) Checker.main(args);
        if (args.length == 0) Test.run(new Generator());
        else Test.run(new Generator(), Integer.parseInt(args[0]));
    }

    public String test(String g) {
        return "" + run(g);
    }

    // Convert the grammar into bytecode
    String run(String grammar) {
        Stacker stacker = new Stacker();
        Node root = stacker.run(grammar);
        output = new StringBuilder();
        changed = true;
        while (changed) {
            changed = false;
            pc = line = 0;
            output.setLength(0);
            encode(root);
        }
        if (line > 0) output.append("\n");
        return output.toString();
    }

    // Generate code for a node. Call this repeatedly to get addresses right.
    void encode(Node node) {
        if (node == null) return;
        if (pc != node.PC()) changed = true;
        node.PC(pc);
        switch (node.op()) {
            case Rule:      encodeRule(node);       break;
            case Id:        encodeId(node);         break;
            case Or:        encodeOr(node);         break;
            case And:       encodeAnd(node);        break;
            case Opt:       encodeOpt(node);        break;
            case Any:       encodeAny(node);        break;
            case Some:      encodeSome(node);       break;
            case Try:       encodeTry(node);        break;
            case Has:       encodeHas(node);        break;
            case Not:       encodeNot(node);        break;
            case Drop:      encodeDrop(node);       break;
            case Act:       encodeAct(node);        break;
            case Mark:      encodeMark(node);       break;
            case Tag:       encodeTag(node);        break;
            case Number:    encodeNumber(node);     break;
            case Cat:       encodeCat(node);        break;
            case String:    encodeString(node);     break;
            case Range:     encodeRange(node);      break;
            case Divider:   encodeDivider(node);    break;
            case Set:       encodeSet(node);        break;
        }
        if (node.LEN() != pc - node.PC()) changed = true;
        node.LEN(pc - node.PC());
    }

    // {id = x; ...}  =  START(nx), {x}, STOP ...
    private void encodeRule(Node node) {
        add(START, node.left().LEN());
        encode(node.left());
        add(STOP);
        encode(node.right());
    }

    // {id}  =  GO(n)    or    BACK(n)
    private void encodeId(Node node) {
        int target = node.ref().left().PC();
        int offset = target - (pc + node.LEN());
        if (offset >= 0) add(GO, offset);
        else add(BACK, -offset);
    }

    // {x / y}  =  EITHER(nx), {x}, OR, {y}
    private void encodeOr(Node node) {
        int nx = node.left().LEN();
        add(EITHER, nx);
        encode(node.left());
        add(OR);
        encode(node.right());
    }

    // {x y}  =  BOTH(nx), {x}, AND, {y}
    private void encodeAnd(Node node) {
        int nx = node.left().LEN();
        add(BOTH, nx);
        encode(node.left());
        add(AND);
        encode(node.right());
    }

    // {x?}  =  MAYBE ONE {x}
    private void encodeOpt(Node node) {
        add(MAYBE);
        add(ONE);
        encode(node.left());
    }

    // {x*}  =  MAYBE MANY {x}
    private void encodeAny(Node node) {
        add(MAYBE);
        add(MANY);
        encode(node.left());
    }

    // {x+}  =  DO AND MAYBE MANY {x}
    private void encodeSome(Node node) {
        add(DO);
        add(AND);
        add(MAYBE);
        add(MANY);
        encode(node.left());
    }

    // {[x]}  =  LOOK TRY {x}
    private void encodeTry(Node node) {
        add(LOOK);
        add(TRY);
        encode(node.left());
    }

    // {x&}  =  LOOK HAS {x}
    private void encodeHas(Node node) {
        add(LOOK);
        add(HAS);
        encode(node.left());
    }

    // {x!}  =  LOOK NOT {x}
    private void encodeNot(Node node) {
        add(LOOK);
        add(NOT);
        encode(node.left());
    }

    // {@}  =  DROP
    private void encodeDrop(Node node) {
        add(DROP);
    }

    // {@a}  =  ACT(a)
    private void encodeAct(Node node) {
        add(ACT.toString());
        add(node.name());
    }

    // {#e}  =  MARK(n)
    private void encodeMark(Node node) {
        add(MARK.toString());
        add(node.name());
    }

    // {%id}  =  TAG(n)
    private void encodeTag(Node node) {
        add(TAG.toString());
        add(node.text());
    }

    // {10}  =  STRING(1), 10
    // {"a"}  =  STRING(1), 97
    // {'a'}  =  STRING(1), 97
    // {128}  =  STRING(2), 194, 128
    private void encodeNumber(Node node) {
        int ch = node.charCode();
        add(STRING, bytes(new String(Character.toChars(ch))));
    }

    // {Nd}  =  CAT(Nd)
    private void encodeCat(Node node) {
        add(CAT.toString());
        add(node.text());
    }

    // {"ab"}  =  STRING(2), 97, 98
    // {"π"}  =  STRING(2), 207, 128
    // {""}  =  STRING(0)
    private void encodeString(Node node) {
        add(STRING, bytes(node.name()));
    }

    // {<a>}  =  LESS(1), 97
    // {<ab>}  =  LESS(2), 97, 98
    private void encodeDivider(Node node) {
        add(LESS, bytes(node.name()));
    }

    // {'a..z'}  =  LOW(1), 97, HIGH(1), 122
    // {'α..ω'}  =  LOW(2), 206, 177, HIGH(2), 207, 137
    private void encodeRange(Node node) {
        add(LOW, bytes(node.left().name()));
        add(HIGH, bytes(node.right().name()));
    }

    // {'a'}  =  STRING(1), 97
    // {'ab'}  =   SET(2), 97, 98
    // {'αβ'}  =   SET(4), 206, 177, 206, 178
    // {''}  =   SET 0
    private void encodeSet(Node node) {
        add(SET, bytes(node.name()));
    }

    private byte[] bytes(String s) {
        try { return s.getBytes("UTF8"); }
        catch (Exception e) { throw new Error(e); }
    }

    // Add a string to the output, with possible preceding comma and newline.
    private void add(String s) {
        int extra = 2 + s.length();
        if (line + extra >= 80) {
            line = extra;
            output.append(",\n");
        }
        else {
            line += extra;
            if (output.length() > 0) output.append(", ");
        }
        output.append(s);
        pc++;
    }

    private void add(Code op) {
        add(op.toString());
    }

    private void add(int n) {
        add("" + n);
    }

    // Encode an op and arg.
    private void add(Code op, int arg) {
        if (arg == 1) {
            add(op.toString());
        }
        else if (arg < 256) {
            add(op.toString() + "1");
            add(arg);
        }
        else if (arg < 65536) {
            add(op.toString() + "2");
            add(arg/256);
            add(arg%256);
        }
        else throw new Error("code too large");
    }

    private void add(byte[] bytes) {
//        add(bytes.length);
        for (byte b : bytes) add(b);
    }

    private void add(char[] text) {
        byte[] bytes;
        try { bytes = new String(text).getBytes("UTF8"); }
        catch (Exception e) { throw new Error(e); }
        add(bytes.length);
        for (byte b : bytes) add(b);
    }

    private void add(Code op, byte[] bytes) {
        add(op, bytes.length);
        add(bytes);
    }

    private void add(Code op, char[] text) {
        add(op.toString());
        add(text);
    }
}
