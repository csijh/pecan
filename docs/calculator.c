// Read input string from user, parse, print. Loop.

typedef unsigned char byte;

// The first line of opcodes take no argument. The second block take a one-byte
// argument, The second block have  If EXTEND is added to an opcode which takes
// an argument, then the argument is two bytes forming a big-endian unsigned
// integer.

 The remaining
// opcodes have
// op & 0x10 non-zero
enum op {
    STOP, OR, AND, MAYBE, OPT, MANY, DO, THEN, LOOK, TRY, HAS, NOT, DROP,
    RULE, START, GO, BACK, EITHER, BOTH, CHAR, SET, STRING, GE, LE, CAT,
    TAG, MARK, ACT0, ACT1, ACT2, ACT3, ACTN,
    LRULE, LSTART, LGO, LBACK, LEITHER, LBOTH, LCHAR, LSET, LSTRING,
    LGE, LLE, LCAT, LTAG, LMARK, LACT0, LACT1, LACT2, LACT4, LACTN
};

num action { number };
static byte code[] = {
    START, 8, GE, 1, 48, LE, 1, 57, ACT, number, STOP
};


int parse(byte *input) {
    int output[1000], stack[1000];
    int pc=0, op, arg, in=0, out=0, top=0, look=0, saveIn, saveOut, ok;

    while(1) {
        op = code[pc++];
        if (op >= RULE) arg = code[pc++];
        if (op >= LRULE) {
            arg = (arg << 8) | code[pc++];
            op = op + RULE - LRULE;
        }
        switch(*pc++) {

            // id = x: entry point; arrange to return to STOP.
            case START: stack[top++] = pc + arg; break;

            // id = x: return the result of parsing.
            case STOP: return output[0]; break;

            // id: skip forwards in the code, to tail-call a remote rule.
            case GO: pc = pc + arg; break;

            // id: skip backwards in the code, to tail-call a remote rule.
            case BACK: pc = pc - arg; break;

            // x / y: Save current state, call x, returning to OR.
            case EITHER:
                saveIn = in;
                saveOut = out;
                stack[top++] = pc + arg;
                break;

            // x / y: Return or continue with y.
            case OR:
                if (ok || in > saveIn) pc = stack[--top];
                else out = saveOut;
                break;

            // x y: Arrange to return to AND and continue with x.
            case BOTH:
                stack[top++] = pc + arg;
                break;

            // x y: If x has failed return, else continue with y.
            case AND:
                if (! ok) pc = stack[--top];
                break;

            // x?, x*: jump to x, returning to OPT or MANY.
            case MAYBE:
                saveIn = in;
                saveOut = out;
                stack[top++] = pc + arg;
                pc++;
                break;

            // x?: check success of x and return.
            case OPT:
                if (!ok && in == saveIn) {
                    out = saveOut;
                    ok = true;
                }
                pc = stack[--top];
                break;

            // x*: check success and return or continue to re-try x.
            case MANY:
                if (ok) {
                    saveIn = in;
                    saveOut = out;
                }
                else {
                    if (!ok && in == saveIn) {
                        out = saveOut;
                        ok = true;
                    }
                    pc = stack[--top];
                }
                break;

            // x+: jump to x, returning to THEN.
            case DO:
                stack[top++] = pc;
                pc = pc + 3;
                break;

            // x+ = x x*: check first x, return or continue with x*
            case THEN:
                if (! ok) pc = stack[--top];
                break;

            // "a".."z": check first half of range; if succeed, continue with LE
            case GE:
                ok = true;
                // TODO check length of input.
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch < code[pc+i]) ok = false;
                }
                if (ok) pc = pc + arg;
                else pc = stack[--top];
                break;

            // "a".."z": check second half of range and return.
            case LE:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch > code[pc+i]) ok = false;
                }
                if (ok) pc = pc + arg;
                pc = stack[--top];
                break;


                int low = node.left().value();
                int high = node.right().value();
                ok = false;
                if (in < input.length()) {
                    int ch = input.codePointAt(in);
                    ok = (ch >= low) && (ch <= high);
                    if (ok) {
                        if (lookahead == 0 && out > 0) takeActions();
                        int n = Character.charCount(ch);
                        in += n;
                        if (tracing) traceInput();
                    }
                }
                break;

            // [x], x&, x!: start a lookahead.
            case LOOK:
                saveIn = in;
                saveOut = out;
                look++;
                stack[top++] = pc;
                pc = pc + 1;
                break;

            // [x]: succeed and perform actions, or fail and discard them.
            case TRY:
                look--;
                in = saveIn;
                if (ok && look == 0) takeActions();
            if (ok) parse(node.left());
            else out = saveOut;
            break;


            LOOK, TRY, HAS, NOT, DROP,
            RULE, CHAR, SET, STRING, CAT,
            TAG, MARK, ACT0, ACT1, ACT2, ACT3, ACTN,


        }

