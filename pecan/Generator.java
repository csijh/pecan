// Pecan 1.0 bytecode generator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Code.*;
import static pecan.Node.Flag.*;
import static pecan.Node.Count.*;

/* Convert a grammar into bytecode.
TODO markers: is there a lift optimisation?
*/

class Generator implements Testable {
    private boolean switchTest;
    private StringBuilder output;
    private int pc, line;
    private boolean changed;

    public static void main(String[] args) {
        if (args.length == 0) Checker.main(args);
        Generator generator = new Generator();
        generator.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, null);
            generator.encode(node);
        }
        generator.switchTest = false;
        Test.run(generator, args);
    }

    // Convert the grammar into bytecode
    public String run(Source grammar) {
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
        if (pc != node.get(PC)) changed = true;
        node.set(PC,pc);
        switch (node.op()) {
            case Error: case Temp: case Empty: break;
            case List:      encodeList(node);   break;
            case Rule:      encodeRule(node);   break;
            case Id:        encodeId(node);     break;
            case Or:        encodeOr(node);     break;
            case And:       encodeAnd(node);    break;
            case Opt:       encodeOpt(node);    break;
            case Any:       encodeAny(node);    break;
            case Some:      encodeSome(node);   break;
            case See:       encodeSee(node);    break;
            case Has:       encodeHas(node);    break;
            case Not:       encodeNot(node);    break;
            case Drop:      encodeDrop(node);   break;
            case Act:       encodeAct(node);    break;
            case Mark:      encodeMark(node);   break;
            case Tag:       encodeTag(node);    break;
            case Cat:       encodeCat(node);    break;
            case Text:      encodeText(node);   break;
            case Success:   encodeSuccess(node); break;
            case Char:      encodeChar(node);   break;
            case Range:     encodeRange(node);  break;
            case Split:     encodeSplit(node);  break;
            case Set:       encodeSet(node);    break;
            case Fail:      encodeFail(node);   break;
            case Eot:       encodeEot(node);    break;
            default: assert false : "Unexpected node type " + node.op(); break;
        }
        if (node.get(LEN) != pc - node.get(PC)) changed = true;
        node.set(LEN, pc - node.get(PC));
    }

    private void encodeList(Node node) {
        encode(node.left());
        encode(node.right());
    }

    // {id = x; ...}  =  START, nx, {x}, STOP ...
    private void encodeRule(Node node) {
        if (switchTest) return;
        add(START, node.right().get(LEN));
        encode(node.right());
        add(STOP);
    }

    // {id}  =  GO(n)    or    BACK(n)
    private void encodeId(Node node) {
        if (switchTest) return;
        int target = node.ref().right().get(PC);
        int offset = target - (pc + node.get(LEN));
        if (offset >= 0) add(GO, offset);
        else add(BACK, -offset);
    }

    // {x / y}  =  EITHER(nx), {x}, OR, {y}
    private void encodeOr(Node node) {
        if (switchTest) return;
        int nx = node.left().get(LEN);
        add(EITHER, nx);
        encode(node.left());
        add(OR);
        encode(node.right());
    }

    // {x y}  =  BOTH(nx), {x}, AND, {y}
    private void encodeAnd(Node node) {
        if (switchTest) return;
        int nx = node.left().get(LEN);
        add(BOTH, nx);
        encode(node.left());
        add(AND);
        encode(node.right());
    }

    // {x?}  =  MAYBE ONE {x}
    private void encodeOpt(Node node) {
        if (switchTest) return;
        add(MAYBE);
        add(ONE);
        encode(node.left());
    }

    // {x*}  =  MAYBE MANY {x}
    private void encodeAny(Node node) {
        if (switchTest) return;
        add(MAYBE);
        add(MANY);
        encode(node.left());
    }

    // {x+}  =  DO AND MAYBE MANY {x}
    private void encodeSome(Node node) {
        if (switchTest) return;
        add(DO);
        add(AND);
        add(MAYBE);
        add(MANY);
        encode(node.left());
    }

    // {[x]}  =  LOOK SEE {x}
    private void encodeSee(Node node) {
        if (switchTest) return;
        add(LOOK);
        add(SEE);
        encode(node.left());
    }

    // {x&}  =  LOOK HAS {x}
    private void encodeHas(Node node) {
        if (switchTest) return;
        add(LOOK);
        add(HAS);
        encode(node.left());
    }

    // {x!}  =  LOOK NOT {x}
    private void encodeNot(Node node) {
        if (switchTest) return;
        add(LOOK);
        add(NOT);
        encode(node.left());
    }

    // {@}  =  DROP
    private void encodeDrop(Node node) {
        if (switchTest) return;
        add(DROP);
    }

    // {@a}  =  ACT(a)
    private void encodeAct(Node node) {
        if (switchTest) return;
        add(ACT.toString());
        add(node.name());
    }

    // {#e}  =  MARK(n)
    private void encodeMark(Node node) {
        if (switchTest) return;
        add(MARK.toString());
        add(node.name());
    }

    // {%id}  =  TAG(n)
    private void encodeTag(Node node) {
        if (switchTest) return;
        add(TAG.toString());
        add(node.name());
    }

    // {Nd}  =  CAT(Nd)
    private void encodeCat(Node node) {
        if (switchTest) return;
        add(CAT.toString());
        add(node.text());
    }

    // {"ab"}  =  STRING(2), 97, 98
    // {"π"}  =  STRING(2), 207, 128
    private void encodeText(Node node) {
        if (switchTest) return;
        add(STRING, bytes(node.name()));
    }

    // {""}  =  STRING(0)
    private void encodeSuccess(Node node) {
        if (switchTest) return;
        add(STRING, 0);
    }

    // {"a"}  =  STRING(1), 97
    private void encodeChar(Node node) {
        if (switchTest) return;
        add(STRING, bytes(node.name()));
    }

    // {<a>}  =  LESS(1), 97
    // {<ab>}  =  LESS(2), 97, 98
    private void encodeSplit(Node node) {
        if (switchTest) return;
        add(LESS, bytes(node.name()));
    }

    // {'a..z'}  =  LOW(1), 97, HIGH(1), 122
    // {'α..ω'}  =  LOW(2), 206, 177, HIGH(2), 207, 137
    private void encodeRange(Node node) {
        if (switchTest) return;
        String s = node.name();
        int n = s.indexOf("..");
        add(LOW, bytes(s.substring(0,n)));
        add(HIGH, bytes(s.substring(n+2)));
    }

    // {'a'}  =  STRING(1), 97
    // {'ab'}  =   SET(2), 97, 98
    // {'αβ'}  =   SET(4), 206, 177, 206, 178
    private void encodeSet(Node node) {
        if (switchTest) return;
        add(SET, bytes(node.name()));
    }

    // {''}  =   SET 0
    private void encodeFail(Node node) {
        if (switchTest) return;
        add(SET, 0);
    }

    // {<>}  =  EOT
    private void encodeEot(Node node) {
        if (switchTest) return;
        add(EOT);
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
        if (arg == 1 && op.hasText()) {
            add(op.toString() + "1");
        }
        else if (arg < 256) {
            add(op.toString());
            add(arg);
        }
        else if (arg < 65536 && op.hasOffset()) {
            add(op.toString() + "L");
            add(arg/256);
            add(arg%256);
        }
        else throw new Error("operand out of range");
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
