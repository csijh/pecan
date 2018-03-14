/* */

// Types represnting items parsed (characters or tokens), items produced (values
// or tree nodes) and an object representing the state of the action module.
typedef int input;
typedef int output;
typedef int actor;

// Get the tag of the current input item, or move past it.
int getTag(input *in);
input *next(input *in);

// The type of the functions which carry out actions. The actor is the object
// which does the acting. The tag specifies which action. Start and end mark the
// input items matched since the last action or discard. Out is a pointer to the
// output items to process, and n is how many.
output act0(actor a, int tag, int start, int end);
output act(actor a, int tag, output *out, int n);

// -----------------------------------------------------------------------------

typedef unsigned char byte;
typedef unsigned short ushort;

enum opcode {
    STOP, RULE, GO, EITHER, OR, BOTH, AND, REPEAT, ONCE, MANY, LOOK, TRY, HAS,
    NOT, STRING, SET, RANGE, CAT, TAG, MARK, REPORT, DROP, ACT
};

char *names[] = {
    "STOP", "RULE", "GO", "EITHER", "OR", "BOTH", "AND", "REPEAT", "ONCE",
    "MANY", "LOOK", "TRY", "HAS", "NOT", "STRING", "SET", "RANGE", "CAT", "TAG",
    "MARK", "REPORT", "DROP", "ACT"
};

// Structure holding current parse state.
struct state {
    char *code; int pc;
    ushort calls[1024]; int top;
    input *points[1024]; int point;
    int fails[1024], failTop;
    int marks[1024], markTop, markSize;
};
typedef struct state state;

// Call a given rule, pushing the return address on the call stack.
static inline void CALL(state *s, int x) { s->calls[s->top++] = x; s->pc = x; }

// Return from a call.
static inline void RETURN(state *s) { s->pc = s->calls[--s->top]; }

// Push the current input position on the backtracking stack.
static inline void PUSH(state *s, input *in) { s->points[s->point++] = in; }

// Pop an input position.
static inline int POP(state *s) { return s->points[--s->point]; }

// Read a 1-byte operand.
static inline int ARG1(state *s) { return s->code[s->pc++]; }

// Read a 2-byte operand.
static inline int ARG2(state *s) { return (ARG1(s)<<8) + ARG1(s); }

static inline void doStop(state *s) {

}

// x / y    EITHER &x OR &y    save input position, call x, return to OR
static inline void doEither(state *s) {
    PUSH(s, s->in);
    CALL(s, ARG2(s));
}

// x / y    check progress, return x or continue with y
static inline void doOr(state *s) {
    POP(s);
    if (s->ok) RETURN(s);
}

// x y    BOTH &x AND y      call x, returning to AND
static inline void doBoth(state *s) {
    CALL(ARG2(s));
}

// x y    if x succeeded, continue with y
static inline void doAnd(state *s) {
    if (! ok) RETURN(s);
}

// x? or x*    REPEAT ONCE/MANY x    call x, return to ONCE or MANY
static inline void doRepeat(state *s) {
    PUSH(s, s->in);
    CALL(s, s->pc + 1);
}

// x?    check success or soft failure of x
static inline void doOnce(state *s) {
    POP(s);
    s->ok = true;
    RETURN(s);
}

// x*    ... MANY x     check success of x, and do a repeat
static inline void doMany(state *s) {
    POP(s);
    if (ok) {
        pc = pc - 1;
        PUSH(s, s->in);
        CALL(s, s->pc + 1);
    }
    else {
        s->ok = true;
        RETURN(s);
    }
}

// [x] or x& or x!    LOOK TRY/HAS/NOT x    prepare to lookahead
// Save the input position, save the high water mark, set it high to
// avoid error reporting during lookahead, and call x.
static inline void doLook(state *s) {
    PUSH(s, s->in);
    PUSH(s->mark); // hwm stack
    PUSH(s->top); // backtrack stack
    mark = INT_MAX;
    CALL(pc + 1);
}

// 'abc'    match against next character in input
// Only ascii characters supported.
static inline void doSet(state *s) {
    int length = ARG1(s);
    ok = false;
    for (int i=0; i<length; i++) {
        if (input[in] == code[pc+i]) { ok = true; break; }
    }
    if (ok) {
        in += 1;
        RETURN(s);
    }
    else {
        if (mark < in) mark = in;
        BACKTRACK(s);
    }
}


// Carry out parsing:
// code:   the bytecode generated from a grammar by Pecan (or a rule within it)
// in:     the UTF-8 text, or tokens (with 0 sentinel);
// a:      the object to cooperate with;
// report: ...
report *parse(byte *code, input *in, actor *a) {
    state *s = malloc(sizeof(state));
    s->calls = malloc(1024*sizeof(ushort));
    s->top = 0;
    s->pc = 0;
    while (true) switch (code[s->pc++]) {
        case STOP: doStop(s); break;
        case RULE: doRule(s); break;
        case GO: doGo(s); break;
        case EITHER: doEither(s); break;
        case OR: doOr(s); break;
        case BOTH: doBoth(s); break;
        case AND: doAnd(s); break;
        case REPEAT: doRepeat(s); break;
        case ONCE: doOnce(s); break;
        case MANY: doMany(s); break;
        case LOOK: doLook(s); break;
        case TRY: doTry(s); break;
        case HAS: doHas(s); break;
        case NOT: doNot(s); break;
        case STRING: doString(s); break;
        case SET: doSet(s); break;
        case RANGE: doRange(s); break;
        case CAT: doCat(s); break;
        case TAG: doTag(s); break;
        case MARK: doMark(s); break;
        case REPORT: doReport(s); break;
        case DROP: doDrop(s); break;
        case ACT: doAct(s); break;
        default: doBug(s); break;
    }
}
