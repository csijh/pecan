#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <assert.h>

typedef unsigned char byte;

// Opcodes before RULE have no argument. Other opcodes have a one-byte argument.
// An extended opcode EXTEND+OP has a two-byte, big-endian, unsigned argument.
enum op {
    STOP, OR, AND, MAYBE, OPT, MANY, DO, LOOK, TRY, HAS, NOT,
    RULE, START, GO, BACK, EITHER, BOTH, CHAR, SET, STRING, LOW, HIGH, LT,
    CAT, TAG, MARK, ACT,
    EXTEND
};

// Action constants. Reserve 0 for @ which drops characters.
enum action { number = 1 };

static byte code[] = {
    START, 8, LOW, 1, 48, HIGH, 1, 57, ACT, number, STOP
};

// Carry out a (delayed) action, returning the updated value of 'out'.
static inline
int doAction(int op, byte input[], int start, int end, int output[], int out) {
    if (op == number) {
        int n = 0;
        for (int i = start; i < end; i++) n = n * 10 + (input[i] - '0');
        output[out++] = n;
    }
    return out;
}

/// Define a jump table of functions, one for each opcode.
static instruction *table[] = {
    [STOP]=doStop, [OR]=doOr, [AND]=doAnd, [MAYBE]=doMaybe, [OPT]=doOpt, /*,
    [MANY], [DO],
    [LOOK], [TRY], [HAS], [NOT],
    [RULE], [START], [GO], [BACK], [EITHER], [BOTH], [CHAR], [SET], [STRING], [RANGE1], [RANGE2], [LT],
    [CAT], [TAG], [MARK], [ACT]*/
};

// Parse an input string, producing an integer. Each time round the main loop,
// carry out any delayed actions then execute an opcode.
int parse(byte *input) {

    // The parsing state.
    int stack[1000], delayed[1000], output[1000];
    int pc, top, start, in, out, delay, look, mark, markers, saveIn, saveDelay;
    bool ok, act, end;

    while(! end) {

        // Carry out any delayed actions, requested by the previous opcode
        // setting the act flag. For each delayed action, the input position at
        // the time of recording is stored.
        if (act) {
            act = false;
            for (int i = 0; i < delay; i++) {
                int op = delayed[i++];
                int oldIn = delayed[i];
                out = doAction(op, input, start, oldIn, output, out);
                start = oldIn;
            }
            delay = 0;
        }

        // At end, check success or failure and return.
        // TODO report error markers better.
        if (end) {
            if (ok) return output[0];
            if (in > mark) markers = 0;
            printf("Parsing failed %x\n", markers);
            exit(1);
        }

        // Carry out one instruction. Read in a one or two byte argument as
        // appropriate, and then dispatch the opcode.
        byte op = code[pc++];
        byte arg = 0;
        if (op >= RULE) arg = code[pc++];
        if (op >= EXTEND) {
            arg = (arg << 8) | code[pc++];
            op = op - EXTEND;
        }
        switch(op) {

            // id = x  label an entry point. continue with START.
            case RULE: break;

            // id = x  Entry point. Prepare, continue with x, returning to STOP.
            case START:
                pc = start = in = out = saveIn = saveOut = 0;
                top = look = mark = markers = 0;
                ok = act = end = false;
                stack[top++] = pc + arg;
                break;

            // id = x  After x, tidy up and arrange to return.
            case STOP:
                if (ok && delay > 0) act = true;
                end = true;
                break;

            // id  Skip forwards in the code. (Tail-call a remote rule.)
            case GO:
                pc = pc + arg;
                break;

            // id  Skip backwards in the code. (Tail-call a remote rule.)
            case BACK:
                pc = pc - arg;
                break;

            // x / y  Save current state, call x, returning to OR.
            case EITHER:
                stack[top++] = in;
                stack[top++] = delay;
                stack[top++] = pc + arg;
                break;

            // x / y  After x, return or continue with y.
            case OR:
                saveDelay = stack[--top];
                saveIn = stack[--top];
                if (ok || in > saveIn) pc = stack[--top];
                else delay = saveDelay;
                break;

            // x y  Continue with x, returning to AND.
            case BOTH:
                stack[top++] = pc + arg;
                break;

            // x y  After x, if it failed, return, else continue with y.
            case AND:
                if (! ok) pc = stack[--top];
                break;

            // x?, x*  Save state and jump to x, returning to OPT or MANY.
            case MAYBE:
                stack[top++] = in;
                stack[top++] = delay;
                stack[top++] = pc + arg;
                pc++;
                break;

            // x?  After x, check success and return.
            case OPT:
                saveDelay = stack[--top];
                saveIn = stack[--top];
                if (!ok && in == saveIn) {
                    delay = saveDelay;
                    ok = true;
                }
                pc = stack[--top];
                break;

            // x*: after x, check success and re-try x or return.
            case MANY:
                if (ok) {
                    stack[top-2] = in;
                    stack[top-1] = delay;
                }
                else {
                    saveDelay = stack[--top];
                    saveIn = stack[--top];
                    if (!ok && in == saveIn) {
                        delay = saveDelay;
                        ok = true;
                    }
                    pc = stack[--top];
                }
                break;

            // x+: jump to x, returning to AND.  (DO AND MAYBE MANY <x>)
            case DO:
                stack[top++] = pc;
                pc = pc + 3;
                break;


        }

    }
}