case ACT: act(); break;
    }
}
/*

int saveIn, saveOut, saveMark, length;
String text;
if (tracing && ! skipTrace) System.out.println(node.trace());
skipTrace = false;
switch(node.op()) {
case SOME:
    saveIn = in;
    saveOut = out;
    parse(node.left());
    if (!ok && in == saveIn) out = saveOut;
    if (!ok) return;
    saveIn = in;
    saveOut = out;
    while (ok) {
        saveIn = in;
        saveOut = out;
        parse(node.left());
    }
    if (!ok && in == saveIn) {
        out = saveOut;
        ok = true;
    }
    break;
case TRY:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    in = saveIn;
    if (ok && lookahead == 0) takeActions();
    if (ok) parse(node.left());
    else out = saveOut;
    break;
case HAS:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    out = saveOut;
    in = saveIn;
    break;
case NOT:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    out = saveOut;
    in = saveIn;
    ok = !ok;
    break;
case CHAR:
    if (in >= input.length()) ok = false;
    else {
        int ch = input.codePointAt(in);
        ok = (ch == node.value());
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case STRING:
    length = node.text().length() - 2;
    text = node.text().substring(1, length+1);
    ok = true;
    if (in + length > input.length()) ok = false;
    else for (int i=0; i<length; i++) {
        if (input.charAt(in+i) != text.charAt(i)) { ok = false; break; }
    }
    if (ok) {
        if (lookahead == 0 && out > 0) takeActions();
        in += length;
        if (tracing) traceInput();
    }
    break;
case SET:
    length = node.text().length() - 2;
    text = node.text().substring(1, length+1);
    ok = false;
    if (in >= input.length()) { }
    else for (int i=0; i<length; i++) {
        if (input.charAt(in) != text.charAt(i)) continue;
        if (lookahead == 0 && out > 0) takeActions();
        if (Character.isHighSurrogate(text.charAt(i))) {
            i++;
            if (input.charAt(in+1) != text.charAt(i)) continue;
            in++;
        }
        in++;
        if (tracing) traceInput();
        ok = true;
    }
    break;
case RANGE:
    int low = node.left().value();
    int high = node.right().value();
    ok = false;
    if (in < input.length()) {
        int ch = input.codePointAt(in);
        ok = (ch >= low) && (ch <= high);
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case CAT:
    ok = false;
    int cats = node.value();
    if (in < input.length()) {
        int ch = input.codePointAt(in);
        int bit = 1 << Character.getType(ch);
        ok = ((cats & bit) != 0);
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case TAG:
    String query;
    if (node.text().charAt(0) == '%') query = node.text().substring(1);
    else query = node.text().substring(1, node.text().length()-1);
    if (query.length() == 0) ok = in == input.length();
    else ok = input.startsWith(query, in);
    if (ok) {
        start = in;
        if (lookahead == 0 && out > 0) takeActions();
        in += query.length();
        while (in < input.length() &&
            (input.charAt(in) == ' ' || input.charAt(in) == '\n')) in++;
        if (tracing) traceInput();
    }
    break;
case MARK:
    ok = true;
    if (lookahead > 0) break;
    if (mark != in) { mark = in; failures.clear(); }
    failures.set(node.value());
    break;
case DROP:
    ok = true;
    delay[out++] = node;
    break;
case ACT:
    ok = true;
    if (lookahead > 0) break;
    delay[out++] = node;
    break;
*/
