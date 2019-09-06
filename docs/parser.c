/* Pecan 1.0 bytecode interpreter in C. Free and open source. See licence.txt.

Compile with option -DTEST (and maybe option -DTRACE) to carry out self-tests.
*/
#include "parser.h"
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

// TODO: tag and cat ops.

static char *opNames[] = {
    [STOP]="STOP", [OR]="OR", [AND]="AND", [MAYBE]="MAYBE", [ONE]="ONE",
    [MANY]="MANY", [DO]="DO", [LOOK]="LOOK", [TRY]="TRY", [HAS]="HAS",
    [NOT]="NOT", [DROP]="DROP", [STRING1]="STRING1", [LOW1]="LOW1",
    [HIGH1]="HIGH1", [LESS1]="LESS1", [SET1]="SET1", [START]="START", [GO]="GO",
    [BACK]="BACK", [EITHER]="EITHER", [BOTH]="BOTH", [STRING]="STRING",
    [LOW]="LOW", [HIGH]="HIGH", [LESS]="LESS", [SET]="SET", [ACT]="ACT",
    [MARK]="MARK", [CAT]="CAT", [TAG]="TAG", [GOL]="GOL", [BACKL]="BACKL",
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
// tag:     an external function to call to get the tag of the i'th token.
// arg:     an argument to pass to the act or tag function.
// look:    the nesting depth inside lookahead constructs.
// top:     the number of items on the call stack.
// out:     the number of delayed actions stored in the actions array.
// stack:   the call stack, i.e. return addresses and saved in/out values.
// actions: the delayed action codes and input positions.
struct parser {
    byte *code, *input;
    void **tokens;
    int pc, start, in, marked;
    uint64_t markers;
    bool ok;
    doAct *act;
    doTag *tag;
    void *arg;
    int look, top, out;
    int stack[1000], actions[1000];
};
typedef struct parser parser;

// Initialize the parsing state.
static void new(
    parser *s, byte c[], byte i[], void **t, doTag *tag, doAct *act, void *arg)
{
    s->code = c;
    s->input = i;
    s->tokens = t;
    s->pc = s->start = s->in = s->marked = 0;
    s->markers = 0L;
    s->ok = false;
    s->act = act;
    s->arg = arg;
    s->tag = tag;
    s->look = s->top = s->out = 0;
}

// Carry out any delayed actions.
static inline void doActs(parser *s) {
    for (int i = 0; i < s->out; i = i + 2) {
        int a = s->actions[i];
        int oldIn = s->actions[i+1];
        if (a != -1) s->act(s->arg, a, oldIn - s->start, &s->input[s->start]);
        s->start = oldIn;
    }
    s->out = 0;
}

// Find the line/column and start/end of line containing an input position.
static void findLine(parser *s, result *r, int p) {
    r->line = 1;
    r->start = 0;
    for (int i = 0; ; i++) {
        byte b = s->input[i];
        if (b == '\0') { r->end = i; break; }
        if (b != '\n') continue;
        r->line++;
        if (i + 1 <= p) r->start = i + 1;
        else { r->end = i; break; }
    }
    r->column = p - r->start;
}

// {id = x}  =  START, nx, {x}, STOP
// Call {x} returning to STOP.
static inline void doSTART(parser *s, int arg) {
    s->stack[s->top++] = s->pc + arg;
}

// {id = x}  =  ... STOP
// Carry out any outstanding delayed actions. Return NULL for success, or an
// error report containing a bitmap of markers.
static inline void doSTOP(parser *s, result *r) {
    if (s->ok && s->out > 0) doActs(s);
    if (s->ok || s->in > s->marked) s->markers = 0L;
    r->input = s->input;
    r->ok = s->ok;
    r->at = s->in;
    r->markers = s->markers;
    if (! s->ok) findLine(s, r, s->in);
}

// {id}  =  GO, n   or   BACK, n
// Skip forwards or backwards in the code, to tail-call a remote rule.
static inline void doGO(parser *s, int arg) {
    s->pc = s->pc + arg;
}

// {x / y}  =  EITHER, nx, {x}, OR, {y}
// Save in/out, call x, returning to OR.
static inline void doEITHER(parser *s, int arg) {
    s->stack[s->top++] = s->in;
    s->stack[s->top++] = s->out;
    s->stack[s->top++] = s->pc + arg;
}

// {x / y}  =  EITHER, nx, {x}, OR, {y}
// After x, check success and progress, return or continue with y.
static inline void doOR(parser *s) {
    int saveOut = s->stack[--s->top];
    int saveIn = s->stack[--s->top];
    if (s->ok || s->in > saveIn) s->pc = s->stack[--s->top];
    else s->out = saveOut;
}

// {x y}  =  BOTH, nx, {x}, AND, {y}
// Call x, returning to AND.
static inline void doBOTH(parser *s, int arg) {
    s->stack[s->top++] = s->pc + arg;
}

// {x y}  =  ...AND, {y}
// After x, check success, continue with y or return.
static inline void doAND(parser *s) {
    if (! s->ok) s->pc = s->stack[--s->top];
}

// {x?}  =  MAYBE, ONE, {x},   and similarly for x*, x+
// Save in/out and call x, returning to ONE or MANY.
static inline void doMAYBE(parser *s) {
    s->stack[s->top++] = s->in;
    s->stack[s->top++] = s->out;
    s->stack[s->top++] = s->pc;
    s->pc++;
}

// {x?}  =  MAYBE, ONE, {x}
// After x, check success or no progress and return.
static inline void doONE(parser *s) {
    int saveOut = s->stack[--s->top];
    int saveIn = s->stack[--s->top];
    if (! s->ok && s->in == saveIn) {
        s->out = saveOut;
        s->ok = true;
    }
    s->pc = s->stack[--s->top];
}

// {x*}  =  MAYBE, MANY, {x}
// After x, check success and re-try x or return.
static inline void doMANY(parser *s) {
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

// {x+}  =  DO, AND, MAYBE, MANY, {x}
// Call x, returning to AND.
static inline void doDO(parser *s) {
    s->stack[s->top++] = s->pc;
    s->pc = s->pc + 3;
}

// {[x]}  =  LOOK, TRY, x,   and similarly for x& and x!
// Save in/out and call x as a lookahead, returning to TRY or HAS or NOT.
static inline void doLOOK(parser *s) {
    s->stack[s->top++] = s->in;
    s->stack[s->top++] = s->out;
    s->look++;
    s->stack[s->top++] = s->pc;
    s->pc++;
}

// {[x]}  =  LOOK, TRY, x
// Succeed and perform actions, or fail and discard them and backtrack.
static inline void doTRY(parser *s) {
    int saveOut = s->stack[--s->top];
    int saveIn = s->stack[--s->top];
    s->look--;
    if (s->ok && s->look == 0 && s->out > 0) doActs(s);
    else if (! s->ok) {
        s->out = saveOut;
        s->in = saveIn;
    }
    s->pc = s->stack[--s->top];
}

// {x&}  =  LOOK, HAS, x
// After x, backtrack and return.
static inline void doHAS(parser *s) {
    s->out = s->stack[--s->top];
    s->in = s->stack[--s->top];
    s->look--;
    s->pc = s->stack[--s->top];
}

// {x!}  =  LOOK, NOT, x
// After x, backtrack, invert the result, and return.
static inline void doNOT(parser *s) {
    s->out = s->stack[--s->top];
    s->in = s->stack[--s->top];
    s->look--;
    s->ok = ! s->ok;
    s->pc = s->stack[--s->top];
}

// {@}  =  DROP
// Delay the drop, using action code -1.
static inline void doDROP(parser *s) {
    s->actions[s->out++] = -1;
    s->actions[s->out++] = s->in;
    s->ok = true;
    s->pc = s->stack[--s->top];
}

// {@2add}  =  ACT, add
// Delay the action and return success.
static inline void doACT(parser *s, int arg) {
    s->actions[s->out++] = arg;
    s->actions[s->out++] = s->in;
    s->ok = true;
    s->pc = s->stack[--s->top];
}

// {#item}  =  MARK, item
// Record an error marker. Assume 0 <= arg <= 62.
static inline void doMARK(parser *s, int arg) {
    if (s->look == 0) {
        if (s->in > s->marked) {
            s->marked = s->in;
            s->markers = 0L;
        }
        s->markers = s->markers | (1L << arg);
    }
    s->ok = true;
    s->pc = s->stack[--s->top];
}

// {"abc"}  =  STRING, 3, 'a', 'b', 'c'
// Match string and return success or failure.
static inline void doSTRING(parser *s, int arg) {
    s->ok = true;
    for (int i = 0; i < arg && s->ok; i++) {
        byte b = s->input[s->in + i];
        if (b == '\0' || b != s->code[s->pc + i]) s->ok = false;
    }
    if (s->ok) {
        s->in = s->in + arg;
        if (s->look == 0 && s->out > 0) doActs(s);
    }
    s->pc = s->stack[--s->top];
}

// {'a..z'}  =  LOW, n, 'a', HIGH, n, 'z'
// Check >= 'a', continue with HIGH or return failure
static inline void doLOW(parser *s, int arg) {
    s->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = s->input[s->in + i];
        if (b == '\0' || b < s->code[s->pc + i]) s->ok = false;
    }
    if (s->ok) s->pc = s->pc + arg;
    else {
        s->pc = s->stack[--s->top];
    }
}

// {'a..z'}  =  ...HIGH, n, 'z'
// Check <= 'z', return success or failure
static inline void doHIGH(parser *s, int arg) {
    s->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = s->input[s->in + i];
        if (b == '\0' || b > s->code[s->pc + i]) s->ok = false;
    }
    if (s->ok) {
        s->pc = s->pc + arg;
        s->in = s->in + arg;
        if (s->look == 0 && s->out > 0) doActs(s);
    }
    s->pc = s->stack[--s->top];
}

