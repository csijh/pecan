// Generic Pecan bytecode interpreter.
#include "parser.h"
#include <stdio.h>
#include <assert.h>
// TODO report error markers better.

static char *opNames[] = {
    [STOP]="STOP", [OR]="OR", [AND]="AND", [MAYBE]="MAYBE", [ONE]="ONE",
    [MANY]="MANY", [DO]="DO", [LOOK]="LOOK", [TRY]="TRY", [HAS]="HAS",
    [NOT]="NOT", [DROP]="DROP", [MARK]="MARK", [START]="START", [GO]="GO",
    [BACK]="BACK", [EITHER]="EITHER", [BOTH]="BOTH", [ACT]="ACT",
    [STRING]="STRING", [LOW]="LOW", [HIGH]="HIGH", [LESS]="LESS", [SET]="SET",
    [CAT]="CAT", [TAG]="TAG"
};

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
// arg:     an argument to pass to the act function.
// look:    the nesting depth inside lookahead constructs.
// top:     the number of items on the call stack.
// out:     the number of delayed actions stored in the actions array.
// stack:   the call stack, i.e. return addresses and saved in/out values.
// actions: the delayed action codes.
struct state {
    byte *code, *input;
    int pc, start, in, marked;
    bits markers;
    bool ok;
    doAction *act;
    void *arg;
    int look, top, out;
    int stack[1000], actions[1000];
};
typedef struct state state;

// Initialize the parsing state.
static void new(state *s, byte c[], byte i[], doAction *act, void *arg) {
    s->code = c;
    s->input = i;
    s->pc = s->start = s->in = s->marked = 0;
    s->markers = 0L;
    s->ok = false;
    s->act = act;
    s->arg = arg;
    s->look = s->top = s->out = 0;
}

// Find the n'th entry point.
static void entry(state *s, int n) {
    for (int i = 0; i < n; i++) {
        int op = s->code[s->pc++];
        int arg = 1;
        if (op == START) ;
        else if (op == START1) arg = s->code[s->pc++];
        else if (op == START2) {
            arg = s->code[s->pc++];
            arg = (arg << 8) + s->code[s->pc++];
        }
        else assert(false);
        s->pc += arg + 1;
    }
}

// Carry out any delayed actions. For each delayed action, the input position at
// the time of recording is stored.
static inline void doActions(state *s) {
    for (int i = 0; i < s->out; i = i + 2) {
        int a = s->actions[i];
        int oldIn = s->actions[i+1];
        printf("doActions a = %d oldin = %d\n", a, oldIn);
        s->act(a, s->start, oldIn, s->arg);
        s->start = oldIn;
    }
    s->out = 0;
}
/*
// Extract a one-byte argument from the bytecode.
static inline int arg1(state *s) {
    return s->code[s->pc++];
}

// Extract a two-byte argument from the bytecode.
static inline int arg2(state *s) {
    return (s->code[s->pc++] << 8) | s->code[s->pc++];
}
*/
// {id = x}  =  START nx {x} STOP
// Initialise state state, call {x} returning to STOP.
static inline void doSTART(state *s, int arg) {
    printf("START ret = %d\n", s->pc + arg);
    s->stack[s->top++] = s->pc + arg;
}

// {id = x}  =  START nx {x} STOP
// At the end of parsing, carry out any outstanding delayed actions. Return zero
// for success, or a bitmap of markers for failure, with the top bit set in case
// there are no markers.
static inline bits doSTOP(state *s) {
    printf("STOP\n");
    if (s->ok && s->out > 0) doActions(s);
    if (s->ok) return 0L;
    if (s->in > s->marked) s->markers = 0L;
    s->markers |= 0x8000000000000000;
    printf("Parsing failed %lx\n", s->markers);
    return s->markers;
}

// {id}  =  GO n   or BACK n
// Skip forwards or backwards in the code, to tail-call a remote rule.
static inline void doGO(state *s, int arg) {
    s->pc = s->pc + arg;
}

// {x / y}  =  EITHER nx {x} OR {y}
// Save in/out, call x, returning to OR.
static inline void doEITHER(state *s, int arg) {
    s->stack[s->top++] = s->in;
    s->stack[s->top++] = s->out;
    s->stack[s->top++] = s->pc + arg;
}

// {x / y}  =  EITHER nx {x} OR {y}
// After x, check success and progress, return or continue with y.
static inline void doOR(state *s) {
    int saveOut = s->stack[--s->top];
    int saveIn = s->stack[--s->top];
    if (s->ok || s->in > saveIn) s->pc = s->stack[--s->top];
    else s->out = saveOut;
}

