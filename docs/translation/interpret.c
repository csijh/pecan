// Interpreter template in C. Public domain.

#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

// Change this section for different applications.

// Switch tracing on or off.
enum { tracing = false };

// The input type should be char for a scanner or text-based parser, otherwise
// it should be a token structure or token pointer.
typedef char input;
typedef int output;

// Action constants, in alphabetical order.
enum action { add, divide, multiply, read, subtract };

// Handle actions.
output act(int a, int n, char *s, output *xs) {
    output x;
    switch (a) {
        case read:
            x = 0;
            for (int i = 0; i < n; i++) x = x * 10 + s[i] - '0';
            return x;
        case add: return xs[0] + xs[1];
        case subtract: return xs[0] - xs[1];
        case multiply: return xs[0] * xs[1];
        case divide: return xs[0] / xs[1];
        default: return -1;
    }
}

// Get tag of i'th token (not needed for text-based parsing).
int tag(int i) { return -1; }

// Error marker constants, and spellings, in alphabetical order.
enum marker { bracket, integer, newline, operator };
char *names[] = { "bracket", "integer", "newline", "operator" };

// Forward declarations of parser structure and functions.
struct parser;
typedef struct parser parser;
typedef unsigned char byte;
byte *readFile(char *filename);
parser *newParser(byte *bytecode);
void freeParser(parser *p);
bool execute(parser *p, int n, input[n]);
output answer(parser *p);
void report(parser *p, char *ds, char *f, char *names[]);

// Read in the bytecode file, then read in a sum from the user and evaluate it.
int main() {
  byte *bytecode = readFile("sum.bin");
  char in[100];
  printf("Type a sum: ");
  char *r = fgets(in, 100, stdin);
  if (r == NULL) printf("Can't read stdin\n");
  parser *p = newParser(bytecode);
  bool ok = execute(p, strlen(in), in);
  if (ok) printf("%d\n", answer(p));
  else report(p, "Syntax error:\n", "Error: expecting %s, %s\n", names);
  freeParser(p);
  free(bytecode);
}

// ----- Parsing support ------------------------------------------------------

// The opcodes, in alphabetical order.
enum op {
    ACT, AND, ARITY, BACK, BOTH, CAT, DO, DROP,
    EITHER, EOT, GO, HAS, HIGH, LOOK, LOW, MANY,
    MARK, MAYBE, NOT, ONE, OR, POINT, SEE, SET,
    SPLIT, START, STOP, STRING, TAG
};

// Types of operand.
enum types { None, Number, String, Offset };

// Operand types for each opcode, for tracing.
int operandTypes[] = {
    Number, None, Number, Offset, Offset, Number, None, Number,
    Offset, None, Offset, None, String, None, String, None,
    Number, None, None, None, None, None, None, String,
    String, Offset, None, String, Number
};

// Names of opcodes, for tracing.
static char *opnames[] = {
    "ACT", "AND", "ARITY", "BACK", "BOTH", "CAT", "DO", "DROP",
    "EITHER", "EOT", "GO", "HAS", "HIGH", "LOOK", "LOW", "MANY",
    "MARK", "MAYBE", "NOT", "ONE", "OR", "POINT", "SEE", "SET",
    "SPLIT", "START", "STOP", "STRING", "TAG"
};