// {<abc>}  =  LESS, 3, 'a', 'b', 'c'
// Check if input < "abc", return.
static inline void doLESS(parser *s, int arg) {
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

// {'abc'}  =  SET, 3, 'a', 'b', 'c'
// Check for one of the characters in a set, and return.
static inline void doSET(parser *s, int arg) {
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
        if (s->look == 0 && s->out > 0) doActs(s);
        s->in = s->in + n;
    }
    s->pc = s->stack[--s->top];
}

// {%t} == TAG, t
// Check if tag of next token is t and return.
static inline void doTAG(parser *s, int arg) {
}

static inline void getOpArg(parser *s, int *op, int *arg) {
#ifdef TRACE
    int pc0 = s->pc;
#endif
    *op = s->code[s->pc++];
    *arg = 1;
    if (*op >= OP2) {
        *arg = s->code[s->pc++];
        *arg = (*arg << 8) | s->code[s->pc++];
    }
    else if (*op >= OP1) {
        *arg = s->code[s->pc++];
    }
#ifdef TRACE
    if (*op >= START) printf("%d: %s %d\n", pc0, opNames[*op], *arg);
    else printf("%d: %s\n", pc0, opNames[*op]);
#endif
}

static void execute(parser *s, result *r) {
    while (true) {
        int op, arg;
        getOpArg(s, &op, &arg);
        switch (op) {
            case START: doSTART(s, arg); break;
            case STOP: doSTOP(s, r); return;
            case GO: case GOL: doGO(s, arg); break;
            case BACK: case BACKL: doGO(s, -arg); break;
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
            case STRING: case STRING1: case SET1: doSTRING(s, arg); break;
            case LOW: case LOW1: doLOW(s, arg); break;
            case HIGH: case HIGH1: doHIGH(s, arg); break;
            case LESS: case LESS1: doLESS(s, arg); break;
            case SET: doSET(s, arg); break;
            case TAG: doTAG(s, arg); break;
            default: printf("Bad op %d\n", op); exit(1);
        }
    }
}

