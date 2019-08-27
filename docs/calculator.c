#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "parser.h"

// Action constants.
enum action { number = 0 };

static byte code[] = {
    // <pecan>
    START1, 6, LOW, 48, HIGH, 57, ACT1, number, STOP
    // </pecan>
};

// The state consists of the input and a stack of integers.
struct state { char *input; int top; int stack[1000]; };
typedef struct state state;

// Carry out an action.
static inline void act(int a, int start, int end, void *vs) {
    printf("act a = %d, start = %d, end = %d\n", a, start, end);
    state *s = vs;
    if (a == number) {
        int n = 0;
        for (int i = start; i < end; i++) n = n * 10 + (s->input[i] - '0');
        s->stack[s->top++] = n;
    }
}

// Call with one argument.
int main(int n, char *args[n]) {
    setbuf(stdout, NULL);
    assert(n == 2);
    state sData;
    state *s = &sData;
    s->input = args[1];
    bits result = parseText(0, code, args[1], act, s);
    if (result == 0L) printf("%d\n", s->stack[0]);
    else printf("Error\n");
}

/*
        // Carry out one instruction. Read in a one or two byte argument as
        // appropriate, and then dispatch the opcode.
        byte op = code[pc++];
        byte arg = 0;
        if (op >= RULE) arg = code[pc++];
        if (op >= EXTEND) {
            arg = (arg << 8) | code[pc++];
            op = op - EXTEND;
        }
*/