// {x y}  =  BOTH nx {x} AND {y}
// Call x, returning to AND.
static inline void doBOTH(state *s, int arg) {
    s->stack[s->top++] = s->pc + arg;
}

// {x y}  =  BOTH nx {x} AND {y}
// After x, check success, continue with y or return.
static inline void doAND(state *s) {
    if (! s->ok) s->pc = s->stack[--s->top];
}

// {x?}  =  MAYBE ONE {x},   and similarly for x*, x+
// Save in/out and call x, returning to ONE or MANY.
static inline void doMAYBE(state *s) {
    s->stack[s->top++] = s->in;
    s->stack[s->top++] = s->out;
    s->stack[s->top++] = s->pc;
    s->pc++;
}

// {x?}  =  MAYBE ONE {x}
// After x, check success or no progress and return.
static inline void doONE(state *s) {
    int saveOut = s->stack[--s->top];
    int saveIn = s->stack[--s->top];
    if (! s->ok && s->in == saveIn) {
        s->out = saveOut;
        s->ok = true;
    }
    s->pc = s->stack[--s->top];
}

// {x*}  =  MAYBE MANY {x}
// After x, check success and re-try x or return.
static inline void doMANY(state *s) {
    int saveOut = s->stack[--s->top];
    int saveIn = s->stack[--s->top];
    if (s->ok) {
        s->stack[s->top++] = s->in;
        s->stack[s->top++] = s->out;
        s->stack[s->top++] = s->pc - 1;
    }
    else {
        if (! s->ok && s->in == saveIn) {
            s->out = saveOut;
            s->ok = true;
        }
        s->pc = s->stack[--s->top];
    }
}

// {x+}  =  DO AND MAYBE MANY {x}
// Call x, returning to AND.
static inline void doDO(state *s) {
    s->stack[s->top++] = s->pc;
    s->pc = s->pc + 3;
}

// {[x]}  =  LOOK TRY x,   and similarly for x& and x!
// Save in/out and call x as a lookahead, returning to TRY or HAS or NOT.
static inline void doLOOK(state *s) {
    s->stack[s->top++] = s->in;
    s->stack[s->top++] = s->out;
    s->look++;
    s->stack[s->top++] = s->pc;
    s->pc++;
}

// {[x]}  =  LOOK TRY x
// Succeed and perform actions, or fail and discard them and backtrack.
static inline void doTRY(state *s) {
    int saveOut = s->stack[--s->top];
    s->in = s->stack[--s->top];
    s->look--;
    if (s->ok && s->look == 0 && s->out > 0) doActions(s);
    else s->out = saveOut;
}

// {x&}  =  LOOK HAS x
// After x, backtrack and return.
static inline void doHAS(state *s) {
    s->out = s->stack[--s->top];
    s->in = s->stack[--s->top];
    s->look--;
    s->pc = s->stack[--s->top];
}

// {x!}  =  LOOK NOT x
// After x, backtrack, invert the result, and return.
static inline void doNOT(state *s) {
    s->out = s->stack[--s->top];
    s->in = s->stack[--s->top];
    s->look--;
    s->ok = ! s->ok;
    s->pc = s->stack[--s->top];
}

// {@}  =  DROP
// Discard input and return.
static inline void doDROP(state *s) {
    s->start = s->in;
    s->pc = s->stack[--s->top];
}

// {@2add}  =  ACT add
// Delay the action and return success.
static inline void doACT(state *s, int arg) {
    printf("ACT in = %d, arg = %d\n", s->in, arg);
    s->actions[s->out++] = arg;
    s->actions[s->out++] = s->in;
    s->ok = true;
    printf("ACT top = %d\n", s->top);
    s->pc = s->stack[--s->top];
    printf("ACT pc = %d\n", s->pc);
}

// {#item}  =  MARK item
// Record an error marker. Assume arg < 63.
static inline void doMARK(state *s, int arg) {
    if (s->look = 0) {
        if (s->in > s->marked) {
            s->marked = s->in;
            s->markers = 0L;
        }
        s->markers = s->markers | (1L << arg);
    }
    s->ok = true;
    s->pc = s->stack[--s->top];
}

