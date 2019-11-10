// Pecan 1.0 bytecode generator. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import java.io.*;
import java.text.*;
import static pecan.Op.*;
import static pecan.Code.*;
import static pecan.Node.Flag.*;
import static pecan.Node.Count.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/* Convert a grammar into bytecode. Each op consists of a sequence of bytes
where the top bit is set on all but the last. The first byte contains two bits
of operand and a 5-bit opcode, and each further byte contains 7 more bits of
operand, in big-endian order.

To test, the bytes are printed in an assembly language format, with offset
operands being converted to addresses. */

class Generator implements Testable {
    private boolean switchTest, testing;
    private Map<String,Integer> codes, actions, markers, tags, cats;
    private boolean hasCats;
    private StringBuilder text;
    private ByteArrayOutputStream bytes;
    private int pc, line;
    private boolean changed;

    public static void main(String[] args) {
        if (args.length == 0) Code.main(args);
        if (args.length == 0) Stacker.main(args);
        Generator generator = new Generator();
        generator.switchTest = true;
        for (Op op : Op.values()) {
            Node node = new Node(op, null, null);
            generator.encode(node);
        }
        generator.switchTest = false;
        generator.testing = true;
        Test.run(generator, args);
    }

    // Convert the grammar into bytecode in both binary and text forms.
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
        bytes = new ByteArrayOutputStream();
        text = new StringBuilder();
        gather(root);
        setSequence(root);
        if (! testing) printNames();
        changed = true;
        while (changed) {
            changed = false;
            pc = line = 0;
            bytes.reset();
            text.setLength(0);
            encode(root);
        }
        if (line > 0) text.append("\n");
        return text.toString();
    }

    // Gather names, in alphabetical order, then allocate numbers.
    private void gather(Node root) {
        codes = new TreeMap<>();
        actions = new TreeMap<>();
        markers = new TreeMap<>();
        tags = new TreeMap<>();
        cats = new TreeMap<>();
        hasCats = false;
        for (Code c : Code.values()) codes.put(c.toString(), c.ordinal());
        for (Category c : Category.values()) {
            cats.put(c.toString(), c.ordinal());
        }
        gatherNode(root);
        int i = 0;
        for (String k : actions.keySet()) actions.put(k, i++);
        i = 0;
        for (String k : markers.keySet()) markers.put(k, i++);
        i = 0;
        for (String k : tags.keySet()) tags.put(k, i++);
    }

    // Gather name info from a node.
    private void gatherNode(Node node) {
        switch (node.op()) {
            case Act: actions.put(node.rawText(), 0); break;
            case Mark: markers.put(node.rawText(), 0); break;
            case Tag: tags.put(node.rawText(), 0); break;
            case Cat: hasCats = true;
        }
        if (node.left() != null) gatherNode(node.left());
        if (node.right() != null) gatherNode(node.right());
    }

    // Print out name info.
    private void printNames() {
        System.out.println("Opcodes: " + codes.toString());
        if (! actions.isEmpty()) {
            System.out.println("Actions: " + actions.toString());
        }
        if (! markers.isEmpty()) {
            System.out.println("Markers: " + markers.toString());
        }
        if (! tags.isEmpty()) {
            System.out.println("Tags: " + tags.toString());
        }
        if (hasCats) {
            System.out.println("Categories: " + cats.toString());
        }
    }

    // Add sequence numbers to nodes.
    private void setSequence(Node node) {
        switch (node.op()) {
            case Act: node.set(SEQ, actions.get(node.rawText())); break;
            case Mark: node.set(SEQ, markers.get(node.rawText())); break;
            case Tag: node.set(SEQ, tags.get(node.rawText())); break;
            case Cat:
                node.set(SEQ, Category.valueOf(node.text()).ordinal()); break;
        }
        if (node.left() != null) setSequence(node.left());
        if (node.right() != null) setSequence(node.right());
    }

    // Get the binary version of the output.
    public byte[] getBytes() {
        return bytes.toByteArray();
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
            case Point:     encodePoint(node);  break;
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

    // {id = x}  =  START(nx), {x}, STOP
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

    // {x?}  =  MAYBE, ONE, {x}
    private void encodeOpt(Node node) {
        if (switchTest) return;
        add(MAYBE);
        add(ONE);
        encode(node.left());
    }

    // {x*}  =  MAYBE, MANY, {x}
    private void encodeAny(Node node) {
        if (switchTest) return;
        add(MAYBE);
        add(MANY);
        encode(node.left());
    }

    // {x+}  =  DO, AND, MAYBE, MANY, {x}
    private void encodeSome(Node node) {
        if (switchTest) return;
        add(DO);
        add(AND);
        add(MAYBE);
        add(MANY);
        encode(node.left());
    }

    // {[x]}  =  LOOK, SEE, {x}
    private void encodeSee(Node node) {
        if (switchTest) return;
        add(LOOK);
        add(SEE);
        encode(node.left());
    }

    // {x&}  =  LOOK, HAS, {x}
    private void encodeHas(Node node) {
        if (switchTest) return;
        add(LOOK);
        add(HAS);
        encode(node.left());
    }

    // {x!}  =  LOOK, NOT, {x}
    private void encodeNot(Node node) {
        if (switchTest) return;
        add(LOOK);
        add(NOT);
        encode(node.left());
    }

    // {@}  =  DROP;   {@n}  =  DROP(n)
    private void encodeDrop(Node node) {
        if (switchTest) return;
        int a = node.arity();
        if (a == 0) add(DROP);
        else add(DROP, a);
    }

    // {@a}  =  ARITY(n), ACT(a)
    private void encodeAct(Node node) {
        if (switchTest) return;
        if (node.arity() > 0) add(ARITY, node.arity());
        add(ACT, node.get(SEQ));
    }

    // {#e}  =  MARK(e)
    private void encodeMark(Node node) {
        if (switchTest) return;
        add(MARK, node.get(SEQ));
    }

    // {.}  =  POINT
    private void encodePoint(Node node) {
        if (switchTest) return;
        add(POINT);
    }

    // {%id}  =  TAG(n)
    private void encodeTag(Node node) {
        if (switchTest) return;
        add(TAG, node.get(SEQ));
    }

    // {Nd}  =  CAT(Nd)
    private void encodeCat(Node node) {
        if (switchTest) return;
        add(CAT, node.get(SEQ));
    }

    // {"ab"}  =  STRING(2), 97, 98
    // {"π"}  =  STRING(2), 207, 128
    private void encodeText(Node node) {
        if (switchTest) return;
        byte[] bs = node.rawText().getBytes(UTF_8);
        add(STRING, bs.length, bs);
    }

    // {""}  =  STRING(0)
    private void encodeSuccess(Node node) {
        if (switchTest) return;
        add(STRING, 0);
    }

    // {"a"}  =  STRING(1), 97
    private void encodeChar(Node node) {
        if (switchTest) return;
        byte[] bs = node.rawText().getBytes(UTF_8);
        add(STRING, bs.length, bs);
    }

    // {<a>}  =  LESS(1), 97
    // {<ab>}  =  LESS(2), 97, 98
    private void encodeSplit(Node node) {
        if (switchTest) return;
        byte[] bs = node.rawText().getBytes(UTF_8);
        add(SPLIT, bs.length, bs);
    }

    // {'a..z'}  =  LOW(1), 97, HIGH(1), 122
    // {'α..ω'}  =  LOW(2), 206, 177, HIGH(2), 207, 137
    private void encodeRange(Node node) {
        if (switchTest) return;
        String s = node.rawText();
        int n = s.indexOf("..");
        byte[] bs1 = s.substring(0,n).getBytes(UTF_8);
        byte[] bs2 = s.substring(n+2).getBytes(UTF_8);
        add(LOW, bs1.length, bs1);
        add(HIGH, bs2.length, bs2);
    }

    // {'a'}  =  STRING(1), 97
    // {'ab'}  =   SET(2), 97, 98
    // {'αβ'}  =   SET(4), 206, 177, 206, 178
    private void encodeSet(Node node) {
        if (switchTest) return;
        byte[] bs = node.rawText().getBytes(UTF_8);
        add(SET, bs.length, bs);
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

    // Encode an op with no arg.
    private void add(Code op) {
        print(pc, op);
        bytes.write(op.ordinal());
        pc++;
    }

    // Encode an op and arg.
    private void add(Code op, int arg) {
        int start = pc;
        write(op, arg);
        print(start, pc, op, arg);
    }

    // Encode an op, arg, and byte array.
    private void add(Code op, int arg, byte[] bs) {
        int start = pc;
        write(op, arg);
        print(start, pc, op, arg, bs);
        for (byte b : bs) bytes.write(b & 0xFF);
        pc += bs.length;
    }

    // Write bytes for an op and arg.
    private void write(Code op, int arg) {
        int[] suffixes = new int[5];
        int n = arg, ns = 0;
        while (n >= 4) {
            suffixes[ns++] = n & 0x7F;
            n = n >> 7;
        }
        int first = op.ordinal() + (n << 5);
        if (ns > 0) first = 0x80 | first;
        bytes.write(first);
        for (int i = ns - 1; i >= 0; i--) {
            if (i > 0) bytes.write(0x80 | suffixes[i]);
            else bytes.write(suffixes[i]);
        }
        pc += ns+1;
    }

    // Print an op with no arg.
    private void print(int start, Code op) {
        text.append("" + start + ": " + op.toString() + "\n");
    }

    // Print an op and arg.
    private void print(int start, int end, Code op, int arg) {
        text.append("" + start + ": " + op.toString());
        if (op == BACK) text.append(" " + (end - arg));
        else if (op.relative()) text.append(" " + (end + arg) + "\n");
        else text.append(" " + arg + "\n");
    }

    // Print an op, arg and byte array.
    private void print(int start, int end, Code op, int arg, byte[] bs) {
        text.append("" + start + ": " + op.toString());
        if (op == BACK) text.append(" " + (end - arg));
        else if (op.relative()) text.append(" " + (end + arg));
        else text.append(" " + arg);
        for (byte b : bs) text.append(" " + (b & 0xFF));
        text.append("\n");
    }
/*
    // Encode a byte.
    private void add(int b) {
        print(b);
        bytes.write(b);
        pc++;
    }

    // Encode a byte array.
    private void add(byte[] bs) {
        for (byte b : bs) add(b & 0xFF);
    }

    // Add a string to the text, with possible preceding comma and newline.
    private void print(String s) {

        if (op == BACK) arg = pc - arg;
        else if (relative[op]) arg = pc + arg;
        if (hasArg[op]) printf("%s %d\n", opnames[op], arg);
        else printf("%s\n", opnames[op]);

        String prefix = " ";
        if (text.length() == 0) prefix = "";
        int extra = prefix.length() + s.length();
        if (line + extra >= 80 || (line > 0 && s.startsWith("START"))) {
            text.append("\n");
            text.append(s);
            line = s.length();
        }
        else {
            text.append(prefix);
            text.append(s);
            line += extra;
        }
    }

    private void print(int n) {
        print("" + n);
    }
    */
}
