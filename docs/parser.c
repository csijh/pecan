// Generic Pecan bytecode interpreter.
#include "parser.h"
#include <stdio.h>
// TODO report error markers better.

// The parsing state.
// code:    the bytecode.
// input:   the text to be parsed.
// pc:      the next byte in the code.
// start:   the first input byte not yet processed.
// in:      the current input byte.
// marked:  high water mark in input which has markers.
// markers: a bitmap of expected items for reporting errors.
// ok:      boolean to say whether the most recent parsing expression succeeded.
// act:     an external function to call to carry out an action.
// state:   a structure to pass to the act function.
// look:    the nesting depth inside lookahead constructs.
// top:     the number of items on the call stack.
// out:     the number of delayed actions stored in the actions array.
// stack:   the call stack, i.e. return addresses and saved in/out values.
// actions: the delayed action codes.
struct parser {
    byte *code, *input;
    int pc, start, in, marked;
    uint64 markers;
    bool ok;
    doAction *act;
    void *state;
    int look, top, out;
    int stack[1000], actions[1000];
};

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

// {@}  =  DROP
// Discard input and return.
static inline void doDROP(struct parser *p) {
    p->start = p->in;
    p->pc = p->stack[--p->top];
}

// {@2add}  =  ACT add
// Delay the action and return success.
static inline void doACT(struct parser *p, int arg) {
    p->actions[p->out++] = p->in;
    p->actions[p->out++] = arg;
    p->ok = true;
    p->pc = p->stack[--p->top];
}

// {#item}  =  MARK item
// Record an error marker. Assume arg < 63.
static inline void doMARK(struct parser *p, int arg) {
    if (p->look = 0) {
        if (p->in > p->marked) {
            p->marked = p->in;
            p->markers = 0L;
        }
        p->markers = p->markers | (1L << arg);
    }
    p->ok = true;
    p->pc = p->stack[--p->top];
}

// {'a'}  =  CHAR 'a'
// Recognise an ascii character, return success or failure.
static inline void doCHAR(struct parser *p, int arg) {
    p->ok = (p->input[p->in] == arg);
    if (p->ok) {
        if (p->look == 0 && p->out > 0) doActions(p);
        p->in++;
    }
    p->pc = p->stack[--p->top];
}

// {"abc"}  =  CHARS 3 'a' 'b' 'c'
// Match string and return success or failure.
static inline void doCHARS(struct parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg && p->ok; i++) {
        byte b = p->input[p->in + i];
        if (b == '\0' || b != p->code[p->pc + i]) p->ok = false;
    }
    if (p->ok) {
        p->in = p->in + arg;
        if (p->look == 0 && p->out > 0) doActions(p);
    }
    p->pc = p->stack[--p->top];
}

// {'a..z'}  =  LOW 'a' HIGH 'z'
// Check >= 'a', continue with HIGH or return failure
static inline void doLOW(struct parser *p, int arg) {
    p->ok = true;
    byte b = p->input[p->in];
    if (b == '\0' || b < arg) p->ok = false;
    if (! p->ok) p->pc = p->stack[--p->top];
}

// {'a..z'}  =  LOWS n 'a' HIGHS n 'z'
// Check >= 'a', continue with HIGH or return failure
static inline void doLOWS(struct parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = p->input[p->in + i];
        if (b == '\0' || b < p->code[p->pc + i]) p->ok = false;
    }
    if (p->ok) p->pc = p->pc + arg;
    else p->pc = p->stack[--p->top];
}

// {'a..z'}  =  LOW 'a' HIGH 'z'
// Check <= 'z', return success or failure
static inline void doHIGH(struct parser *p, int arg) {
    p->ok = true;
    byte b = p->input[p->in];
    if (b == '\0' || b > arg) p->ok = false;
    if (p->ok) {
        p->in++;
        if (p->look == 0 && p->out > 0) doActions(p);
    }
    else p->pc = p->stack[--p->top];
}

