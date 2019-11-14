// Generic parser support in C. Public domain.
#include "parse.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef unsigned char byte;

// Structure to hold input, stack of outputs, saved input positions, depth of
// lookahead, and error marking info, during parsing. There is also a node store
// for allocating nodes, and the original text for error messages.
struct parser {
    int in, start, end; byte *input;
    int out, outLength; byte *output;
    int save, nsaves; int *saves;
    int look, marked; long markers;
    node *nodes; int textLength; char *text;
};

static parser *newParser(int n, void *input, int nc, char text[nc]) {
    parser *p = malloc(sizeof(parser));
    *p = (parser) {
        .in = 0, .start = 0, .end = n, .input = input,
        .out = 0, .outLength = 1, .output = malloc(sizeof(node)),
        .save = 0, .nsaves = 8, .saves = malloc(8 * sizeof(int)),
        .look = 0, .marked = 0, .markers = 0L,
        .nodes = NULL, .textLength = nc, .text = text
    };
    return p;
}

parser *newCharParser(int n, char input[n]) {
    return newParser(n, input, 0, NULL);
}

parser *newTokenParser(int n, token input[n], int nc, char text[nc]) {
    return newParser(n, input, nc, text);
}

token *getTokens(parser *p) {
    token *ts = (token *) p->output;
    ts = realloc(ts, p->out * sizeof(token));
    p->output = NULL;
    return ts;
}

void freeParser(parser *p) {
    if (p->output != NULL) free(p->output);
    free(p->saves);
    free(p);
}

int outputs(parser *p) {
    return p->out;
}

int at(parser *p) {
    return p->start;
}

extern inline int matched(parser *p) {
    return p->in - p->start;
}

extern inline void *match(parser *p) {
    return &p->input[p->start];
}


extern inline int topI(parser *p, int n) {
    int *xs = (int *) p->output;
    return xs[p->out - n - 1];
}

extern inline int64_t topL(parser *p, int n) {
    int64_t *xs = (int64_t *) p->output;
    return xs[p->out - n - 1];
}

extern inline double topD(parser *p, int n) {
    double *xs = (double *) p->output;
    return xs[p->out - n - 1];
}

extern inline void *topP(parser *p, int n) {
    void **xs = (void **) p->output;
    return xs[p->out - n - 1];
}

extern inline token topT(parser *p, int n) {
    token *xs = (token *) p->output;
    return xs[p->out - n - 1];
}

// A node pointer on the stack is relative to the start of the node store.
// Convert to a real pointer.
extern inline node *topN(parser *p, int n) {
    int *xs = (int *) p->output;
    return p->nodes + xs[p->out - n - 1];
}

// After pushing, make sure that there will be room to push the NEXT item.
extern inline bool pushI(parser *p, int n, int x) {
    p->start = p->in;
    p->out = p->out - n;
    int *xs = (int *) p->output;
    xs[p->out++] = x;
    if (p->out >= p->outLength) {
        p->outLength = p->outLength * 2;
        p->output = realloc(p->output, p->outLength * sizeof(int));
    }
    return true;
}

extern inline bool pushT(parser *p, int n, token x) {
    p->start = p->in;
    p->out = p->out - n;
    token *xs = (token *) p->output;
    xs[p->out++] = x;
    if (p->out >= p->outLength) {
        p->outLength = p->outLength * 2;
        p->output = realloc(p->output, p->outLength * sizeof(token));
    }
    return true;
}

extern inline bool go(parser *p) {
    if (p->save >= p->nsaves) {
        p->nsaves = p->nsaves * 2;
        p->saves = realloc(p->saves, p->nsaves);
    }
    p->saves[p->save++] = p->in;
    return true;
}

extern inline bool ok(parser *p) {
    return p->in == p->saves[p->save-1];
}

extern inline bool alt(parser *p, bool b) {
    --p->save;
    return b;
}

extern inline bool opt(parser *p, bool b) {
    return b || p->in == p->saves[--p->save];
}

extern inline bool has(parser *p, bool b) {
    p->in = p->saves[--p->save];
    return b;
}

extern inline bool not(parser *p, bool b) {
    p->in = p->saves[--p->save];
    return ! b;
}

extern inline bool see(parser *p, bool b) {
    if (b) --p->save;
    else p->in = p->saves[--p->save];
    return b;
}

extern inline bool mark(parser *p, int m) {
    if (p->look > 0) return true;
    if (p->marked < p->in) {
        p->marked = p->in;
        p->markers = 0L;
    }
    p->markers = p->markers | (1L << m);
    return true;
}

