#include <stdio.h>

typedef unsigned short op;
typedef char input;

enum opcode {
    STOP, RULE, GO, EITHER, OR, BOTH, AND, REPEAT, ONCE, MANY, LOOK, TRY, HAS,
    NOT, STRING, SET, RANGE, CAT, TAG, MARK, REPORT, DROP, ACTn, BEGIN, END
};

// digit = @< "0".."9" @>number
op code1[] = {
    [0] = BOTH, 4-2, AND, 5-4,
    [4] = BEGIN,
    [5] = BOTH, 9-7, AND, 12-9,
    [9] = RANGE, '0', '9',
    [12] = ACTn, 0,
    [14] = STOP
};
input *in1 = "2";
input *in1b = "x";

// Stacks
// ======
// Call stack.
// Lookahead stack: each entry an input pointer and a call stack location.
//    One kind is real lookahead
//    The other is one-item lookahead
//    On mismatch, compare 'in' with top.
//        If equal (and one-item?) then RETURN(false)
//        Else, do an exception jump, popping to the most recent lookahead
// Somewhere to put 'in' items connected with lookaheads.
//    Could be a stack controlled by the other stacks.
// Somewhere to put 'begin' markers associated with one-item lookahead
//    Could be the action stack.
// Action stack, gathering up actions during a lookahead.
// Output stack of items.
// Fail stack for gathering error markers. (Precompute?)

// Structure holding current parse state.
struct state {
    char *code; int pc;
    ushort calls[1024]; int call;
    input *points[1024]; int point;
    output *[1024]; int point;
    int fails[1024], failTop;
    int marks[1024], markTop, markSize;
};
typedef struct state state;

static inline void CALL(state *s, int x) { s->calls[s->call++] = x; s->pc = x; }
static inline void RETURN(state *s) { s->pc = s->calls[--s->call]; }
static inline void PUSH(state *s, input *in) { s->points[s->point++] = in; }
static inline input *POP(state *s) { return s->points[--s->point]; }
static inline int ARG(state *s) { int x = s->code[s->pc++]; return s->pc + x; }

// x y    BOTH &x AND &y      call x, returning to AND
static inline void doBoth(state *s) {
    CALL(s, ARG(s));
}

// x y    ... AND &y          if x succeeded, continue by calling y
static inline void doAnd(state *s) {
    if (! ok) RETURN(s);
    else CALL(s, ARG(s));
}

// @<     BEGIN ...           mark the start of a list of items
static inline void doBegin(state *s) {
    // Mark the place in the output stack.
}

char *parse(op *code, input *in, output *out) {
    state *s = malloc(sizeof(state));
    s->code = code;
    s->in = in;
    s->out = out;

}


int main() {
    return 0;
}
