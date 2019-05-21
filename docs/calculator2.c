#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <assert.h>

typedef unsigned char byte;

// Opcodes before RULE have no argument. Other opcodes have a one-byte argument.
// An extended opcode EXTEND+OP has a two-byte, big-endian, unsigned argument.
enum op {
    STOP, OR, AND, MAYBE, OPT, MANY, DO, LOOK, TRY, HAS, NOT,
    RULE, START, GO, BACK, EITHER, BOTH, CHAR, SET, STRING, RANGE1, RANGE2, LT,
    CAT, TAG, MARK, ACT,
    EXTEND
};

struct state {
    byte *code, *input;
    int output[1000], delayed[1000];
    int start, in, out, delay;
    int look, mark, markers;
};
typedef struct state *state;

typedef bool function(state *s, byte *pc);

static bool bothAnd(state *s, byte *pc);

static function *table[] = {
    [BOTH] = bothAnd
};

static inline bool call(state *s, byte *pc) {
    // TODO deal with EXTEND
    return table[*pc](s, pc+1);
}

// Carry out delayed actions. Each action is stored as an opcode and a saved
// value of in. The drop action discards input characters, and the number action
// turns them into an output integer.
static inline void doActions(state *s) {
    for (int i = 0; i < s->delay; i++) {
        int op = output[i] & 0xFF, oldIn = output[i] >> 24;
        if (op == number) output[saveOut++] = getInt(start, oldIn);
        start = oldIn;
    }
    out = saveOut;
}


// id = x  Entry point. Call x. Tidy up.
static bool startStop(state *s, byte *pc) {
    byte *x = pc + 1;
    bool ok = call(s, x);
    if (ok && s->delay > 0) doActions(s);
    if (!ok && in > mark) markers = 0;
    return ok;
}

static bool bothAnd(state *s, byte *pc) {
    int n = *pc++;
    byte *x = pc, *y = pc + n + 1;
    bool ok = call(s, x);
    if (! ok) return false;
    return call(s, y);
}


int calculate(byte *input) {
    struct state sData;
    struct state *s = &sData;
    s->start = s->in = s->out = s->delay = 0;
    s->look = s->mark = s->markers = 0;
    byte *pc = code;
    bool ok = call(s, pc);
    // TODO report errors properly
    if (! ok) {
        printf("Parsing failed\n");
        exit(1);
    }
}
