#include <stdio.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

// -----------------------------------------------------------------------------
// Change this section for different applications.

// Declare the parser type and input and output types. The input type should be
// char for a scanner or text-based parser, otherwise a token structure or token
// pointer. The output type should be a number or pointer type.
struct parser;
typedef struct parser parser;
typedef char input;
typedef int output;

// Error marker constants, and spellings.
enum marker { digit, operator, bracket, newline };
char *names[] = { "digit", "operator", "bracket", "newline" };

// Forward declarations of support functions.
parser *newParser(int n, input[n]);
void freeParser(parser *p);
input *start(parser *p);
int length(parser *p);
void push(parser *p, output n);
output pop(parser *p);
void report(parser *p, char *ds, char *f, char *names[]);

// Forward declarations of parsing functions, to allow them to be recursive.
bool Psum(parser *p);
bool Pexpression(parser *p);
bool Pterm(parser *p);
bool Patom(parser *p);
bool Pnumber(parser *p);
bool Pgap(parser *p);
bool Pend(parser *p);
bool Pplus(parser *p);
bool Pminus(parser *p);
bool Ptimes(parser *p);
bool Pover(parser *p);
bool Popen(parser *p);
bool Pclose(parser *p);
bool Pdigit(parser *p);

// Actions. Create a number from the given text or carry out arithmetic.
bool number(parser *p) {
    input *s = start(p);
    int n = length(p);
    output x = 0;
    for (int i = 0; i < n; i++) x = x * 10 + s[i] - '0';
    push(p, x);
    return true;
}

bool add(parser *p) {
    output y = pop(p), x = pop(p);
    push(p, x + y);
    return true;
}

bool subtract(parser *p) {
    output y = pop(p), x = pop(p);
    push(p, x - y);
    return true;
}

bool multiply(parser *p) {
    output y = pop(p), x = pop(p);
    push(p, x * y);
    return true;
}

bool divide(parser *p) {
    output y = pop(p), x = pop(p);
    push(p, x / y);
    return true;
}

int main() {
    char in[100];
    printf("Type a sum: ");
    char *r = fgets(in, 100, stdin);
    if (r == NULL) printf("Can't read stdin\n");
    parser *p = newParser(strlen(in), in);
    bool ok = Psum(p);
    if (ok) printf("%d\n", pop(p));
    else report(p, "Syntax error:\n", "Error: expecting %s, %s\n", names);
    freeParser(p);
}

// -----------------------------------------------------------------------------
// Forward declarations of supporting constants, structures and functions.

// Unicode category codes, in the order used in the lookup tables.
enum category {
    Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
    Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf
};

// Small support functions.
static bool DO(parser *p);
static bool OR(parser *p);
static bool ALT(parser *p, bool b);
static bool OPT(parser *p, bool b);
static bool HAS(parser *p, bool b);
static bool NOT(parser *p, bool b);
static bool TRY(parser *p, bool b);
static bool MARK(parser *p, int m);
static bool CAT(parser *p, int c);
static bool CHAR(parser *p, int ch);
static bool RANGE(parser *p, int low, int high);
static bool STRING(parser *p, char *s);
static bool SET(parser *p, char *s);
static bool DROP(parser *p, int n);
static bool END(parser *p);

// The parsing functions themselves, as generated by Pecan. The attributes of
// the pecan tag are the patterns to be used to generate the functions.
/* <pecan
    comment  = "// %s"
    declare  = "bool P%s(parser *p) {"
    body     = "    return %s;"
    close    = "}"
    compact  = "bool P%s(parser *p) { return %s; }"
    call     = "P%s(p)"
    true     = "true"
    false    = "false"
    or       = "||"
    and      = "&&"
    char     = "'%c'"
    string   = '"%s"'
    bmp      = "\u%4x"
    unicode  = "\U%8x"
    act0     = "%s(p%.0s%.0s%.0s)"
    act2     = "%s(p%.0s%.0s%.0s)"
    tag      = "tag(p, %n%d)"
    do       = "DO(p)"
> */

// sum = gap expression end
bool Psum(parser *p) { return Pgap(p) && Pexpression(p) && Pend(p); }

