// Generic Pecan bytecode interpreter.
#include "interpreter.h"
#include <stdio.h>
// TODO report error markers better.

// The parsing state.
// pc: the next byte in the code.
// top: the number of items on the call stack.
// start: the first input byte not yet processed.
// in: the current input byte.
// marked: high water mark in input which has markers.
// out: the number of delayed actions stored in the actions array.
// markers: a bitmap of expected items for reporting errors.
// ok: boolean to say whether the most recent parsing expression succeeded.
// state: a structure to pass to the act function.
struct parser {
    int pc, top, start, in, look, marked, out, saveIn, saveOut;
    uint64 markers;
    bool ok, end;
    void *state;
    doAction *act;
    byte *code, *input;
    int actions[1000], stack[1000];
};

// Find the length of a UTF-8 character.
static inline int length(byte first) {
    if ((first & 0x80) == 0) return 1;
    if ((first & 0xE0) == 0xC0) return 2;
    if ((first & 0xF0) == 0xE0) return 3;
    return 4;
}

// Carry out any delayed actions. For each delayed action, the input position at
// the time of recording is stored.
static inline void doActions(struct parser *p) {
    for (int i = 0; i < p->out; i++) {
        int a = p->actions[i++];
        int oldIn = p->actions[i];
        p->act(a, p->start, oldIn, p->state);
        p->start = oldIn;
    }
    p->out = 0;
}

// Extract a one-byte argument from the bytecode.
static inline int arg1(struct parser *p) {
    return p->code[p->pc++];
}

// Extract a two-byte argument from the bytecode.
static inline int arg2(struct parser *p) {
    return (p->code[p->pc++] << 8) | p->code[p->pc++];
}

// {id = x}  =  START nx {x} STOP
// Initialise parser state, call {x} returning to STOP.
static inline void doSTART(struct parser *p, int arg) {
    p->pc = p->start = p->in = 0;
    p->top = p->look = p->marked = 0;
    p->markers = 0L;
    p->ok = false;
    p->stack[p->top++] = p->pc + arg;
}

// {id = x}  =  START nx {x} STOP
// At the end of parsing, carry out any outstanding delayed actions. Return zero
// for success, or a bitmap of markers for failure, with the top bit set in case
// there are no markers.
static inline uint64 doSTOP(struct parser *p) {
    if (p->ok && p->out > 0) doActions(p);
    if (p->ok) return 0L;
    if (p->in > p->marked) p->markers = 0L;
    p->markers |= 0x8000000000000000;
    printf("Parsing failed %lx\n", p->markers);
    return p->markers;
}

// {id}  =  GO n   or BACK n
// Skip forwards or backwards in the code, to tail-call a remote rule.
static inline void doGO(struct parser *p, int arg) {
    p->pc = p->pc + arg;
}

// {x / y}  =  EITHER nx {x} OR {y}
// Save in/out, call x, returning to OR.
static inline void doEITHER(struct parser *p, int arg) {
    p->stack[p->top++] = p->in;
    p->stack[p->top++] = p->out;
    p->stack[p->top++] = p->pc + arg;
}

// {x / y}  =  EITHER nx {x} OR {y}
// After x, check success and progress, return or continue with y.
static inline void doOR(struct parser *p) {
    int saveOut = p->stack[--p->top];
    int saveIn = p->stack[--p->top];
    if (p->ok || p->in > saveIn) p->pc = p->stack[--p->top];
    else p->out = saveOut;
}

// {x y}  =  BOTH nx {x} AND {y}
// Call x, returning to AND.
static inline void doBOTH(struct parser *p, int arg) {
    p->stack[p->top++] = p->pc + arg;
}

// {x y}  =  BOTH nx {x} AND {y}
// After x, check success, continue with y or return.
static inline void doAND(struct parser *p) {
    if (! p->ok) p->pc = p->stack[--p->top];
}

// {x?}  =  MAYBE ONE {x},   and similarly for x*, x+
// Save in/out and call x, returning to ONE or MANY.
static inline void doMAYBE(struct parser *p) {
    p->stack[p->top++] = p->in;
    p->stack[p->top++] = p->out;
    p->stack[p->top++] = p->pc;
    p->pc++;
}

// {x?}  =  MAYBE ONE {x}
// After x, check success or no progress and return.
static inline void doONE(struct parser *p) {
    int saveOut = p->stack[--p->top];
    int saveIn = p->stack[--p->top];
    if (! p->ok && p->in == saveIn) {
        p->out = saveOut;
        p->ok = true;
    }
    p->pc = p->stack[--p->top];
}

// {x*}  =  MAYBE MANY {x}
// After x, check success and re-try x or return.
static inline void doMANY(struct parser *p) {
    int saveOut = p->stack[--p->top];
    int saveIn = p->stack[--p->top];
    if (p->ok) {
        p->stack[p->top++] = p->in;
        p->stack[p->top++] = p->out;
    }
    else {
        if (! p->ok && p->in == saveIn) {
            p->out = saveOut;
            p->ok = true;
        }
        p->pc = p->stack[--p->top];
    }
}

// {x+}  =  DO AND MAYBE MANY {x}
// Call x, returning to AND.
static inline void doDO(struct parser *p) {
    p->stack[p->top++] = p->pc;
    p->pc = p->pc + 3;
}

// {[x]}  =  LOOK TRY x,   and similarly for x& and x!
// Save in/out and call x as a lookahead, returning to TRY or HAS or NOT.
static inline void doLOOK(struct parser *p) {
    p->stack[p->top++] = p->in;
    p->stack[p->top++] = p->out;
    p->look++;
    p->stack[p->top++] = p->pc;
    p->pc++;
}

// {[x]}  =  LOOK TRY x
// Succeed and perform actions, or fail and discard them and backtrack.
static inline void doTRY(struct parser *p) {
    int saveOut = p->stack[--p->top];
    p->in = p->stack[--p->top];
    p->look--;
    if (p->ok && p->look == 0 && p->out > 0) doActions(p);
    else p->out = saveOut;
}

// {x&}  =  LOOK HAS x
// After x, backtrack and return.
static inline void doHAS(struct parser *p) {
    p->out = p->stack[--p->top];
    p->in = p->stack[--p->top];
    p->look--;
    p->pc = p->stack[--p->top];
}

// {x!}  =  LOOK NOT x
// After x, backtrack, invert the result, and return.
static inline void doNOT(struct parser *p) {
    p->out = p->stack[--p->top];
    p->in = p->stack[--p->top];
    p->look--;
    p->ok = ! p->ok;
    p->pc = p->stack[--p->top];
}

// 'a' = CHAR 'a'
// Recognise an ascii character.
static inline void doCHAR(struct parser *p, int arg) {
    p->ok = (p->input[p->in] == arg);
    if (p->ok) {
        if (p->look == 0 && p->out > saveOut) doActions(p);
        p->in++;
    }
    p->pc = p->stack[--p->top];
}




// Parse an input string, producing an integer. Each time round the main loop,
// carry out any delayed actions then execute an opcode.
uint64 parseText(byte code[], byte input[], doAction *act, void *state) {
}