// Unicode category codes, in alphabetical order, as in the lookup tables.
enum category {
    Cc, Cf, Cn, Co, Cs, Ll, Lm, Lo, Lt, Lu, Mc, Me, Mn, Nd, Nl, No,
    Pc, Pd, Pe, Pf, Pi, Po, Ps, Sc, Sk, Sm, So, Zl, Zp, Zs
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

// Create new parser object from given bytecode.
parser *newParser(byte *bytecode) {
  parser *p = malloc(sizeof(parser));
  *p = (parser) {
    .in = 0, .start = 0, .end = -1, .ins = NULL,
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

// Line/column and start/end of line containing an input position
struct lineInfo { int line, column, start, end; };
typedef struct lineInfo lineInfo;

static void reportLine(char *in, lineInfo *li) {
    fprintf(stderr, "%.*s\n", li->end - li->start, in + li->start);
}

static void reportColumn(lineInfo *li) {
    for (int i = 0; i < li->column; i++) fprintf(stderr, " ");
    fprintf(stderr, "^\n");
}

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
// Clear any markers before the current position.
static inline void doSTOP(parser *p) {
    if (p->ok || p->in > p->marked) p->markers = 0L;
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
// After x, backtrack, and if successful, tail-call x for actions/markers.
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
        p->out = p->out - arg;
    }
    p->ok = true;
    p->pc = p->saves[--p->nsaves];
}

// {@2add}  =  ARITY(2) ACT(add)
// Set up the arity for the following ACT instruction.
static inline void doARITY(parser *p, int arg) {
    p->arity = arg;
}

// {@2add}  =  ARITY(2) ACT(add)
// Call act() with action code, matched text, and array of outputs.
static inline void doACT(parser *p, int arg) {
    if (p->look == 0) {
        output *array = &p->outs[p->out - p->arity];
        output x = act(arg, p->in - p->start, &p->ins[p->start], array);
        p->out = p->out - p->arity;
        p->outs[p->out++] = x;
        p->start = p->in;
    }
    p->arity = 0;
    p->ok = true;
    p->pc = p->saves[--p->nsaves];
}

// {#m}  =  MARK(m)
// Record an error marker. Assume 0 <= arg <= 63.
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
// Match string and return success or failure. Note matching a byte at a time
// works with UTF-8.
static inline void doSTRING(parser *p, int arg) {
    p->ok = true;
    if (p->in + arg > p->end) p->ok = false;
    else for (int i = 0; i < arg && p->ok; i++) {
        byte bc = p->code[p->pc + i];
        byte bi = p->ins[p->in + i];
        if (bi != bc) p->ok = false;
    }
    if (p->ok) p->in = p->in + arg;
    p->pc = p->saves[--p->nsaves];
}

// {'a..z'}  =  LOW(n) 'a' HIGH(n) 'z'
// Check 'a' <= in, continue with HIGH or return failure
static inline void doLOW(parser *p, int arg) {
    p->ok = true;
    if (p->in + arg >= p->end) p->ok = false;
    for (int i = 0; i < arg && p->ok; i++) {
        if (p->in + i >= p->end) { p->ok = false; break; }
        byte bc = p->code[p->pc + i];
        byte bi = p->ins[p->in + i];
        if (bc > bi) p->ok = false;
    }
    if (p->ok) p->pc = p->pc + arg;
    else p->pc = p->saves[--p->nsaves];
}

// {'a..z'}  =  ...HIGH(n) 'z'
// Check <= 'z', return success or failure
static inline void doHIGH(parser *p, int arg) {
    p->ok = true;
    if (p->in >= p->end) p->ok = false;
    for (int i = 0; i < arg && p->ok; i++) {
        if (p->in + i >= p->end) break;
        byte bc = p->code[p->pc + i];
        byte bi = p->ins[p->in + i];
        if (bi > bc) p->ok = false;
    }
    if (p->ok) p->in = p->in + arg;
    p->pc = p->saves[--p->nsaves];
}

// {<abc>}  =  SPLIT(3) 'a' 'b' 'c'
// Check if input <= "abc", return.
static inline void doSPLIT(parser *p, int arg) {
    p->ok = true;
    for (int i = 0; i < arg && p->ok; i++) {
        if (p->in + i == p->end) break;
        byte bc = p->code[p->pc + i];
        byte bi = p->ins[p->in + i];
        if (bi > bc) p->ok = false;
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
            byte bc = p->code[p->pc + i + j];
            byte bi = p->ins[p->in + j];
            if (bi != bc) oki = false;
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

// {.}  =  POINT
// Match one character.
static inline void doPOINT(parser *p, int arg) {
    p->ok = p->in < p->end;
    if (p->ok) {
        int len = lengthUTF8(p->ins[p->in]);
        p->in += len;
    }
    p->pc = p->saves[--p->nsaves];
}

// {Nd}  =  CAT(Nd)
// Check if next character is in given category.
static inline void doCAT(parser *p, int arg) {
    if (p->ins[p->in] == '\0') { p->ok = false; return; }
    if (table1 == NULL) readTables();
    int ch, len;
    ch = getUTF8(&p->ins[p->in], &len);
    int cat = table2[table1[ch>>8]*256+(ch&255)];
    p->ok = cat == arg;
    if (p->ok) p->in += len;
    p->pc = p->saves[--p->nsaves];
}

// {<>}  =  EOT
// Check for end of input.
static inline void doEOT(parser *p) {
    p->ok = (p->in == p->end);
    p->pc = p->saves[--p->nsaves];
}

static void trace(parser *p, int start, int end, int op, int arg) {
    printf("%d: ", start);
    switch(operandTypes[op]) {
        case None: printf("%s\n", opnames[op]); break;
        case Number: printf("%s %d\n", opnames[op], arg); break;
        case Offset:
            if (op == BACK) printf("%s %d\n", opnames[op], end - arg);
            else printf("%s %d\n", opnames[op], end + arg);
            break;
        case String:
            printf("%s %d", opnames[op], arg);
            for (int i = 0; i < arg; i++) printf(" %d", p->code[end + i]);
            printf("\n");
            break;
    }
}

bool execute(parser *p, int n, input ins[n]) {
    p->end = n;
    p->ins = ins;
    int b, op, arg;
    while (true) {
        int start = p->pc;
        b = p->code[p->pc++];
        op = b & 0x1F;
        arg = (b >> 5) & 0x3;
        while ((b & 0x80) != 0) {
            b = p->code[p->pc++];
            arg = (arg << 7) | (b & 0x7F);
        }
        if (tracing) trace(p, start, p->pc, op, arg);
        switch (op) {
            case ACT: doACT(p, arg); break;
            case ARITY: doARITY(p, arg); break;
            case START: doSTART(p, arg); break;
            case STOP: doSTOP(p); return p->ok;
            case GO: doGO(p, arg); break;
            case BACK: doGO(p, -arg); break;
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
            case DROP: doDROP(p, arg); break;
            case MARK: doMARK(p, arg); break;
            case STRING: doSTRING(p, arg); break;
            case LOW: doLOW(p, arg); break;
            case HIGH: doHIGH(p, arg); break;
            case SPLIT: doSPLIT(p, arg); break;
            case SET: doSET(p, arg); break;
            case TAG: doTAG(p, arg); break;
            case POINT: doPOINT(p, arg); break;
            case CAT: doCAT(p, arg); break;
            case EOT: doEOT(p); break;
            default: printf("Bad op %d\n", op); exit(1);
        }
    }
}
