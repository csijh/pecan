// Interpreter template in C. Public domain.

#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

// Change this section for different applications.

// The input type should be char for a scanner or text-based parser, otherwise
// it should be a token structure or token pointer.
typedef char input;
typedef int output;

// Action constants.
enum action { value, add, subtract, multiply, divide };

// Handle actions.
output act(int a, int n, char *s, output *xs) {
    switch (a) {
        case value:
            output x = 0;
            for (int i = 0; i < n; i++) x = x * 10 + s[i] - '0';
            return x;
        case add: return xs[0] + xs[1];
        case subtract: return xs[0] - xs[1];
        case multiply: return xs[0] * xs[1];
        case divide: return xs[0] / xs[1];
    }
}

// Get tag of i'th token (not needed for text-based parsing).
int tag(int i) { return -1; }

// Error marker constants, and spellings.
enum marker { integer, operator, bracket, newline };
char *names[] = { "integer", "operator", "bracket", "newline" };

// Forward declarations of parser structure and functions.
struct parser;
typedef struct parser parser;
typedef unsigned char byte;
byte *readFile(char *filename);
parser *newParser(byte *bytecode, int n, input[n]);
void freeParser(parser *p);
output answer(parser *p);
void report(parser *p, char *ds, char *f, char *names[]);

// Read in the bytecode file, then read in a sum from the user and evaluate it.
int main() {
  byte *bytecode = readFile("sum.bin");
  char in[100];
  printf("Type a sum: ");
  char *r = fgets(in, 100, stdin);
  if (r == NULL) printf("Can't read stdin\n");
  parser *p = newParser(bytecode, strlen(in), in);
  bool ok = sum(p);
  if (ok) printf("%d\n", answer(p));
  else report(p, "Syntax error:\n", "Error: expecting %s, %s\n", names);
  freeParser(p);
}

// ----- Parsing support ------------------------------------------------------

// The opcodes, in alphabetical order.
enum op {
    ACT, AND, ARITY, BACK, BOTH, CAT, DO, DROP, EITHER, EOT, GO, HAS, HIGH,
    SPLIT, LOOK, LOW, MANY, MARK, MAYBE, NOT, ONE, OR, SEE, SET, START, STOP,
    STRING, TAG;
};

// Unicode category codes, in the order used in the lookup tables.
enum category {
    Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
    Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf
};

// Structure to hold input, stack of outputs, saved bytecode addresses and input
// positions, depth of lookahead, error markers, and bytecode, during parsing.
struct parser {
  int in, start, end; input *ins;
  int out, nouts; output *outs;
  int save, nsaves; int *saves;
  int look, marked; long markers;
  byte *code; int pc, arity, tagpos, tag; bool ok;
};

// Read in a binary file.
byte *readFile(char *filename) {
  FILE *fp = fopen(filename, "rb");
  if (fp == NULL) { printf("Can't read %s\n", filename); exit(1); }
  fseek(fp, 0, SEEK_END);
  long size = ftell(fp);
  fseek(fp, 0, SEEK_SET);
  byte *content = malloc(size);
  int n = fread(content, 1, size, fp);
  if (n != size) { printf("Can't read %s\n", filename); exit(1); }
  fclose(fp);
  return content;
}

// Create new parser object from given input.
parser *newParser(byte[] bytecode, int n, input ins[n]) {
  parser *p = malloc(sizeof(parser));
  *p = (parser) {
    .in = 0, .start = 0, .end = n, .ins = ins,
    .out = 0, .nouts = 8, .outs = malloc(8 * sizeof(output)),
    .save = 0, .nsaves = 8, .saves = malloc(8 * sizeof(int)),
    .look = 0, .marked = 0, .markers = 0L,
    .code = bytecode, .pc = 0, .arity = 0, .tagpos = -1, .tag = 0, .ok = true
  };
  return p;
}

// Free parser and its data.
void freeParser(parser *p) {
  free(p->outs);
  free(p->saves);
  free(p);
}

// Return final output item.
output answer(parser *p) {
  return p->outs[--p->out];
}

// Return a pointer to the text for an action.
static inline input *start(parser *p) {
  return &p->ins[p->start];
}

// Return the length of the text for an action.
static inline int length(parser *p) {
  return p->in - p->start;
}

static void reportLine(char *in, lineInfo *li) {
    fprintf(stderr, "%.*s\n", li->end - li->start, in + li->start);
}

static void reportColumn(lineInfo *li) {
    for (int i = 0; i < li->column; i++) fprintf(stderr, " ");
    fprintf(stderr, "^\n");
}