/*
            // [x], x&, x!: start a lookahead.
            case LOOK:
                saveIn = in;
                saveOut = out;
                look++;
                stack[top++] = pc;
                pc = pc + 1;
                break;

            // x&: after x, backtrack and report success.
            case HAS:
                look--;
                out = saveOut;
                in = saveIn;
                break;

            // x!: after x, backtrack and report inverted result.
            case NOT:
                look--;
                out = saveOut;
                in = saveIn;
                ok = ! ok;
                break;

            // [x]: succeed and perform actions, or fail and discard them.
            case TRY:
                look--;
                in = saveIn;
                if (ok && look == 0) doActions(output, saveOut, out);
                else out = saveOut;
                break;

            // 'a': recognise an ascii character.
            case CHAR:
                ok = (input[in] == arg);
                if (ok) {
                    if (look == 0 && out > saveOut) {
                        doActions(output, saveOut, out);
                    }
                    in++;
                }
                break;

            // 'abc': check for each character in a set.
            // TODO: support UTF-8
            case SET:
                ok = false;
                for (int i = 0; i < arg && !ok; i++) {
                    assert(input[in+i] < 128);
                    if (input[in+i] == code[pc+i]) ok = true;
                }
                if (ok) {
                    in++;
                    if (look == 0 && out > saveOut) {
                        doActions(output, saveOut, out);
                    }
                }
                break;

            // "abc": check for string and return
            case STRING:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch != code[pc+i]) ok = false;
                    if (ch == '\0') break;
                }
                if (ok) {
                    in = in + arg;
                    if (look == 0 && out > saveOut) doActions();
                }
                pc = stack[--top];
                break;

            // "a".."z": check >= "a", return or continue with RANGE2
            case RANGE1:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch < code[pc+i]) ok = false;
                    if (ch == '\0') break;
                }
                if (ok) pc = pc + arg;
                else pc = stack[--top];
                break;

            // "a".."z": check second half of range and return.
            case RANGE2:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch > code[pc+i]) ok = false;
                }
                if (ok) {
                    in = in + arg; // TODO check!
                    if (look == 0 && out > saveOut) doActions();
                }
                pc = stack[--top];
                break;

            // <a>: check if input < "a", return
            case LT:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch >= code[pc+i]) ok = false;
                }
                pc = stack[--top];
                break;

            // Categories are not implemented and other tags are not expected.
            case CAT: case TAG: case ACT1: case ACT2: case ACT3: case ACTN:
                printf("Opcode %d not implemented\n", op);
                exit(1);
                break;

            // Record an error marker.
            case MARK:
                assert(arg < 32);
                if (look = 0) {
                    if (mark != in) {
                        mark = in;
                        markers = 0;
                    }
                    markers = markers | (1 << arg);
                }
                ok = true;
                pc = stack[--top];
                break;

            // Delay any action and return success.
            case ACT:
                output[out++] = (in << 24) | arg;
                ok = true;
                pc = stack[--top];
                break;
        }
    }
}
*/
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
