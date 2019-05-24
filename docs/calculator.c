#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <assert.h>

typedef unsigned char byte;

// Opcodes before RULE have no argument. Other opcodes have a one-byte argument.
// An extended opcode EXTEND+OP has a two-byte, big-endian, unsigned argument.
enum op {
    STOP, OR, AND, MAYBE, OPT, MANY, DO, LOOK, TRY, HAS, NOT,
    RULE, START, GO, BACK, EITHER, BOTH, CHAR, SET, SET2, SET3, SET4, STRING,
    LOW, HIGH, LESS, CAT, TAG, MARK, ACT,
    EXTEND
};

// Action constants. Reserve 0 for @ which drops characters.
enum action { number = 1 };

static byte code[] = {
    START, 8, LOW, 1, 48, HIGH, 1, 57, ACT, number, STOP
};

// Carry out a (delayed) action, returning the updated value of 'val'.
static inline
int doAction(int op, byte input[], int start, int end, int values[], int val) {
    if (op == number) {
        int n = 0;
        for (int i = start; i < end; i++) n = n * 10 + (input[i] - '0');
        values[val++] = n;
    }
    return val;
}

// Find the length of a UTF-8 character.
static inline int length(byte first) {
    if ((first & 0x80) == 0) return 1;
    if ((first & 0xE0) == 0xC0) return 2;
    if ((first & 0xF0) == 0xE0) return 3;
    return 4;
}

// Parse an input string, producing an integer. Each time round the main loop,
// carry out any delayed actions then execute an opcode.
int parse(byte input[]) {

    // The parsing state.
    int output[1000], stack[1000], values[1000];
    int pc, top, start, in, val, out, look, mark, markers, saveIn, saveOut;
    bool ok, act, end;

    while(! end) {

        // Carry out any delayed actions. An opcode requests this by setting the
        // act flag, rather than doing it directly, so that this code appears
        // only once. For each delayed action, the input position at the time of
        // recording is stored.
        if (act) {
            act = false;
            for (int i = 0; i < out; i++) {
                int op = output[i++];
                int oldIn = output[i];
                val = doAction(op, input, start, oldIn, values, val);
                start = oldIn;
            }
            out = 0;
        }

        // At the end of parsing, check success or failure and return. The STOP
        // action requests this by setting the end flag, rather than doing it
        // directly, in case there are outstanding actions to perform using the
        // above code.
        // TODO report error markers better.
        if (end) {
            if (ok) return values[0];
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
                pc = start = in = val = saveIn = saveOut = 0;
                top = look = mark = markers = 0;
                ok = act = end = false;
                stack[top++] = pc + arg;
                break;

            // id = x  After x, tidy up and arrange to return.
            case STOP:
                if (ok && out > 0) act = true;
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
                stack[top++] = out;
                stack[top++] = pc + arg;
                break;

            // x / y  After x, return or continue with y.
            case OR:
                saveOut = stack[--top];
                saveIn = stack[--top];
                if (ok || in > saveIn) pc = stack[--top];
                else out = saveOut;
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
                stack[top++] = out;
                stack[top++] = pc + arg;
                pc++;
                break;

            // x?  After x, check success and return.
            case OPT:
                saveOut = stack[--top];
                saveIn = stack[--top];
                if (! ok && in == saveIn) {
                    out = saveOut;
                    ok = true;
                }
                pc = stack[--top];
                break;

            // x*: after x, check success and re-try x or return.
            case MANY:
                saveOut = stack[--top];
                saveIn = stack[--top];
                if (ok) {
                    stack[top++] = in;
                    stack[top++] = out;
                }
                else {
                    if (! ok && in == saveIn) {
                        out = saveOut;
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

            // [x], x&, x!: start a lookahead.
            case LOOK:
                stack[top++] = in;
                stack[top++] = out;
                look++;
                stack[top++] = pc;
                pc = pc + 1;
                break;

            // x&: after x, backtrack and return.
            case HAS:
                out = stack[--top];
                in = stack[--top];
                look--;
                pc = stack[--top];
                break;

            // x!: after x, backtrack, inverted the result, and return.
            case NOT:
                out = stack[--top];
                in = stack[--top];
                look--;
                ok = ! ok;
                pc = stack[--top];
                break;

            // [x]: succeed and perform actions, or fail and discard them.
            case TRY:
                saveOut = stack[--top];
                in = stack[--top];
                look--;
                if (ok && look == 0) act = true;
                else out = saveOut;
                break;

            // 'a': recognise an ascii character.
            case CHAR:
                ok = (input[in] == arg);
                if (ok) {
                    if (look == 0 && out > saveOut) act = true;
                    in++;
                }
                pc = stack[--top];
                break;

            // 'abc': check for each character in a set.
            case SET: case SET2: case SET3: case SET4:
                ok = false;
                int k = 1 + (op - SET);
                for (int i = 0; i < arg && ! ok; i = i + k) {
                    bool okch = true;
                    for (int j = 0; j < k; j++) {
                        if (input[in+i+j] != code[pc+i+j]) okch = false;
                    }
                    if (okch) ok = true;
                }
                if (ok) {
                    if (look == 0 && out > saveOut) act = true;
                    in = in + k;
                }
                pc = stack[--top];
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
                    if (look == 0 && out > saveOut) act = true;
                }
                pc = stack[--top];
                break;

            // 'a'..'z': check >= 'a', return or continue with HIGH
            case LOW:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch < code[pc+i]) ok = false;
                    if (ch == '\0') break;
                }
                if (ok) pc = pc + arg;
                else pc = stack[--top];
                break;

            // 'a'..'z': check <= 'z' and return.
            case HIGH:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch > code[pc+i]) ok = false;
                }
                if (input[in] == '\0') ok = false;
                if (ok) {
                    in = in + length(input[in]);
                    if (look == 0 && out > saveOut) act = true;
                }
                pc = stack[--top];
                break;

            // <a>: check if input < "a", return
            case LESS:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch >= code[pc+i]) ok = false;
                }
                pc = stack[--top];
                break;

            // Categories and tags are not implemented.
            case CAT: case TAG:
                printf("Opcode %d not implemented\n", op);
                exit(1);
                break;

            // Record an error marker. Assume arg < 32.
            case MARK:
                if (look = 0) {
                    if (in > mark) {
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
                output[out++] = in;
                output[out++] = arg;
                ok = true;
                pc = stack[--top];
                break;
        }
    }
}
