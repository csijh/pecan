/* Pecan 1.0 bytecode interpreter. Free and open source. See licence.txt.

Compile with option -DTEST (and maybe option -DTRACE) to carry out self-tests.

The parse function is built up from calls to inline functions, and the parser
structure is allocated locally, so the efficiency should be the same as a single
function containing a monolithic switch, acting on local variables. */
#include "parser.h"
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

// TODO: tag and cat ops.
// TODO: backtrack tokens, or pass index in next function.

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
// next:    an external function to call to get the tag of the next token.
// arg:     an argument to pass to the act or next function.
// look:    the nesting depth inside lookahead constructs.
// top:     the number of items on the call stack.
// out:     the number of delayed actions stored in the actions array.
// stack:   the call stack, i.e. return addresses and saved in/out values.
// actions: the delayed action codes and input positions.
struct parser {
    byte *code, *input;
    int pc, start, in, marked;
    uint64_t markers;
    bool ok;
    doAct *act;
    doNext *next;
    void *arg;
    int look, top, out;
    int stack[1000], actions[1000];
};
typedef struct parser parser;

// Initialize the parsing state.
static void new(
    parser *s, byte c[], byte i[], doNext *next, doAct *act, void *arg)
{
    s->code = c;
    s->input = i;
    s->pc = s->start = s->in = s->marked = 0;
    s->markers = 0L;
    s->ok = false;
    s->act = act;
    s->arg = arg;
    s->next = next;
    s->look = s->top = s->out = 0;
}

// Find the n'th entry point in the bytecode.
static void entry(parser *s, int n) {
    for (int i = 0; i < n; i++) {
        int op = s->code[s->pc++];
        int arg = s->code[s->pc++];
        if (op != START) {
            printf("Error: badly structured bytecode\n");
            exit(1);
        }
        s->pc += arg + 1;
    }
}

// Carry out any delayed actions.
static inline void doActs(parser *s) {
    for (int i = 0; i < s->out; i = i + 2) {
        int a = s->actions[i];
        int oldIn = s->actions[i+1];
        s->act(s->arg, a, &s->input[s->start], oldIn - s->start);
        s->start = oldIn;
    }
    s->out = 0;
}

// Find the line/column and start/end of line containing an input position.
static void findLine(parser *s, err *e, int p) {
    e->line = 1;
    e->start = 0;
    for (int i = 0; ; i++) {
        byte b = s->input[i];
        if (b == '\0') { e->end = i; break; }
        if (b != '\n') continue;
        e->line++;
        if (i + 1 <= p) e->start = i + 1;
        else { e->end = i; break; }
    }
    e->column = p - e->start;
}

// {id = x}  =  START(nx), {x}, STOP
// Call {x} returning to STOP.
static inline void doSTART(parser *s, int arg) {
    s->stack[s->top++] = s->pc + arg;
}

// {id = x}  =  ... STOP
// Carry out any outstanding delayed actions. Return NULL for success, or an
// error report containing a bitmap of markers.
static inline void doSTOP(parser *s, err *e) {
    if (s->ok && s->out > 0) doActs(s);
    if (s->ok || s->in > s->marked) s->markers = 0L;
    e->ok = s->ok;
    e->at = s->in;
    e->markers = s->markers;
    if (! s->ok) findLine(s, e, s->in);
}

// {id}  =  GO(n)   or   BACK(n)
// Skip forwards or backwards in the code, to tail-call a remote rule.
static inline void doGO(parser *s, int arg) {
    s->pc = s->pc + arg;
}

// {x / y}  =  EITHER(nx), {x}, OR, {y}
// Save in/out, call x, returning to OR.
static inline void doEITHER(parser *s, int arg) {
    s->stack[s->top++] = s->in;
    s->stack[s->top++] = s->out;
    s->stack[s->top++] = s->pc + arg;
}