// Print a report on stderr using s0 if there are no markers, or s if there are,
// with s containing two copies of %s as an example print string for two
// markers. Print the line containing the error on stderr. Print spaces followed
// by a ^ character to report the error column on stderr.
void report(parser *p, char *s0, char *s, char *names[]) {
    lineInfo liData;
    lineInfo *li = &liData;
    findLine(p->ins, p->in, li);
    if (p->markers == 0L) fprintf(stderr, "%s", s0);
    else {
      char text[100];
      strcpy(text, s);
      int n = strstr(text, "%s") - text;
      text[n] = '\0';
      fprintf(stderr, "%s", text);
      strcpy(text, text + n + 2);
      n = strstr(text, "%s") - text;
      text[n] = '\0';
      bool first = true;
      for (int i = 0; i < 64; i++) {
        if ((p->markers & (1L << i)) == 0) continue;
        if (! first) fprintf(stderr, "%s", text);
        first = false;
        fprintf(stderr, "%s", names[i]);
      }
      strcpy(text, text + n + 2);
      fprintf(stderr, "%s", text);
    }
    reportLine(p->ins, li);
    reportColumn(li);
}

// Unicode category lookup tables, read in lazily in case not needed.
static byte *table1 = NULL, *table2 = NULL;

// Read in lookup tables.
static void readTables() {
    table1 = readFile("table1.bin");
    table2 = readFile("table2.bin");
}

// ----- Functions for each opcode ----------

// {id = x}  =  START(nx) {x} STOP
// Call {x} returning to STOP.
static inline void doSTART(parser *p, int arg) {
    p->saves[p->nsaves++] = p->pc + arg;
}

// {id = x}  =  ... STOP
// Return NULL for success, or an error report containing a bitmap of markers.
static inline void doSTOP(parser *p, result *r) {
    if (p->ok || p->in > p->marked) p->markers = 0L;
    r->ok = p->ok;
    r->at = p->in;
    r->markers = p->markers;
}

// {id}  =  GO(n)   or   BACK(n)
// Skip forwards or backwards in the code, to tail-call a remote rule.
static inline void doGO(parser *p, int arg) {
    p->pc = p->pc + arg;
}

// {x / y}  =  EITHER(nx) {x} OR {y}
// Save in, call x, returning to OR.
static inline void doEITHER(parser *p, int arg) {
    p->saves[p->nsaves++] = p->in;
    p->saves[p->nsaves++] = p->pc + arg;
}

// {x / y}  =  EITHER(nx) {x} OR {y}
// After x, check success and progress, return or continue with y.
static inline void doOR(parser *p) {
    int saveIn = p->saves[--p->nsaves];
    if (p->ok || p->in > saveIn) p->pc = p->saves[--p->nsaves];
}

// {x y}  =  BOTH(nx) {x} AND {y}
// Call x, returning to AND.
static inline void doBOTH(parser *p, int arg) {
    p->saves[p->nsaves++] = p->pc + arg;
}

// {x y}  =  ...AND {y}
// After x, check success, continue with y or return.
static inline void doAND(parser *p) {
    if (! p->ok) p->pc = p->saves[--p->nsaves];
}

// {x?}  =  MAYBE ONE {x}   and similarly for x*, x+
// Save in and call x, returning to ONE or MANY.
static inline void doMAYBE(parser *p) {
    p->saves[p->nsaves++] = p->in;
    p->saves[p->nsaves++] = p->pc;
    p->pc++;
}

// {x?}  =  MAYBE ONE {x}
// After x, check success or no progress and return.
static inline void doONE(parser *p) {
    int saveIn = p->saves[--p->nsaves];
    if (! p->ok && p->in == saveIn) {
        p->ok = true;
    }
    p->pc = p->saves[--p->nsaves];
}

// {x*}  =  MAYBE MANY {x}
// After x, check success and re-try x or return.
static inline void doMANY(parser *p) {
    int saveIn = p->saves[--p->nsaves];
    if (p->ok) {
        p->saves[p->nsaves++] = p->in;
        p->saves[p->nsaves++] = p->pc - 1;
    }
    else {
        if (! p->ok && p->in == saveIn) {
            p->ok = true;
        }
        p->pc = p->saves[--p->nsaves];
    }
}

// {x+}  =  DO AND MAYBE MANY {x}
// Call x, returning to AND.
static inline void doDO(parser *p) {
    p->saves[p->nsaves++] = p->pc;
    p->pc = p->pc + 3;
}