// The parse function makes calls to inline functions, and the parser structure
// is allocated locally, so it should be as efficient as a monolithic function,
// with local variables, containing a giant switch statement.
void parseC(byte code[], byte in[], doAct *f, void *x, result *r) {
    parser pData;
    parser *s = &pData;
    new(s, code, in, NULL, NULL, f, x);
    execute(s, r);
}

void parseT(byte code[], void *in[], doTag *g, doAct *f, void *x, result *r) {
    parser pData;
    parser *s = &pData;
    new(s, code, NULL, in, g, f, x);
    execute(s, r);
}

static void reportLine(result *r) {
    fprintf(stderr, "%.*s\n", r->end - r->start, r->input + r->start);
}

static void reportColumn(result *r) {
    for (int i = 0; i < r->column; i++) fprintf(stderr, " ");
    fprintf(stderr, "^\n");
}

void report(result *r, char *s, char *s1, char *s2, char *s3, char *names[]) {
    if (r->markers == 0L) { fprintf(stderr, "%s", s); }
    else {
        fprintf(stderr, "%s", s1);
        bool first = true;
        for (int i = 0; i < 64; i++) {
            if ((r->markers & (1L << i)) == 0) continue;
            if (! first) fprintf(stderr, "%s", s2);
            first = false;
            fprintf(stderr, "%s", names[i]);
        }
        fprintf(stderr, "%s", s3);
    }
    reportLine(r);
    reportColumn(r);
}

#ifdef TEST

// The examples in the calculator tutorial are used as self-tests.

#include <assert.h>
#include <string.h>
enum action { number = 0, add = 1, subtract = 2, multiply = 3, divide = 4 };
enum marker { digit, operator, bracket, newline, space };
struct state { int n; char output[100]; };
typedef struct state state;

// Store actions symbolically.
static void act(void *vs, int a, int n, char matched[n]) {
    state *s = vs;
    s->output[s->n++] = "#+-*/"[a];
    if (a == number) for (int i = 0; i < n; i++) {
        s->output[s->n++] = matched[i];
    }
}

