// Generic parser support in C. Public domain.
#include "parser.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

// The maximum number of bytes allowed to be allocated during a single action.
enum { MAX = 120 };

// A type for values pushed on the output stack, where R is a store-relative
// reference to an object in the store.
union value { int64_t I; double D; void *P; int R; };
typedef union value value;

// Structure with text and token input, start of recently matched input items,
// current input position, stack of outputs, stack of saved input positions,
// depth of lookahead, error marking info, an object store and a context.
struct parser {
    int maxText; char *text;
    int maxTokens; token *tokens;
    int start, at;
    int out, maxOut; value *output;
    int save, maxSaves; int *saves;
    int look, marked; uint64_t markers;
    int nextObj, maxStore; char *store;
    tagFunction *tag; void *context;
};

parser *newTokenParser(int n, char text[n], int nt, token *tokens) {
    parser *p = malloc(sizeof(parser));
    *p = (parser) {
        .maxText = n, .text = text,
        .maxTokens = nt, .tokens = tokens,
        .at = 0, .start = 0,
        .out = 0, .maxOut = 1, .output = malloc(16),
        .save = 0, .maxSaves = 8, .saves = malloc(8 * sizeof(int)),
        .look = 0, .marked = 0, .markers = 0L,
        .nextObj = 0, .maxStore = 0, .store = NULL,
        .tag = NULL, .context = NULL
    };
    return p;
}

parser *newParser(int n, char text[n]) {
    parser *p = newTokenParser(n, text, 0, NULL);
    return p;
}

// Free the store only if it hasn't been extracted with getStore.
void freeParser(parser *p) {
    if (p->nextObj > 0) free(p->store);
    free(p->output);
    free(p->saves);
    free(p);
}

// Make non-static to allow inlining of caller.
void crashParser(char *s) {
    fprintf(stderr, "%s\n", s);
    exit(1);
}

// Double the output stack. Make non-static to allow inlining of caller.
void expandStack(parser *p) {
    p->maxOut = p->maxOut * 2;
    p->output = realloc(p->output, p->maxOut * sizeof(value));
}

// Double the object store. Make non-static to allow inlining of caller.
void expandStore(parser *p) {
    p->maxStore = p->maxStore * 2;
    p->store = realloc(p->store, p->maxStore);
}

extern inline void setContext(parser *p, tagFunction *tag, void *context) {
    p->tag = tag;
    p->context = context;
}

extern inline int lengthText(parser *p) {
    return p->maxText;
}

extern inline char *text(parser *p) {
    return p->text;
}

extern inline int lengthTokens(parser *p) {
    return p->maxTokens;
}

extern inline token *getTokens(parser *p) {
    return p->tokens;
}

extern inline int start(parser *p) {
    return p->start;
}

extern inline int at(parser *p) {
    return p->at;
}

// Initialize the store if necessary. This can be done now, because there cannot
// be any outstanding store pointers. Otherwise, avoid reallocations which would
// invalidate pointers by making sure (in push) that a reasonable amount of
// memory is available.
extern inline void *new(parser *p, int size) {
    if (p->store == NULL) {
        p->store = malloc(MAX);
        p->maxStore = MAX;
    }
    if (size > MAX) crashParser("Error: allocation request too large");
    if (p->nextObj < 0) crashParser("Error: allocation after parsing");
    char *next = p->store + p->nextObj;
    p->nextObj += size;
    return next;
}

extern inline int storeSize(parser *p) {
    return (p->nextObj < 0) ? -p->nextObj : p->nextObj;
}

// Set p->nextObj < 0 to stop store being freed and prevent further allocation.
extern inline void *getStore(parser *p) {
    if (p->nextObj < 0) return p->store;
    p->store = realloc(p->store, p->nextObj);
    p->nextObj = - p->nextObj;
    return p->store;
}

extern inline int64_t topI(parser *p, int n) {
    return p->output[p->out - n - 1].I;
}

extern inline double topD(parser *p, int n) {
    return p->output[p->out - n - 1].D;
}

extern inline void *topP(parser *p, int n) {
    return p->output[p->out - n - 1].P;
}

extern inline void *topR(parser *p, int n) {
    return p->store + p->output[p->out - n - 1].R;
}

// After pushing, make sure there is enough space to allocate the NEXT stack
// item. Make extern to allow inlining of caller.
extern inline bool pushValue(parser *p, int n, value v) {
    p->start = p->at;
    if (n == 0 && p->out >= p->maxOut) expandStack(p);
    p->out = p->out - n;
    p->output[p->out++] = v;
    if (p->store != NULL && p->nextObj + MAX > p->maxStore) expandStore(p);
    return true;
}