// {'a..z'}  =  LOWS n 'a' HIGHS n 'z'
// Check <= 'z', return success or failure
static inline void doHIGHS(struct parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = p->input[p->in + i];
        if (b == '\0' || b > p->code[p->pc + i]) p->ok = false;
    }
    if (p->ok) {
        p->in = p->in + arg;
        if (p->look == 0 && p->out > 0) doActions(p);
    }
    else p->pc = p->stack[--p->top];
}

// {<a>}  =  BELOW 'a'
// Check if input < 'a', return.
static inline void doBELOW(struct parser *p, int arg) {
    p->ok = true;
    byte b = p->input[p->in];
    if (b == '\0' || b >= arg) p->ok = false;
    p->pc = p->stack[--p->top];
}

// {<abc>}  =  BELOWS 3 'a' 'b' 'c'
// Check if input < "abc", return.
static inline void doBELOWS(struct parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = p->input[p->in + i];
        if (b == '\0' || b >= p->code[p->pc + i]) p->ok = false;
    }
    p->pc = p->stack[--p->top];
}

// Find the length of a UTF-8 character from its first byte.
static inline int length(byte first) {
    if ((first & 0x80) == 0) return 1;
    if ((first & 0xE0) == 0xC0) return 2;
    if ((first & 0xF0) == 0xE0) return 3;
    return 4;
}

// {'abc'}  =  SET 3 'a' 'b' 'c'
// Check for one of the characters in a set, and return.
static inline void doSET(struct parser *p, int arg) {
    p->ok = false;
    int n = 0;
    for (int i = 0; i < arg && ! p->ok; ) {
        byte b = p->code[p->in + i];
        n = length(b);
        bool oki = true;
        for (int j = 0; j < n && oki; j++) {
            if (p->input[p->in + j] != p->code[p->pc + i + j]) oki = false;
        }
        if (oki) p->ok = true;
    }
    if (p->ok) {
        if (p->look == 0 && p->out > 0) doActions(p);
        p->in = p->in + n;
    }
    p->pc = p->stack[--p->top];
}

// Parse an input string, producing an integer. Each time round the main loop,
// carry out any delayed actions then execute an opcode.
uint64 parseText(byte code[], byte input[], doAction *act, void *state) {
    struct parser pData;
    struct parser *p = &pData;
    while (true) switch (p->code[p->pc++]) {
        case START: doSTART(p, arg1(p)); break;
        case START2: doSTART(p, arg2(p)); break;
        case STOP: return doSTOP(p); break;
        case GO: doGO(p, arg1(p)); break;
        case GO2: doGO(p, arg2(p)); break;
        case BACK: doGO(p, -arg1(p)); break;
        case BACK2: doGO(p, -arg2(p)); break;
        case EITHER: doEITHER(p, arg1(p)); break;
        case EITHER2: doEITHER(p, arg2(p)); break;
        case OR: doOR(p); break;
        case BOTH: doBOTH(p, arg1(p)); break;
        case BOTH2: doBOTH(p, arg2(p)); break;
        case AND: doAND(p); break;
        case MAYBE: doMAYBE(p); break;
        case ONE: doONE(p); break;
        case MANY: doMANY(p); break;
        case DO: doDO(p); break;
        case LOOK: doLOOK(p); break;
        case TRY: doTRY(p); break;
        case HAS: doHAS(p); break;
        case NOT: doNOT(p); break;
        case DROP: doDROP(p); break;
        case ACT: doACT(p, arg1(p)); break;
        case MARK: doMARK(p, arg1(p)); break;
        case CHAR: doCHAR(p, arg1(p)); break;
        case CHARS: doCHARS(p, arg1(p)); break;
        case LOW: doLOW(p, arg1(p)); break;
        case LOWS: doLOWS(p, arg1(p)); break;
        case HIGH: doHIGH(p, arg1(p)); break;
        case HIGHS: doHIGHS(p, arg1(p)); break;
        case BELOW: doBELOW(p, arg1(p)); break;
        case BELOWS: doBELOWS(p, arg1(p)); break;
        case SET: doSET(p, arg1(p)); break;

// TAG, CAT,
    }
}