extern inline bool tag(parser *p, int t, int nt) {
    bool b = (nt == t);
    if (b) p->in++;
    return b;
}

// Structure for returning length and value of a character (code point).
struct uchar { int len, code; };
typedef struct uchar uchar;

// Read a UTF-8 character and its length. This is made extern so a cross-module
// optimizer can see it.
extern inline uchar getUTF8(byte const *s) {
    int len = 1, ch = s[0];
    if ((ch & 0x80) == 0) return (uchar) { len, ch };
    else if ((ch & 0xE0) == 0xC0) { len = 2; ch = ch & 0x3F; }
    else if ((ch & 0xF0) == 0xE0) { len = 3; ch = ch & 0x1F; }
    else if ((ch & 0xF8) == 0xF0) { len = 4; ch = ch & 0x0F; }
    for (int i = 1; i < len; i++) ch = (ch << 6) | (s[i] & 0x3F);
    return (uchar) {len, ch};
}

// Unicode category lookup tables, read in lazily in case not needed.
typedef unsigned char byte;
static byte *table1 = NULL, *table2 = NULL;

// Read in a binary file. Extern so a cross-module optimizer can see it.
byte *readBinaryFile(char *filename) {
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

// Read in lookup tables. Extern so a cross-module optimizer can see it.
void readLookupTables() {
    table1 = readBinaryFile("table1.bin");
    table2 = readBinaryFile("table2.bin");
}

extern inline bool point(parser *p) {
    if (p->in >= p->end) return false;
    uchar u = getUTF8(&p->input[p->in]);
    p->in += u.len;
    return true;
}

extern inline bool cat(parser *p, int c) {
    if (p->in >= p->end) return false;
    if (table1 == NULL) readLookupTables();
    uchar u = getUTF8(&p->input[p->in]);
    int cat = table2[table1[u.code>>8]*256+(u.code&255)];
    bool ok = cat == c;
    if (ok) p->in += u.len;
    return ok;
}

extern inline bool range(parser *p, int first, int last) {
    if (p->in >= p->end) return false;
    uchar u = getUTF8(&p->input[p->in]);
    if (u.code < first || u.code > last) return false;
    p->in = p->in + u.len;
    return true;
}

// Handles UTF-8, working byte-by-byte.
extern inline bool string(parser *p, char *s) {
    byte *bs = (byte *) s;
    int i;
    for (i = 0; bs[i] != '\0'; i++) {
        if (p->in >= p->end || p->input[p->in + i] != bs[i]) return false;
    }
    p->in += i;
    return true;
}

extern inline bool set(parser *p, char *s) {
    if (p->in >= p->end) return false;
    byte *bs = (byte *) s;
    bool ok = false;
    uchar u1, u2;
    for (int i = 0; s[i] != '\0' && ! ok; ) {
        u1 = getUTF8(&bs[i]);
        u2 = getUTF8(&p->input[p->in]);
        if (u1.code == u2.code) ok = true;
        i = i + u1.len;
    }
    if (ok) p->in = p->in + u2.len;
    return ok;
}

extern inline bool drop(parser *p, int n) {
    p->start = p->in;
    p->out = p->out - n;
    return true;
}

// Match end of input.
extern inline bool eot(parser *p) {
    return p->in >= p->end;
}

// Find the text.
static char *findText(parser *p) {
    if (p->text == NULL) return (char *) p->input;
    return p->text;
}

// Find the length of the text.
static int findTextLength(parser *p) {
    if (p->text == NULL) return p->end;
    return p->textLength;
}

// Find the current position in the text.
static int findTextPosition(parser *p) {
    if (p->text == NULL) return p->in;
    token *ts = (token *) p->input;
    return ts[p->in].at;
}

// Find the line number at the current position within the text.
int lineNumber(parser *p) {
    char *text = findText(p);
    int at = findTextPosition(p);
    int r = 1;
    for (int i = 0; i < at; i++) if (text[i] == '\n') r++;
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
void report(parser *p, char *s0, char *s, char *names[]) {
    char *text = findText(p);
    int n = findTextLength(p);
    int at = findTextPosition(p);
//    int line = lineNumber(at, text);
    int start = startLine(at, text);
    int end = endLine(at, text, n);
    int column = at - start;
    if (p->markers == 0L) {
        fprintf(stderr, "%s", s0);
        reportLine(start, end, text);
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
        if ((p->markers & (1L << i)) == 0) continue;
        if (! first) fprintf(stderr, "%s", string);
        first = false;
        fprintf(stderr, "%s", names[i]);
    }
    strcpy(string, string + ps + 2);
    fprintf(stderr, "%s", string);
    reportLine(start, end, text);
    reportColumn(column);
}
