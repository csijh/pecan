#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "interpreter.h"

// Action constants.
enum action { number = 0 };

static byte code[] = {
    START, 8, LOW, 48, HIGH, 57, ACT, number, STOP
};

// Carry out a (delayed) action, returning the updated value of 'val'.
static inline
int doAction(int act, byte input[], int start, int end, int values[], int val) {
    if (act == number) {
        int n = 0;
        for (int i = start; i < end; i++) n = n * 10 + (input[i] - '0');
        values[val++] = n;
    }
    return val;
}

// ---- Interpreter ------------------------------------------------------------
// The remainder of this program is a generic interpreter for Pecan bytecode.

// Parse an input string, producing an integer. Each time round the main loop,
// carry out any delayed actions then execute an opcode.
int parse(byte input[], act) {

    // The parsing state.
    int output[1000], stack[1000], values[1000];
    int pc, top, start, in, val, out, look, mark, markers, saveIn, saveOut;
    bool ok, act, end;

    while(! end) {

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