// {x / y}  =  EITHER(nx), {x}, OR, {y}
// After x, check success and progress, return or continue with y.
static inline void doOR(parser *s) {
    int saveOut = s->stack[--s->top];
    int saveIn = s->stack[--s->top];
    if (s->ok || s->in > saveIn) s->pc = s->stack[--s->top];
    else s->out = saveOut;
}

// {x y}  =  BOTH(nx), {x}, AND, {y}
// Call x, returning to AND.
static inline void doBOTH(parser *s, int arg) {
    s->stack[s->top++] = s->pc + arg;
}

// {x y}  =  BOTH(nx), {x}, AND, {y}
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
// Discard input and return.
static inline void doDROP(parser *s) {
    s->start = s->in;
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

// {"abc"}  =  STRING(3), 'a', 'b', 'c'
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

// {'a..z'}  =  LOW(n), 'a', HIGH(n), 'z'
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

// {'a..z'}  =  LOW(n), 'a', HIGH(n), 'z'
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

// {<abc>}  =  LESS(3), 'a', 'b', 'c'
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

// {'abc'}  =  SET(3), 'a', 'b', 'c'
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

// {%t} == TAG1, t
// Check if tag of next token is t and return.
static inline void doTAG(parser *s, int arg) {
}

static inline void getOpArg(parser *s, int *op, int *arg) {
#ifdef TRACE
    int pc0 = s->pc;
#endif
    *op = s->code[s->pc++];
    *arg = 1;
    if (*op >= O2) {
        *arg = s->code[s->pc++];
        *arg = (*arg << 8) | s->code[s->pc++];
    }
    else if (*op >= O1) {
        *arg = s->code[s->pc++];
    }
#ifdef TRACE
    if (*op >= START) printf("%d: %s %d\n", pc0, opNames[*op], *arg);
    else printf("%d: %s\n", pc0, opNames[*op]);
#endif
}

static void execute(parser *s, err *e) {
    while (true) {
        int op, arg;
        getOpArg(s, &op, &arg);
        switch (op) {
            case START: doSTART(s, arg); break;
            case STOP: doSTOP(s, e); return;
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

// By allocating the parser structure on the stack in the parse function and
// using inline functions, efficiency should be the same as using local
// variables and a monolithic switch statement.
void parse(int n, byte c[], byte in[], doNext *f, doAct *g, void *x, err *e) {
    parser pData;
    parser *s = &pData;
    new(s, c, in, f, g, x);
    entry(s, n);
    execute(s, e);
}

void report(err *e, char *s, char *s1, char *s2, char *s3, char *names[]) {
    if (e->markers == 0L) { fprintf(stderr, "%s", s); }
    else {
        fprintf(stderr, "%s", s1);
        bool first = true;
        for (int i = 0; i < 64; i++) {
            if ((e->markers & (1L << i)) == 0) continue;
            if (! first) fprintf(stderr, "%s", s2);
            first = false;
            fprintf(stderr, "%s", names[i]);
        }
        fprintf(stderr, "%s", s3);
    }
}

void reportLine(err *e, char *input) {
    fprintf(stderr, "%.*s\n", e->end - e->start, input + e->start);
}

void reportColumn(err *e) {
    for (int i = 0; i < e->column; i++) fprintf(stderr, " ");
    fprintf(stderr, "^\n");
}

#ifdef TEST

#include <assert.h>
#include <string.h>
enum action { number = 0, add = 1, subtract = 2, multiply = 3, divide = 4 };
enum marker { digit, operator, bracket, newline };
struct state { int n; char output[100]; };
typedef struct state state;

// Store actions symbolically.
static void act(void *vs, int a, char *matched, int n) {
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

static bool run(state *s, byte *code, char *input, char *output) {
    err eData;
    err *e = &eData;
    s->n = 0;
    parse(0, code, input, NULL, act, s, e);
    s->output[s->n] = '\0';
    if (! e->ok) sprintf(&s->output[s->n], "E%d:%lx", e->at, e->markers);
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
}

#endif