// {"abc"}  =  CHARS 3 'a' 'b' 'c'
// Match string and return success or failure.
static inline void doSTRING(state *s, int arg) {
    s->ok = true;
    for (int i = 0; i < arg && s->ok; i++) {
        byte b = s->input[s->in + i];
        if (b == '\0' || b != s->code[s->pc + i]) s->ok = false;
    }
    if (s->ok) {
        s->in = s->in + arg;
        if (s->look == 0 && s->out > 0) doActions(s);
    }
    s->pc = s->stack[--s->top];
}

// {'a..z'}  =  LOW n 'a' HIGH n 'z'
// Check >= 'a', continue with HIGH or return failure
static inline void doLOW(state *s, int arg) {
    printf("LOW\n");
    s->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = s->input[s->in + i];
        printf("b = %d, low = %d\n", b, s->code[s->pc + i]);
        if (b == '\0' || b < s->code[s->pc + i]) s->ok = false;
    }
    if (s->ok) s->pc = s->pc + arg;
    else {
        printf("About to return, top=%d, ret=%d\n", s->top, s->stack[s->top-1]);
        s->pc = s->stack[--s->top];
    }
}

// {'a..z'}  =  LOW n 'a' HIGH n 'z'
// Check <= 'z', return success or failure
static inline void doHIGH(state *s, int arg) {
    printf("HIGH\n");
    s->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = s->input[s->in + i];
        printf("b = %d, high = %d\n", b, s->code[s->pc + i]);
        if (b == '\0' || b > s->code[s->pc + i]) s->ok = false;
    }
    if (s->ok) {
        s->pc = s->pc + arg;
        s->in = s->in + arg;
        if (s->look == 0 && s->out > 0) doActions(s);
    }
    s->pc = s->stack[--s->top];
}

// {<abc>}  =  LESS 3 'a' 'b' 'c'
// Check if input < "abc", return.
static inline void doLESS(state *s, int arg) {
    s->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = s->input[s->in + i];
        if (b == '\0' || b >= s->code[s->pc + i]) s->ok = false;
    }
    s->pc = s->stack[--s->top];
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
static inline void doSET(state *s, int arg) {
    s->ok = false;
    int n = 0;
    for (int i = 0; i < arg && ! s->ok; ) {
        byte b = s->code[s->in + i];
        n = length(b);
        bool oki = true;
        for (int j = 0; j < n && oki; j++) {
            if (s->input[s->in + j] != s->code[s->pc + i + j]) oki = false;
        }
        if (oki) s->ok = true;
    }
    if (s->ok) {
        if (s->look == 0 && s->out > 0) doActions(s);
        s->in = s->in + n;
    }
    s->pc = s->stack[--s->top];
}

bits parseText(int n, byte code[], byte input[], doAction *act, void *arg) {
    state pData;
    state *s = &pData;
    new(s, code, input, act, arg);
    entry(s, n);
    while (true) {
        printf("%d: ", s->pc);
        byte op = s->code[s->pc++];
        int arg = 1;
        if (op >= START2) {
            arg = s->code[s->pc++];
            arg = (arg << 8) | s->code[s->pc++];
            op = op + (START - START2);
        }
        else if (op >= START1) {
            arg = s->code[s->pc++];
            op = op + (START - START1);
        }
        if (op >= START) printf("%s %d\n", opNames[op], arg);
        else printf("%s\n", opNames[op]);
        switch (op) {
        case START: doSTART(s, arg); break;
        case STOP: return doSTOP(s); break;
        case GO: doGO(s, arg); break;
        case BACK: doGO(s, -arg); break;
        case EITHER: doEITHER(s, arg); break;
        case OR: doOR(s); break;
        case BOTH: doBOTH(s, arg); break;
        case AND: doAND(s); break;
        case MAYBE: doMAYBE(s); break;
        case ONE: doONE(s); break;
        case MANY: doMANY(s); break;
        case DO: doDO(s); break;
        case LOOK: doLOOK(s); break;
        case TRY: doTRY(s); break;
        case HAS: doHAS(s); break;
        case NOT: doNOT(s); break;
        case DROP: doDROP(s); break;
        case ACT: doACT(s, arg); break;
        case MARK: doMARK(s, arg); break;
        case STRING: doSTRING(s, arg); break;
        case LOW: doLOW(s, arg); break;
        case HIGH: doHIGH(s, arg); break;
        case LESS: doLESS(s, arg); break;
        case SET: doSET(s, arg); break;
        }
// TAG, CAT,
    }
}

bits parseTokens(int n, byte code[], doNext *next, doAction *act, void *state) {
    
}
