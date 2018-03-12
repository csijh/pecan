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
    int fails[1024], failTop;
    int marks[1024], markTop, markSize;
};
typedef struct state state;

// Push a return address onto the call stack.
static inline void PUSH(state *s, int x) { s->calls[s->top++] = x; }

// Pop a return address from the call stack.
static inline int POP(state *s) { return s->calls[--s->top]; }

// Call a given rule.
static inline void CALL(state *s, int x) { PUSH(s, s->pc); s->pc = x; }

// Return from a call.
static inline void RETURN(state *s) { s->pc = POP(s); }

// Read a 2-byte operand.
static inline int ARG2(state *s) {
    return (s->code[s->pc]<<8) + s->code[s->pc+1];
}

static inline void doStop(state *s) {

}

// x / y    EITHER &x OR &y    save in, call x, return to OR
static inline void doEither(state *s) {
    int arg = ARG2(s);
    s->pc += 2;
    PUSH(s->in);
    CALL(s, arg);
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