// {[x]}  =  LOOK SEE x   and similarly for x& and x!
// Save in and call x as a lookahead, returning to SEE or HAS or NOT.
static inline void doLOOK(parser *p) {
    p->saves[p->nsaves++] = p->in;
    p->look++;
    p->saves[p->nsaves++] = p->pc;
    p->pc++;
}

// {[x]}  =  LOOK SEE x
// After x, backtrack, and if successfull, tail-call x for actions/markers.
static inline void doSEE(parser *p) {
    int saveIn = p->saves[--p->nsaves];
    p->look--;
    p->in = saveIn;
    if (! p->ok) p->pc = p->saves[--p->nsaves];
}

// {x&}  =  LOOK HAS x
// After x, backtrack and return.
static inline void doHAS(parser *p) {
    p->in = p->saves[--p->nsaves];
    p->look--;
    p->pc = p->saves[--p->nsaves];
}

// {x!}  =  LOOK NOT x
// After x, backtrack, invert the result, and return.
static inline void doNOT(parser *p) {
    p->in = p->saves[--p->nsaves];
    p->look--;
    p->ok = ! p->ok;
    p->pc = p->saves[--p->nsaves];
}

// {@n}  =  DROP(n)      discard matched text and n outputs
static inline void doDROP(parser *p, int arg) {
    if (p->look == 0) {
        p->start = p->in;
        p->nouts = p->nouts - arg;
    }
    p->ok = true;
    p->pc = p->saves[--p->nsaves];
}

// {@2add}  =  ARITY(2) ACT(add)
static inline void doARITY(parser *p, int arg) {
    p->arity = arg;
}

// {@2add}  =  ARITY(2) ACT(add)
static inline void doACT(parser *p, int arg) {
    if (p->look == 0) {
        output *array = &p->outs[p->nouts - p->arity];
        output x = act(arg, p->in - p->start, p->start, array);
        p->nouts = p->nouts - p->arity;
        p->outs[p->nouts++] = x;
        p->start = p->in;
    }
    p->ok = true;
    p->pc = p->saves[--p->nsaves];
}

// {#m}  =  MARK(m)
// Record an error marker. Assume 0 <= arg <= 62.
static inline void doMARK(parser *p, int arg) {
    if (p->look == 0) {
        if (p->in > p->marked) {
            p->marked = p->in;
            p->markers = 0L;
        }
        p->markers = p->markers | (1L << arg);
    }
    p->ok = true;
    p->pc = p->saves[--p->nsaves];
}

// {"abc"}  =  STRING(3) 'a' 'b' 'c'
// Match string and return success or failure.
static inline void doSTRING(parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg && p->ok; i++) {
        byte b = p->ins[p->in + i];
        if (b == '\0' || b != p->code[p->pc + i]) p->ok = false;
    }
    if (p->ok) {
        p->in = p->in + arg;
    }
    p->pc = p->saves[--p->nsaves];
}

// {'a..z'}  =  LOW(n) 'a' HIGH(n) 'z'
// Check >= 'a', continue with HIGH or return failure
static inline void doLOW(parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = p->ins[p->in + i];
        if (b == '\0' || b < p->code[p->pc + i]) p->ok = false;
    }
    if (p->ok) p->pc = p->pc + arg;
    else {
        p->pc = p->saves[--p->nsaves];
    }
}

// {'a..z'}  =  ...HIGH(n) 'z'
// Check <= 'z', return success or failure
static inline void doHIGH(parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg; i++) {
        byte b = p->ins[p->in + i];
        if (b == '\0' || b > p->code[p->pc + i]) p->ok = false;
    }
    if (p->ok) {
        p->pc = p->pc + arg;
        p->in = p->in + arg;
    }
    p->pc = p->saves[--p->nsaves];
}

// {<abc>}  =  SPLIT(3) 'a' 'b' 'c'
// Check if input <= "abc", return.
static inline void doSPLIT(parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg && p->ok; i++) {
        if (p->in + i == p->end) p->ok = false;
        else {
            byte b = p->ins[p->in + i];
            if (b > p->code[p->pc + i]) p->ok = false;
        }
    }
    p->pc = p->saves[--p->nsaves];
}

// Find the length of a UTF-8 character from its first byte.
static inline int lengthUTF8(byte first) {
    if ((first & 0x80) == 0) return 1;
    if ((first & 0xE0) == 0xC0) return 2;
    if ((first & 0xF0) == 0xE0) return 3;
    return 4;
}