extern inline bool pushI(parser *p, int n, int64_t x) {
    value v = { .I = x };
    return pushValue(p, n, v);
}

extern inline bool pushD(parser *p, int n, double x) {
    value v = { .D = x };
    return pushValue(p, n, v);
}

extern inline bool pushP(parser *p, int n, void *x) {
    value v = { .P = x };
    return pushValue(p, n, v);
}

extern inline bool pushR(parser *p, int n, void *x) {
    value v = { .R = (char *)x - p->store };
    return pushValue(p, n, v);
}

extern inline bool go(parser *p) {
    if (p->save >= p->maxSaves) {
        p->maxSaves = p->maxSaves * 2;
        p->saves = realloc(p->saves, p->maxSaves * sizeof(int));
    }
    p->saves[p->save++] = p->at;
    return true;
}

extern inline bool ok(parser *p) {
    return p->at == p->saves[p->save-1];
}

extern inline bool alt(parser *p, bool b) {
    --p->save;
    return b;
}

extern inline bool opt(parser *p, bool b) {
    return b || p->at == p->saves[--p->save];
}

extern inline bool has(parser *p, bool b) {
    p->at = p->saves[--p->save];
    return b;
}

extern inline bool not(parser *p, bool b) {
    p->at = p->saves[--p->save];
    return ! b;
}

extern inline bool see(parser *p, bool b) {
    if (b) --p->save;
    else p->at = p->saves[--p->save];
    return b;
}

extern inline bool mark(parser *p, int m) {
    if (p->look > 0) return true;
    if (p->marked < p->at) {
        p->marked = p->at;
        p->markers = 0L;
    }
    p->markers = p->markers | (1ULL << m);
    return true;
}

extern inline bool tag(parser *p, int tag1) {
    if (p->maxTokens >= 0 && p->at >= p->maxTokens) return false;
    int tag2 = p->tokens[p->at].tag;
    if (tag2 < 0) {
        if (p->tag == NULL) crashParser("Error: negative tag, no tag function");
        tag2 = p->tag(&p->tokens[p->at], p->context);
        if (tag2 < 0) crashParser("Error: negative tag");
        p->tokens[p->at].tag = tag2;
    }
    bool b = tag2 == tag1;
    if (b) p->at++;
    return b;
}

// Table of lengths based on first five bits of first byte of character, with 0
// indicating an error. This is non-static for cross-module optimization.
const char lengthTable[] = {
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 3, 3, 4, 0
};

// Table of masks to extract code bits of first byte, based on length. This is
// non-static for cross-module optimization.
const int maskTable[]  = {0x00, 0x7f, 0x1f, 0x0f, 0x07};

// Find length of next character, with 0 indicating an error. This is made
// extern for cross-module optimization.
extern inline int UTF8length(char const *s) {
    unsigned char b = (unsigned char) s[0];
    return lengthTable[b >> 3];
}

// Find code of next character, or -1 for an error. If UTF8length is called
// just before this, an optimizing compiler will avoid calculating the length
// twice. This is made extern for cross-module optimization.
extern inline int UTF8code(char const *s) {
    int n = UTF8length(s);
    int ch = s[0] & maskTable[n];
    int err = !n;
    for (int i = 1; i < n; i++) {
        err = err | (s[i] & 0xC0);
        ch = (ch << 6) | (s[i] & 0x3F);
    }
    if (err != 0) return -1;
    return ch;
}

// Unicode category lookup tables, read in lazily in case not needed.
static char *table1 = NULL, *table2 = NULL;

// Read in a binary file. Extern so a cross-module optimizer can see it.
char *readBinaryFile(char *filename) {
    FILE *fp = fopen(filename, "rb");
    if (fp == NULL) { fprintf(stderr, "Can't read %s\n", filename); exit(1); }
    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);
    fseek(fp, 0, SEEK_SET);
    char *content = malloc(size);
    int n = fread(content, 1, size, fp);
    if (n != size) { fprintf(stderr, "Can't read %s\n", filename); exit(1); }
    fclose(fp);
    return content;
}

// Read in lookup tables. Extern so a cross-module optimizer can see it.
void readLookupTables() {
    table1 = readBinaryFile("table1.bin");
    table2 = readBinaryFile("table2.bin");
}