// digit = '0..9' @number   (Calculator step 1)
static byte calc1[] = {
    START, 9, BOTH, 4, LOW1, 48, HIGH1, 57, AND, ACT, number, STOP
};
// number = ('0..9')+ @number   (Calculator step 2)
static byte calc2[] = {
    START, 13, BOTH, 8, DO, AND, MAYBE, MANY, LOW1, 48, HIGH1, 57, AND, ACT,
    number, STOP
};
// sum = number / number '+' number @2add   (Calculator step 3)
// number = ('0..9')+ @number
static byte calc3[] = {
    START, 22, EITHER, 2, GO, 21, OR, BOTH, 2, GO, 16, AND, BOTH, 2,
    STRING1, 43, AND, BOTH, 2, GO, 6, AND, ACT, add, STOP, START, 13, BOTH,
    8, DO, AND, MAYBE, MANY, LOW1, 48, HIGH1, 57, AND, ACT, number, STOP
};
// sum = number '+' number @2add / number   (Calculator step 4)
// number = ('0..9')+ @number
static byte calc4[] = {
    START, 22, EITHER, 17, BOTH, 2, GO, 19, AND, BOTH, 2, STRING1, 43, AND,
    BOTH, 2, GO, 9, AND, ACT, add, OR, GO, 3, STOP, START, 13, BOTH, 8,
    DO, AND, MAYBE, MANY, LOW1, 48, HIGH1, 57, AND, ACT, number, STOP
};
// sum = [number '+'] number @2add / number   (Calculator step 5)
// number = ('0..9')+ @number
static byte calc5[] = {
    START, 24, EITHER, 19, BOTH, 9, LOOK, TRY, BOTH, 2, GO, 17, AND,
    STRING1, 43, AND, BOTH, 2, GO, 9, AND, ACT, add, OR, GO, 3, STOP,
    START, 13, BOTH, 8, DO, AND, MAYBE, MANY, LOW1, 48, HIGH1, 57, AND, ACT,
    number, STOP
};
// sum = number ('+' number @2add)?   (Calculator step 6)
// number = ('0..9')+ @number
static byte calc6[] = {
    START, 19, BOTH, 2, GO, 18, AND, MAYBE, ONE, BOTH, 2, STRING1, 43, AND,
    BOTH, 2, GO, 6, AND, ACT, add, STOP, START, 13, BOTH, 8, DO, AND,
    MAYBE, MANY, LOW1, 48, HIGH1, 57, AND, ACT, number, STOP
};
// sum = number ('+' @ number @2add)?   (Calculator step 7)
// number = ('0..9')+ @number
static byte calc7[] = {
    START, 23, BOTH, 2, GO, 22, AND, MAYBE, ONE, BOTH, 2, SET1, 43, AND,
    BOTH, 1, DROP, AND, BOTH, 2, GO, 6, AND, ACT, add, STOP, START, 13, BOTH,
    8, DO, AND, MAYBE, MANY, LOW1, 48, HIGH1, 57, AND, ACT, number, STOP
};
// sum = number ('+' @ number @2add)? end   (Calculator step 8)
// number = ('0..9')+ @number
// end = 13? 10 @
static byte calc8[] = {
    START, 28, BOTH, 2, GO, 27, AND, BOTH, 18, MAYBE, ONE, BOTH, 2, STRING1,
    43, AND, BOTH, 1, DROP, AND, BOTH, 2, GO, 9, AND, ACT, add, AND, GO, 19,
    STOP, START, 13, BOTH, 8, DO, AND, MAYBE, MANY, LOW1, 48, HIGH1, 57, AND,
    ACT, number, STOP, START, 13, BOTH, 4, MAYBE, ONE, STRING1, 13, AND,
    BOTH, 2, STRING1, 10, AND, DROP, STOP
};
// sum = number ('+' @ number @2add)* end   (Calculator step 9)
// number = ('0..9')+ @number
// end = 13? 10 @
static byte calc9[] = {
    START, 28, BOTH, 2, GO, 27, AND, BOTH, 18, MAYBE, MANY, BOTH, 2,
    STRING1, 43, AND, BOTH, 1, DROP, AND, BOTH, 2, GO, 9, AND, ACT, add, AND,
    GO, 19, STOP, START, 13, BOTH, 8, DO, AND, MAYBE, MANY, LOW1, 48, HIGH1,
    57, AND, ACT, number, STOP, START, 13, BOTH, 4, MAYBE, ONE, STRING1, 13,
    AND, BOTH, 2, STRING1, 10, AND, DROP, STOP
};
// sum = number ('+' @ number @2add / '-' @ number @2subtract)* end
// number = ('0..9')+ @number
// end = 13? 10 @                         (Calculator step 10)
static byte calc10[] = {
    START, 47, BOTH, 2, GO, 46, AND, BOTH, 37, MAYBE, MANY, EITHER, 16, BOTH, 2,
    SET1, 43, AND, BOTH, 1, DROP, AND, BOTH, 2, GO, 26, AND, ACT, add, OR, BOTH,
    2, SET1, 45, AND, BOTH, 1, DROP, AND, BOTH, 2, GO, 9, AND, ACT, subtract,
    AND, GO, 19, STOP, START, 13, BOTH, 8, DO, AND, MAYBE, MANY, LOW1, 48,
    HIGH1, 57, AND, ACT, number, STOP, START, 13, BOTH, 4, MAYBE, ONE, STRING1,
    13, AND, BOTH, 2, STRING1, 10, AND, DROP, STOP
};
// sum = number ('+' @ number @2add / '-' @ number @2subtract)* end
// number = (#digit '0..9')+ @number
// end = #newline 13? 10 @                (Calculator step 11)
static byte calc11[] = {
    START, 47, BOTH, 2, GO, 46, AND, BOTH, 37, MAYBE, MANY, EITHER, 16, BOTH, 2,
    SET1, 43, AND, BOTH, 1, DROP, AND, BOTH, 2, GO, 26, AND, ACT, add, OR, BOTH,
    2, SET1, 45, AND, BOTH, 1, DROP, AND, BOTH, 2, GO, 9, AND, ACT, subtract,
    AND, GO, 24, STOP, START, 18, BOTH, 13, DO, AND, MAYBE, MANY, BOTH, 2,
    MARK, digit, AND, LOW1, 48, HIGH1, 57, AND, ACT, number, STOP, START, 18,
    BOTH, 2, MARK, newline, AND, BOTH, 4, MAYBE, ONE, STRING1, 13, AND, BOTH,
    2, STRING1, 10, AND, DROP, STOP
};
// sum = number (plus number @2add / minus number @2subtract)* end
// number = digit+ @number
// plus = #operator '+' @
// minus = #operator '-' @
// digit = #digit '0..9'
// end = #newline 13? 10 @
static byte calc12[] = {
    START, 39, BOTH, 2, GO, 38, AND, BOTH, 29, MAYBE, MANY, EITHER, 12, BOTH, 2,
    GO, 41, AND, BOTH, 2, GO, 22, AND, ACT, add, OR, BOTH, 2, GO, 42, AND, BOTH,
    2, GO, 9, AND, ACT, subtract, AND, GO, 57, STOP, START, 11, BOTH, 6, DO,
    AND, MAYBE, MANY, GO, 34, AND, ACT, number, STOP, START, 11, BOTH, 2, MARK,
    operator, AND, BOTH, 2, SET1, 43, AND, DROP, STOP, START, 11, BOTH, 2, MARK,
    operator, AND, BOTH, 2, SET1, 45, AND, DROP, STOP, START, 9, BOTH, 2, MARK,
    digit, AND, LOW1, 48, HIGH1, 57, STOP, START, 18, BOTH, 2, MARK, newline,
    AND, BOTH, 4, MAYBE, ONE, STRING1, 13, AND, BOTH, 2, STRING1, 10, AND,
    DROP, STOP
};
// sum = term (plus term @2add / minus term @2subtract)* end
// term = number (times number @2multiply / over number @2divide)*
// number = digit+ @number
// plus = #operator '+' @
// minus = #operator '-' @
// times = #operator '*' @
// over = #operator '/' @
// digit = #digit '0..9'
// end = #newline 13? 10 @
static byte calc13[] = {
    START, 39, BOTH, 2, GO, 38, AND, BOTH, 29, MAYBE, MANY, EITHER, 12, BOTH, 2,
    GO, 78, AND, BOTH, 2, GO, 22, AND, ACT, add, OR, BOTH, 2, GO, 79, AND, BOTH,
    2, GO, 9, AND, ACT, subtract, AND, GO, 122, STOP, START, 34, BOTH, 2, GO,
    33, AND, MAYBE, MANY, EITHER, 12, BOTH, 2, GO, 66, AND, BOTH, 2, GO, 19,
    AND, ACT, multiply, OR, BOTH, 2, GO, 67, AND, BOTH, 2, GO, 6, AND, ACT,
    divide, STOP, START, 11, BOTH, 6, DO, AND, MAYBE, MANY, GO, 62, AND, ACT,
    number, STOP, START, 11, BOTH, 2, MARK, operator, AND, BOTH, 2, SET1, 43,
    AND, DROP, STOP, START, 11, BOTH, 2, MARK, operator, AND, BOTH, 2, SET1, 45,
    AND, DROP, STOP, START, 11, BOTH, 2, MARK, operator, AND, BOTH, 2, SET1, 42,
    AND, DROP, STOP, START, 11, BOTH, 2, MARK, operator, AND, BOTH, 2, SET1, 47,
    AND, DROP, STOP, START, 9, BOTH, 2, MARK, digit, AND, LOW1, 48, HIGH1, 57,
    STOP, START, 18, BOTH, 2, MARK, newline, AND, BOTH, 4, MAYBE, ONE, STRING1,
    13, AND, BOTH, 2, STRING1, 10, AND, DROP, STOP
};
// sum = term (plus term @2add / minus term @2subtract)* end
// term = atom (times atom @2multiply / over atom @2divide)*
// atom = number / open sum close
// number = digit+ @number
// plus = #operator '+' @
// minus = #operator '-' @
// times = #operator '*' @
// open = #bracket '(' @
// over = #operator '/' @
// close = #bracket ')' @
// digit = #digit '0..9'
// end = #newline 13? 10 @
static byte calc14[] = {
    START, 39, BOTH, 2, GO, 38, AND, BOTH, 29, MAYBE, MANY, EITHER, 12, BOTH, 2,
    GO, 98, AND, BOTH, 2, GO, 22, AND, ACT, add, OR, BOTH, 2, GO, 99, AND, BOTH,
    2, GO, 9, AND, ACT, subtract, AND, GO, 170, STOP, START, 34, BOTH, 2, GO,
    33, AND, MAYBE, MANY, EITHER, 12, BOTH, 2, GO, 86, AND, BOTH, 2, GO, 19,
    AND, ACT, multiply, OR, BOTH, 2, GO, 87, AND, BOTH, 2, GO, 6, AND, ACT,
    divide, STOP, START, 17, EITHER, 2, GO, 16, OR, BOTH, 2, GO, 81, AND, BOTH,
    2, BACK, 93, AND, GO, 87, STOP, START, 11, BOTH, 6, DO, AND, MAYBE, MANY,
    GO, 90, AND, ACT, number, STOP, START, 11, BOTH, 2, MARK, operator, AND,
    BOTH, 2, SET1, 43, AND, DROP, STOP, START, 11, BOTH, 2, MARK, operator, AND,
    BOTH, 2, SET1, 45, AND, DROP, STOP, START, 11, BOTH, 2, MARK, operator, AND,
    BOTH, 2, SET1, 42, AND, DROP, STOP, START, 11, BOTH, 2, MARK, operator, AND,
    BOTH, 2, SET1, 47, AND, DROP, STOP, START, 11, BOTH, 2, MARK, bracket, AND,
    BOTH, 2, SET1, 40, AND, DROP, STOP, START, 11, BOTH, 2, MARK, bracket, AND,
    BOTH, 2, SET1, 41, AND, DROP, STOP, START, 9, BOTH, 2, MARK, digit, AND,
    LOW1, 48, HIGH1, 57, STOP, START, 18, BOTH, 2, MARK, newline, AND, BOTH, 4,
    MAYBE, ONE, STRING1, 13, AND, BOTH, 2, STRING1, 10, AND, DROP, STOP
};
// sum = expression end
// expression = term (plus term @2add / minus term @2subtract)*
// term = atom (times atom @2multiply / over atom @2divide)*
// atom = number / open expression close
// number = digit+ @number
// plus = #operator '+' @
// minus = #operator '-' @
// times = #operator '*' @
// over = #operator '/' @
// open = #bracket '(' @
// close = #bracket ')' @
// digit = #digit '0..9'
// end = #newline 13? 10 @
static byte calc15[] = {
    START, 7, BOTH, 2, GO, 6, AND, GO, 207, STOP, START, 34, BOTH, 2, GO, 33,
    AND, MAYBE, MANY, EITHER, 12, BOTH, 2, GO, 95, AND, BOTH, 2, GO, 19, AND,
    ACT, add, OR, BOTH, 2, GO, 96, AND, BOTH, 2, GO, 6, AND, ACT, subtract,
    STOP, START, 34, BOTH, 2, GO, 33, AND, MAYBE, MANY, EITHER, 12, BOTH, 2, GO,
    86, AND, BOTH, 2, GO, 19, AND, ACT, multiply, OR, BOTH, 2, GO, 87, AND,
    BOTH, 2, GO, 6, AND, ACT, divide, STOP, START, 17, EITHER, 2, GO, 16, OR,
    BOTH, 2, GO, 81, AND, BOTH, 2, BACK, 88, AND, GO, 87, STOP, START, 11, BOTH,
    6, DO, AND, MAYBE, MANY, GO, 90, AND, ACT, number, STOP, START, 11, BOTH, 2,
    MARK, operator, AND, BOTH, 2, SET1, 43, AND, DROP, STOP, START, 11, BOTH, 2,
    MARK, operator, AND, BOTH, 2, SET1, 45, AND, DROP, STOP, START, 11, BOTH, 2,
    MARK, operator, AND, BOTH, 2, SET1, 42, AND, DROP, STOP, START, 11, BOTH, 2,
    MARK, operator, AND, BOTH, 2, SET1, 47, AND, DROP, STOP, START, 11, BOTH, 2,
    MARK, bracket, AND, BOTH, 2, SET1, 40, AND, DROP, STOP, START, 11, BOTH, 2,
    MARK, bracket, AND, BOTH, 2, SET1, 41, AND, DROP, STOP, START, 9, BOTH, 2,
    MARK, digit, AND, LOW1, 48, HIGH1, 57, STOP, START, 18, BOTH, 2, MARK,
    newline, AND, BOTH, 4, MAYBE, ONE, STRING1, 13, AND, BOTH, 2, STRING1, 10,
    AND, DROP, STOP
};
// sum = gap expression end
// expression = term (plus term @2add / minus term @2subtract)*
// term = atom (times atom @2multiply / over atom @2divide)*
// atom = number / open expression close
// number = digit+ @number gap
// plus = #operator '+' gap
// minus = #operator '-' gap
// times = #operator '*' gap
// over = #operator '/' gap
// open = #bracket '(' gap
// close = #bracket ')' gap
// digit = #digit '0..9'
// gap = (#space ' ')* @
// end = #newline 13? 10 @
static byte calc16[] = {
    START, 12, BOTH, 2, GO, 226, AND, BOTH, 2, GO, 6, AND, GO, 234, STOP, START,
    34, BOTH, 2, GO, 33, AND, MAYBE, MANY, EITHER, 12, BOTH, 2, GO, 100, AND,
    BOTH, 2, GO, 19, AND, ACT, add, OR, BOTH, 2, GO, 102, AND, BOTH, 2, GO, 6,
    AND, ACT, subtract, STOP, START, 34, BOTH, 2, GO, 33, AND, MAYBE, MANY,
    EITHER, 12, BOTH, 2, GO, 93, AND, BOTH, 2, GO, 19, AND, ACT, multiply, OR,
    BOTH, 2, GO, 95, AND, BOTH, 2, GO, 6, AND, ACT, divide, STOP, START, 17,
    EITHER, 2, GO, 16, OR, BOTH, 2, GO, 90, AND, BOTH, 2, BACK, 88, AND, GO, 97,
    STOP, START, 16, BOTH, 6, DO, AND, MAYBE, MANY, GO, 101, AND, BOTH, 2, ACT,
    number, AND, GO, 105, STOP, START, 12, BOTH, 2, MARK, operator, AND, BOTH,
    2, SET1, 43, AND, GO, 90, STOP, START, 12, BOTH, 2, MARK, operator, AND,
    BOTH, 2, SET1, 45, AND, GO, 75, STOP, START, 12, BOTH, 2, MARK, operator,
    AND, BOTH, 2, SET1, 42, AND, GO, 60, STOP, START, 12, BOTH, 2, MARK,
    operator, AND, BOTH, 2, SET1, 47, AND, GO, 45, STOP, START, 12, BOTH, 2,
    MARK, bracket, AND, BOTH, 2, SET1, 40, AND, GO, 30, STOP, START, 12, BOTH,
    2, MARK, bracket, AND, BOTH, 2, SET1, 41, AND, GO, 15, STOP, START, 9, BOTH,
    2, MARK, digit, AND, LOW1, 48, HIGH1, 57, STOP, START, 13, BOTH, 9, MAYBE,
    MANY, BOTH, 2, MARK, space, AND, SET1, 32, AND, DROP, STOP, START, 18, BOTH,
    2, MARK, newline, AND, BOTH, 4, MAYBE, ONE, STRING1, 13, AND, BOTH, 2,
    STRING1, 10, AND, DROP, STOP
};
// sum = gap expression end
// expression = term (plus term @2add / minus term @2subtract)*
// term = atom (times atom @2multiply / over atom @2divide)*
// atom = number / open expression close
// number = digit+ @number gap
// plus = #operator '+' gap
// minus = #operator '-' gap
// times = #operator '*' gap
// over = #operator '/' gap
// open = #bracket '(' gap
// close = #bracket ')' gap
// digit = #digit '0..9'
// gap = (' ')* @
// end = #newline 13? 10 @
static byte calc17[] = {
    START, 12, BOTH, 2, GO, 226, AND, BOTH, 2, GO, 6, AND, GO, 229, STOP, START,
    34, BOTH, 2, GO, 33, AND, MAYBE, MANY, EITHER, 12, BOTH, 2, GO, 100, AND,
    BOTH, 2, GO, 19, AND, ACT, add, OR, BOTH, 2, GO, 102, AND, BOTH, 2, GO, 6,
    AND, ACT, subtract, STOP, START, 34, BOTH, 2, GO, 33, AND, MAYBE, MANY,
    EITHER, 12, BOTH, 2, GO, 93, AND, BOTH, 2, GO, 19, AND, ACT, multiply, OR,
    BOTH, 2, GO, 95, AND, BOTH, 2, GO, 6, AND, ACT, divide, STOP, START, 17,
    EITHER, 2, GO, 16, OR, BOTH, 2, GO, 90, AND, BOTH, 2, BACK, 88, AND, GO, 97,
    STOP, START, 16, BOTH, 6, DO, AND, MAYBE, MANY, GO, 101, AND, BOTH, 2, ACT,
    number, AND, GO, 105, STOP, START, 12, BOTH, 2, MARK, operator, AND, BOTH,
    2, SET1, 43, AND, GO, 90, STOP, START, 12, BOTH, 2, MARK, operator, AND,
    BOTH, 2, SET1, 45, AND, GO, 75, STOP, START, 12, BOTH, 2, MARK, operator,
    AND, BOTH, 2, SET1, 42, AND, GO, 60, STOP, START, 12, BOTH, 2, MARK,
    operator, AND, BOTH, 2, SET1, 47, AND, GO, 45, STOP, START, 12, BOTH, 2,
    MARK, bracket, AND, BOTH, 2, SET1, 40, AND, GO, 30, STOP, START, 12, BOTH,
    2, MARK, bracket, AND, BOTH, 2, SET1, 41, AND, GO, 15, STOP, START, 9, BOTH,
    2, MARK, digit, AND, LOW1, 48, HIGH1, 57, STOP, START, 8, BOTH, 4, MAYBE,
    MANY, SET1, 32, AND, DROP, STOP, START, 18, BOTH, 2, MARK, newline, AND,
    BOTH, 4, MAYBE, ONE, STRING1, 13, AND, BOTH, 2, STRING1, 10, AND, DROP, STOP
};

