#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "parser.h"

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


            // Categories and tags are not implemented.
            case CAT: case TAG:
                printf("Opcode %d not implemented\n", op);
                exit(1);
                break;


        }
    }
}