// Read a UTF-8 character and its length.
static inline int getUTF8(char const *s, int *plength) {
    int ch = s[0], len = 1;
    if ((ch & 0x80) == 0) { *plength = len; return ch; }
    else if ((ch & 0xE0) == 0xC0) { len = 2; ch = ch & 0x3F; }
    else if ((ch & 0xF0) == 0xE0) { len = 3; ch = ch & 0x1F; }
    else if ((ch & 0xF8) == 0xF0) { len = 4; ch = ch & 0x0F; }
    for (int i = 1; i < len; i++) ch = (ch << 6) | (s[i] & 0x3F);
    *plength = len;
    return ch;
}

// {'abc'}  =  SET(3) 'a' 'b' 'c'
// Check for one of the characters in a set, and return.
static inline void doSET(parser *p, int arg) {
    p->ok = false;
    int n = 0;
    for (int i = 0; i < arg && ! p->ok; ) {
        byte b = p->code[p->in + i];
        n = lengthUTF8(b);
        bool oki = true;
        if (p->in + n > p->end) oki = false;
        else for (int j = 0; j < n && oki; j++) {
            if (p->ins[p->in + j] != p->code[p->pc + i + j]) oki = false;
        }
        if (oki) p->ok = true;
    }
    if (p->ok) p->in = p->in + n;
    p->pc = p->saves[--p->nsaves];
}

// {%t} == TAG(t)
// Check if tag of next token is t and return.
static inline void doTAG(parser *p, int t) {
    int nextTag;
    if (p->in == p->tagpos) nextTag = p->tag;
    else {
        nextTag = tag(p->in);
        p->tag = nextTag;
        p->tagpos = p->in;
    }
    p->ok = (nextTag == t);
    if (p->ok) p->in++;
    p->pc = p->saves[--p->nsaves];
}

// {Nd}  =  CAT(Nd)
// Check if next character is in given category.
static inline void doCAT(parser *p, int arg) {
    if (p->ins[p->in] == '\0') { p->ok = false; return; }
    if (arg == Uc) { p->ok = true; return; }
    if (table1 == NULL) readTables();
    int ch, len;
    ch = getUTF8(&p->ins[p->in], &len);
    int cat = table2[table1[ch>>8]*256+(ch&255)];
    p->ok = cat == arg;
    if (p->ok) {
        p->in += len;
    }
    p->pc = p->saves[--p->nsaves];
}

// {<>}  =  END
// Check for end of input.
static inline void doEND(parser *p) {
    if (p->ins != NULL) p->ok = p->ins[p->in] == '\0';
    else p->ok = p->tag(p->arg, p->in) < 0;
    p->pc = p->saves[--p->nsaves];
}

static inline void getOpArg(parser *p, int *op, int *arg) {
#ifdef TRACE
    int pc0 = p->pc;
#endif
    *op = p->code[p->pc++];
    *arg = 1;
    if (*op >= OP2) {
        *arg = p->code[p->pc++];
        *arg = (*arg << 8) | p->code[p->pc++];
    }
    else if (*op >= OP1) {
        *arg = p->code[p->pc++];
    }
#ifdef TRACE
    if (*op >= START) printf("%d: %s %d\n", pc0, opNames[*op], *arg);
    else printf("%d: %s\n", pc0, opNames[*op]);
#endif
}

static void execute(parser *p, result *r) {
    while (true) {
        int op, arg;
        getOpArg(p, &op, &arg);
        switch (op) {
            case START: doSTART(p, arg); break;
            case STOP: doSTOP(p, r); return;
            case GO: case GOL: doGO(p, arg); break;
            case BACK: case BACKL: doGO(p, -arg); break;
            case EITHER: doEITHER(p, arg); break;
            case OR: doOR(p); break;
            case BOTH: doBOTH(p, arg); break;
            case AND: doAND(p); break;
            case MAYBE: doMAYBE(p); break;
            case ONE: doONE(p); break;
            case MANY: doMANY(p); break;
            case DO: doDO(p); break;
            case LOOK: doLOOK(p); break;
            case SEE: doSEE(p); break;
            case HAS: doHAS(p); break;
            case NOT: doNOT(p); break;
            case DROP: doDROP(p); break;
            case ACT: doACT(p, arg); break;
            case MARK: doMARK(p, arg); break;
            case STRING: case STRING1: case SET1: doSTRING(p, arg); break;
            case LOW: case LOW1: doLOW(p, arg); break;
            case HIGH: case HIGH1: doHIGH(p, arg); break;
            case SPLIT: case SPLIT1: doSPLIT(p, arg); break;
            case SET: doSET(p, arg); break;
            case TAG: doTAG(p, arg); break;
            case CAT: doCAT(p, arg); break;
            case END: doEND(p); break;
            default: printf("Bad op %d\n", op); exit(1);
        }
    }
}