// expression1 = (plus term @2add / minus term @2subtract expression1)?
bool Pexpression1(parser *p) {
    return ((ALT(p,
        (DO(p) && Pplus(p) && Pterm(p) && add(p)) ||
        (OR(p) && Pminus(p) && Pterm(p) && subtract(p))
    ) && Pexpression1(p)) || true);
}

// expression = term (plus term @2add / minus term @2subtract)*
bool Pexpression(parser *p) { return Pterm(p) && Pexpression1(p); }

// term1 = (times atom @2multiply / over atom @2divide term1)?
bool Pterm1(parser *p) {
    return ((ALT(p,
        (DO(p) && Ptimes(p) && Patom(p) && multiply(p)) ||
        (OR(p) && Pover(p) && Patom(p) && divide(p))
    ) && Pterm1(p)) || true);
}

// term = atom (times atom @2multiply / over atom @2divide)*
bool Pterm(parser *p) { return Patom(p) && Pterm1(p); }

// atom = number / open expression close
bool Patom(parser *p) {
    return Pnumber(p) || (Popen(p) && Pexpression(p) && Pclose(p));
}

// number1 = (digit) number1?
bool Pnumber1(parser *p) { return Pdigit(p) && (Pnumber1(p) || true); }

// number = digit+ @number gap
bool Pnumber(parser *p) { return Pnumber1(p) && number(p) && Pgap(p); }

// plus = #operator '+' gap
bool Pplus(parser *p) { return MARK(p,operator) && CHAR(p,'+') && Pgap(p); }

// minus = #operator '-' gap
bool Pminus(parser *p) { return MARK(p,operator) && CHAR(p,'-') && Pgap(p); }

// times = #operator '*' gap
bool Ptimes(parser *p) { return MARK(p,operator) && CHAR(p,'*') && Pgap(p); }

// over = #operator '/' gap
bool Pover(parser *p) { return MARK(p,operator) && CHAR(p,'/') && Pgap(p); }

// open = #bracket '(' gap
bool Popen(parser *p) { return MARK(p,bracket) && CHAR(p,'(') && Pgap(p); }

// close = #bracket ')' gap
bool Pclose(parser *p) { return MARK(p,bracket) && CHAR(p,')') && Pgap(p); }

// digit = #digit '0..9'
bool Pdigit(parser *p) { return MARK(p,digit) && RANGE(p,'0','9'); }

// gap1 = (' ' gap1)?
bool Pgap1(parser *p) { return (CHAR(p,' ') && Pgap1(p)) || true; }

// gap = (' ')* @
bool Pgap(parser *p) { return Pgap1(p) && DROP(p,0); }

// end = #newline 13? 10 @
bool Pend(parser *p) {
    return MARK(p,newline) && (CHAR(p,13) || true) && CHAR(p,10) && DROP(p,0);
}

// </pecan>

// Structure to hold input, stack of outputs, saved input positions, depth of
// lookahead, and error marking info, during parsing.
struct parser {
    int in, start, end; input *ins;
    int out, nouts; output *outs;
    int save, nsaves; int *saves;
    int look, marked; long markers;
};

// Create new parser object from given input.
parser *newParser(int n, input ins[n]) {
    parser *p = malloc(sizeof(parser));
    *p = (parser) {
        .in = 0, .start = 0, .end = n, .ins = ins,
        .out = 0, .nouts = 8, .outs = malloc(8 * sizeof(output)),
        .save = 0, .nsaves = 8, .saves = malloc(8 * sizeof(int)),
        .look = 0, .marked = 0, .markers = 0
    };
    return p;
}

// Free parser and its data.
void freeParser(parser *p) {
    free(p->outs);
    free(p->saves);
    free(p);
}

// Return a pointer to the text for an action.
extern inline input *start(parser *p) {
    return &p->ins[p->start];
}

// Return the length of the text for an action.
extern inline int length(parser *p) {
    return p->in - p->start;
}

// Push output item onto stack.
extern inline void push(parser *p, output n) {
    if (p->out >= p->nouts) {
        p->nouts = p->nouts * 2;
        p->outs = realloc(p->outs, p->nouts);
    }
    p->outs[p->out++] = n;
}