extern inline bool point(parser *p) {
    if (p->at >= p->maxText) return false;
    int len = UTF8length(&p->text[p->at]);
    if (len == 0) { fprintf(stderr, "Malformed UTF-8\n"); exit(1);}
    p->at += len;
    return true;
}

extern inline bool cat(parser *p, int c) {
    if (p->at >= p->maxText) return false;
    if (table1 == NULL) readLookupTables();
    int len = UTF8length(&p->text[p->at]);
    int code = UTF8code(&p->text[p->at]);
    if (code < 0) { fprintf(stderr, "Malformed UTF-8\n"); exit(1);}
    int cat = table2[table1[code>>8]*256+(code&255)];
    bool ok = cat == c;
    if (ok) p->at += len;
    return ok;
}

extern inline bool range(parser *p, int first, int last) {
    if (p->at >= p->maxText) return false;
    int len = UTF8length(&p->text[p->at]);
    int code = UTF8code(&p->text[p->at]);
    if (code < 0) { fprintf(stderr, "Malformed UTF-8\n"); exit(1);}
    if (code < first || code > last) return false;
    p->at = p->at + len;
    return true;
}

// Handles UTF-8, working byte-by-byte, with char signed or unsigned. It is
// probably better to have the eot test inside the loop than to call strlen.
extern inline bool string(parser *p, char *s) {
    int i;
    for (i = 0; s[i] != '\0'; i++) {
        if (p->at >= p->maxText || p->text[p->at + i] != s[i]) return false;
    }
    p->at += i;
    return true;
}

extern inline bool set(parser *p, char *s) {
    if (p->at >= p->maxText) return false;
    bool ok = false;
    int len1, len2, code1, code2;
    for (int i = 0; s[i] != '\0' && ! ok; ) {
        len1 = UTF8length(&s[i]);
        code1 = UTF8code(&s[i]);
        len2 = UTF8length(&p->text[p->at]);
        code2 = UTF8code(&p->text[p->at]);
        if (code2 < 0) { fprintf(stderr, "Malformed UTF-8\n"); exit(1);}
        if (code1 == code2) ok = true;
        i = i + len1;
    }
    if (ok) p->at = p->at + len2;
    return ok;
}

extern inline bool drop(parser *p, int n) {
    p->start = p->at;
    p->out = p->out - n;
    return true;
}

// Match end of input.
extern inline bool eot(parser *p) {
    int end = p->maxText;
    if (p->tokens != NULL) {
        end = p->maxTokens;
        if (end < 0) crashParser("eot called when number of tokens not given");
    }
    return p->at >= end;
}

int lineNumber(parser *p, int at) {
    int r = 1;
    for (int i = 0; i < at; i++) if (p->text[i] == '\n') r++;
    return r;
}

// Find the start of the line containing p.
static int startLine(int p, char *text) {
    while (p > 0 && text[p-1] != '\n') p--;
    return p;
}

// Find the end of the line containing p.
static int endLine(int p, char *text, int n) {
    while (p < n && text[p] != '\n') p++;
    return p;
}

// Print the line.
static void reportLine(int start, int end, char *text) {
    fprintf(stderr, "%.*s\n", end - start, text + start);
}

// Point to the column.
static void reportColumn(int column) {
    for (int i = 0; i < column; i++) fprintf(stderr, " ");
    fprintf(stderr, "^\n");
}

// Print a report on stderr using s0 if there are no markers, or s if there are,
// with s containing two copies of %s as an example print string for two
// markers. Print the line containing the error on stderr. Print spaces followed
// by a ^ character to report the error column on stderr.
void report(parser *p, int at, char *s0, char *s, char *names[]) {
    int start = startLine(at, p->text);
    int end = endLine(at, p->text, p->maxText);
    int column = at - start;
    if (p->markers == 0L) {
        fprintf(stderr, "%s", s0);
        reportLine(start, end, p->text);
        reportColumn(column);
        return;
    }
    char string[100];
    strcpy(string, s);
    int ps = strstr(string, "%s") - string;
    string[ps] = '\0';
    fprintf(stderr, "%s", string);
    strcpy(string, string + ps + 2);
    ps = strstr(string, "%s") - string;
    string[ps] = '\0';
    bool first = true;
    for (int i = 0; i < 64; i++) {
        if ((p->markers & (1ULL << i)) == 0) continue;
        if (! first) fprintf(stderr, "%s", string);
        first = false;
        fprintf(stderr, "%s", names[i]);
    }
    strcpy(string, string + ps + 2);
    fprintf(stderr, "%s", string);
    reportLine(start, end, p->text);
    reportColumn(column);
}