// The parse function makes calls to inline functions, and the parser structure
// is allocated locally, so it should be as efficient as a monolithic function,
// with local variables, containing a giant switch statement.
void parseText(byte code[], char in[], doAct *f, void *x, result *r) {
    parser pData;
    parser *p = &pData;
    new(p, code, in, NULL, f, x);
    execute(p, r);
}

void parseTokens(byte code[], doTag *g, doAct *f, void *x, result *r) {
    parser pData;
    parser *p = &pData;
    new(p, code, NULL, g, f, x);
    execute(p, r);
}

// Line/column and start/end of line containing an input position
struct lineInfo { int line, column, start, end; };
typedef struct lineInfo lineInfo;

// Find the line/column and start/end of line containing an input position.
static void findLine(char *input, int at, lineInfo *li) {
    li->line = 1;
    li->start = 0;
    for (int i = 0; ; i++) {
        byte b = input[i];
        if (b == '\0') { li->end = i; break; }
        if (b != '\n') continue;
        li->line++;
        if (i + 1 <= at) li->start = i + 1;
        else { li->end = i; break; }
    }
    li->column = at - li->start;
}


#ifdef interpretTest

// The examples in the calculator tutorial are used as self-tests.

#include <assert.h>

enum action { number = 0, add = 1, subtract = 2, multiply = 3, divide = 4 };
struct state { char *input; int n; char output[100]; };
typedef struct state state;

// Store actions symbolically for testing.
static void act(void *vs, int a, int p, int n) {
    state *s = vs;
    s->output[s->n++] = "#+-*/"[a];
    if (a == number) for (int i = 0; i < n; i++) {
        s->output[s->n++] = s->input[p + i];
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
    START, 24, EITHER, 19, BOTH, 9, LOOK, SEE, BOTH, 2, GO, 17, AND,
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

// Test for category recognition.
// ch = Nd @number
static byte cat[] = {
    START, 7, BOTH, 2, CAT, Nd, AND, ACT, number, STOP
};

static bool run(state *s, byte *code, char *input, char *output) {
    result rData;
    result *r = &rData;
    s->input = input;
    s->n = 0;
    parseText(code, input, act, s, r);
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
    assert(run(s, calc4, "2", "#2E1:0"));
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
    assert(run(s, calc8, "2+40%\n", "#2#40+E4:0"));
    assert(run(s, calc9, "2+10+12+18\n", "#2#10+#12+#18+"));
    assert(run(s, calc10, "2+10+12+18\n", "#2#10+#12+#18+"));
    assert(run(s, calc10, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc11, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc12, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc12, "2+\n", "#2E2:1"));
    assert(run(s, calc12, "2+40%\n", "#2#40+E4:b"));
    assert(run(s, calc13, "5*8+12/6\n", "#5#8*#12#6/+"));
    assert(run(s, calc14, "2*(20+1)\n", "#2#20#1+E7:b"));
    assert(run(s, calc15, "2*(20+1)\n", "#2#20#1+*"));
    assert(run(s, calc16, "2\n", "#2"));
    assert(run(s, calc16, "2+\n", "#2E2:15"));
    assert(run(s, calc17, "2\n", "#2"));
    assert(run(s, calc17, "42\n", "#42"));
    assert(run(s, calc17, "2+40\n", "#2#40+"));
    assert(run(s, calc17, "2+40%\n", "#2#40+E4:b"));
    assert(run(s, calc17, "2+10+12+18\n", "#2#10+#12+#18+"));
    assert(run(s, calc17, "2-10+53-3\n", "#2#10-#53+#3-"));
    assert(run(s, calc17, "2+\n", "#2E2:5"));
    assert(run(s, calc17, "5*8+12/6\n", "#5#8*#12#6/+"));
    assert(run(s, calc17, "2*(20+1)\n", "#2#20#1+*"));
    assert(run(s, calc17, " 2 * ( 20 + 1 ) \n", "#2#20#1+*"));
    assert(run(s, cat, "2", "#2"));
    assert(run(s, cat, "x", "E0:0"));
    printf("Interpreter OK\n");
}

#endif