static bool run(state *s, byte *code, char *input, char *output) {
    result rData;
    result *r = &rData;
    s->n = 0;
    parseC(code, input, act, s, r);
    s->output[s->n] = '\0';
    if (! r->ok) sprintf(&s->output[s->n], "E%d:%lx", r->at, r->markers);
    if (strcmp(s->output, output) != 0) printf("Output: %s\n", s->output);
    return strcmp(s->output, output) == 0;
}

int main() {
    setbuf(stdout, NULL);
    state sData;
    state *s = &sData;
    assert(run(s, calc1, "2", "#2"));
    assert(run(s, calc1, "x", "E0:0"));
    assert(run(s, calc2, "2", "#2"));
    assert(run(s, calc2, "42", "#42"));
    assert(run(s, calc2, "2x", "#2"));
    assert(run(s, calc3, "2", "#2"));
    assert(run(s, calc3, "42", "#42"));
    assert(run(s, calc3, "2x", "#2"));
    assert(run(s, calc4, "2", "E1:0"));
    assert(run(s, calc5, "2", "#2"));
    assert(run(s, calc5, "42", "#42"));
    assert(run(s, calc5, "2+40", "#2#+40+"));
    assert(run(s, calc6, "2", "#2"));
    assert(run(s, calc6, "42", "#42"));
    assert(run(s, calc6, "2+40", "#2#+40+"));
    assert(run(s, calc7, "2", "#2"));
    assert(run(s, calc7, "42", "#42"));
    assert(run(s, calc7, "2+40", "#2#40+"));
    assert(run(s, calc8, "2\n", "#2"));
    assert(run(s, calc8, "42\n", "#42"));
    assert(run(s, calc8, "2+40\n", "#2#40+"));
    assert(run(s, calc8, "2+40%\n", "#2E4:0"));
    assert(run(s, calc9, "2+10+12+18\n", "#2#10+#12+#18+"));
    assert(run(s, calc10, "2+10+12+18\n", "#2#10+#12+#18+"));
    assert(run(s, calc10, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc11, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc12, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc12, "2+\n", "#2E2:1"));
    assert(run(s, calc12, "2+40%\n", "#2E4:b"));
    assert(run(s, calc13, "5*8+12/6\n", "#5#8*#12#6/+"));
    assert(run(s, calc14, "2*(20+1)\n", "#2#20E7:b"));
    assert(run(s, calc15, "2*(20+1)\n", "#2#20#1+*"));
    assert(run(s, calc16, "2\n", "#2"));
    assert(run(s, calc16, "2+\n", "#2E2:15"));
    assert(run(s, calc17, "2\n", "#2"));
    assert(run(s, calc17, "42\n", "#42"));
    assert(run(s, calc17, "2+40\n", "#2#40+"));
    assert(run(s, calc17, "2+40%\n", "#2E4:b"));
    assert(run(s, calc17, "2+10+12+18\n", "#2#10+#12+#18+"));
    assert(run(s, calc17, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc17, "2+\n", "#2E2:5"));
    assert(run(s, calc17, "5*8+12/6\n", "#5#8*#12#6/+"));
    assert(run(s, calc17, "2*(20+1)\n", "#2#20#1+*"));
    assert(run(s, calc17, " 2 * ( 20 + 1 ) \n", "#2#20#1+*"));
    printf("Parser OK\n");
}

#endif
