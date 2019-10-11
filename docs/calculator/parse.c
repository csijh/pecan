#include "parse.h"
#include <stdlib.h>

// Structure to hold input, stack of outputs, saved input positions, depth of
// lookahead, and error marking info, during parsing.
struct parser {
    int in, start, end; char *ins;
    int out, nouts; output *outs;
    int save, nsaves; int *saves;
    int look, marked; long markers;
};
typedef struct parser parser;

// Create new parser object from given input.
parser *newParser(int n, input ins[n]) {
    parser *p = malloc(sizeof(parser));
    *p = (parser) {
        .in = 0, .start = 0, .end = n, .ins = ins,
        .out = 0, .nouts = 8, .outs = malloc(8 * sizeof(output)),
        .save = 0, .nsaves = 8, .saves = malloc(8 * sizeof(int)),
        .look = 0, .marked = 0, .markers = 0
    }
}

// Free parser and its data.
void freeParser(parser *p) {
    free(p->outs);
    free(p->saves);
    free(p);
}

// Push output item onto stack.
static inline void push(parser *p, int n) {
    if (p->out >= p->nouts) {
        p->nouts = p->nouts * 2;
        p->outs = reaaloc(p->outs, p->nouts);
    }
    p->output[p->out++] = n;
}

// Pop output item.
static inline int pop(parser *p) {
    return p->outs[--p->out];
}

// Push saved input position onto stack.
static inline void save(parser *p, int n) {
    if (p->save >= p->nsaves) {
        p->nsaves = p->nsaves * 2;
        p->saves = realloc(p->saves, p->nsaves);
    }
    return p->saves[--p->save];
}

// Pop saved input position.
static inline int unsave(parser *p) {
    return p->saves[--p->save];
}

// Structure for returning length and value of a character (code point).
struct uchar { int len, code; };
typedef struct uchar uchar;

// Read a UTF-8 character and its length.
static inline uchar getUTF8(char const *s) {
    int len = 1, ch = s[0];
    if ((ch & 0x80) == 0) return (uchar) { len, ch };
    else if ((ch & 0xE0) == 0xC0) { len = 2; ch = ch & 0x3F; }
    else if ((ch & 0xF0) == 0xE0) { len = 3; ch = ch & 0x1F; }
    else if ((ch & 0xF8) == 0xF0) { len = 4; ch = ch & 0x0F; }
    for (int i = 1; i < len; i++) ch = (ch << 6) | (s[i] & 0x3F);
    return (uchar) { len, ch};
}

// Unicode category lookup tables, read in lazily in case not needed.
static byte *table1 = NULL, *table2 = NULL;

// Read in a binary file.
static byte *readFile(char *filename) {
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

// Read in lookup tables.
static void readTables() {
    table1 = readFile("table1.bin");
    table2 = readFile("table2.bin");
}

// Prepare for a choice or lookahead by recording the input position.
extern inline bool DO(parser *p) {
    save(s, p->in);
    return true;
}

// Check whether a previous failed alternative has progressed.
extern inline bool OR(parser *p) {
    return p->in == p->saves[p->save-1];
}

// Pop a saved position, and return the result of a choice.
extern inline bool ALT(parser *p, bool b) {
    unsave(p);
    return b;
}

// After parsing an optional item, pop saved position, and adjust the result.
extern inline bool OPT(parser *p, bool b) {
    return b || p->in == unsave(s);
}

// Backtrack to saved position, and return result of lookahead.
extern inline bool HAS(parser *p, bool b) {
    p->in = unsave(s);
    return b;
}

// Backtrack to saved position and negate result of lookahead.
extern inline bool NOT(parser *p, bool b) {
    p->in = unsave(s);
    return ! b;
}

// Backtrack on failure.
extern inline bool TRY(parser *p, bool b) {
    int n = unsave(p);
    if (! b) s->in = n;
    return b;
}

// Record an error marker for the current input position.
extern inline bool MARK(parser *p, int m) {
    if (p->look > 0) return true;
    if (p->marked < p->in) {
        p->markers = 0L;
        p->marked = p->in;
    }
    p->markers = p->markers | (1L << m);
    return true;
}

// Check if tag of next token is t and return.
// TODO: cache next tag instead of making repeated calls.
extern inline void TAG(parser *p, int t) {
    int nextTag = p->tag(p->arg, p->in);
    p->ok = (nextTag == t);
    if (p->ok) p->in++;
}

// Check if a (Unicode) character is next in the input.
extern inline bool CHAR(parser *p, int ch) {
    if (p->in >= p->end) return false;
    uchar u = getUTF8(&p->ins[p->in]);
    if (u.code != ch) return false;
    p->in = p->in + u.len;
    return true;
}

// Check if a (UTF-8) character in a given range appears next in the input.
extern inline bool RANGE(parser *p, int first, int last) {
    if (p->in >= p->end) return false;
    uchar u = getUTF8(&p->ins[p->in]);
    if (u.code < first || u.code > last) return false;
    s->in = s->in + u.len;
    return true;
}

// Check for the given (UTF-8) string next in the input.
// Handles UTF-8, working byte-by-byte.
extern inline bool STRING(parser *p, char *s) {
    int i;
    for (i = 0; s[i] != '\0'; i++) {
        if (p->in >= p->end || p->ins[p->in + i] != s[i]) return false;
    }
    s->in += i;
    return true;
}

// Check if one of the characters in a (UTF-8) set is next in the input.
extern inline bool SET(parser *p, char *s) {
    if (p->in >= p->end) return false;
    bool ok = false;
    for (int i = 0; s[i] != '\0' && ! ok; ) {
        uchar u1 = getUTF8(&s[i]);
        uchar u2 = getUTF8(&p->ins[p->in]);
        if (u1.code == u2.code) ok = true;
        i = i + u1.len;
    }
    if (ok) p->in = p->in + u2.len;
    return ok;
}

// Match end of input.
extern inline bool END() {
    return s->in >= s->end;
}