// Pop output item.
extern inline int pop(parser *p) {
    return p->outs[--p->out];
}

// Push saved input position onto stack.
static inline void save(parser *p, int n) {
    if (p->save >= p->nsaves) {
        p->nsaves = p->nsaves * 2;
        p->saves = realloc(p->saves, p->nsaves);
    }
    p->saves[p->save++] = n;
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
typedef unsigned char byte;
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
static inline bool DO(parser *p) {
    save(p, p->in);
    return true;
}

// Check whether a previous failed alternative has progressed.
static inline bool OR(parser *p) {
    return p->in == p->saves[p->save-1];
}

// Pop a saved position, and return the result of a choice.
static inline bool ALT(parser *p, bool b) {
    unsave(p);
    return b;
}

// After parsing an optional item, pop saved position, and adjust the result.
static inline bool OPT(parser *p, bool b) {
    return b || p->in == unsave(p);
}

// Backtrack to saved position, and return result of lookahead.
static inline bool HAS(parser *p, bool b) {
    p->in = unsave(p);
    return b;
}

// Backtrack to saved position and negate result of lookahead.
static inline bool NOT(parser *p, bool b) {
    p->in = unsave(p);
    return ! b;
}

// Backtrack on failure.
static inline bool TRY(parser *p, bool b) {
    int n = unsave(p);
    if (! b) p->in = n;
    return b;
}

// Record an error marker for the current input position.
static inline bool MARK(parser *p, int m) {
    if (p->look > 0) return true;
    if (p->marked < p->in) {
        p->markers = 0L;
        p->marked = p->in;
    }
    p->markers = p->markers | (1L << m);
    return true;
}

// Check if next character is in given category.
static inline bool CAT(parser *p, int c) {
    if (p->in >= p->end) return false;
    if (c == Uc) return true;
    if (table1 == NULL) readTables();
    uchar u = getUTF8(&p->ins[p->in]);
    int cat = table2[table1[u.code>>8]*256+(u.code&255)];
    bool ok = cat == c;
    if (ok) p->in += u.len;
    return ok;
}

// Check if a (Unicode) character is next in the input.
static inline bool CHAR(parser *p, int ch) {
    if (p->in >= p->end) return false;
    uchar u = getUTF8(&p->ins[p->in]);
    if (u.code != ch) return false;
    p->in = p->in + u.len;
    return true;
}

// Check if a (UTF-8) character in a given range appears next in the input.
static inline bool RANGE(parser *p, int first, int last) {
    if (p->in >= p->end) return false;
    uchar u = getUTF8(&p->ins[p->in]);
    if (u.code < first || u.code > last) return false;
    p->in = p->in + u.len;
    return true;
}

// Check for the given (UTF-8) string next in the input.
// Handles UTF-8, working byte-by-byte.
static inline bool STRING(parser *p, char *s) {
    int i;
    for (i = 0; s[i] != '\0'; i++) {
        if (p->in >= p->end || p->ins[p->in + i] != s[i]) return false;
    }
    p->in += i;
    return true;
}

// Check if one of the characters in a (UTF-8) set is next in the input.
static inline bool SET(parser *p, char *s) {
    if (p->in >= p->end) return false;
    bool ok = false;
    uchar u1, u2;
    for (int i = 0; s[i] != '\0' && ! ok; ) {
        u1 = getUTF8(&s[i]);
        u2 = getUTF8(&p->ins[p->in]);
        if (u1.code == u2.code) ok = true;
        i = i + u1.len;
    }
    if (ok) p->in = p->in + u2.len;
    return ok;
}

// Drop the text matched recently, and drop n output items.
static inline bool DROP(parser *p, int n) {
    p->start = p->in;
    p->out = p->out - n;
    return true;
}

// Match end of input.
static inline bool END(parser *p) {
    return p->in >= p->end;
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

static void reportLine(char *in, lineInfo *li) {
    fprintf(stderr, "%.*s\n", li->end - li->start, in + li->start);
}

static void reportColumn(lineInfo *li) {
    for (int i = 0; i < li->column; i++) fprintf(stderr, " ");
    fprintf(stderr, "^\n");
}

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
