// Parsing template in C. Public domain.
#include <stdio.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

// Change this section for different applications.

// The input type should be char for a scanner or text-based parser, otherwise
// it should be a token structure or token pointer.
typedef char input;
typedef int output;

// Action: create a number from given text.
output value(int n, char s[n]) {
  output x = 0;
  for (int i = 0; i < n; i++) x = x * 10 + s[i] - '0';
  return x;
}

// Actions: arithmetic operations.
output add(output x, output y) { return x + y; }
output subtract(output x, output y) { return x - y; }
output multiply(output x, output y) { return x * y; }
output divide(output x, output y) { return x / y; }

// Error marker constants, and spellings.
enum marker { integer, operator, bracket, newline };
char *names[] = { "integer", "operator", "bracket", "newline" };

// Forward declarations of parser structure and functions.
struct parser;
typedef struct parser parser;
parser *newParser(int n, input[n]);
void freeParser(parser *p);
output answer(parser *p);
void report(parser *p, char *ds, char *f, char *names[]);
static input *start(parser *p);
static int length(parser *p);
static output top(parser *p, int n);
static bool act(parser *p, int n, output x);
static bool go(parser *p);
static bool ok(parser *p);
static bool alt(parser *p, bool b);
static bool opt(parser *p, bool b);
static bool has(parser *p, bool b);
static bool not(parser *p, bool b);
static bool see(parser *p, bool b);
static bool mark(parser *p, int m);
static bool cat(parser *p, int c);
static bool range(parser *p, int low, int high);
static bool string(parser *p, char *s);
static bool set(parser *p, char *s);
static bool drop(parser *p, int n);
static bool eot(parser *p);

// Unicode category codes, in the order used in the lookup tables.
enum category {
  Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
  Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf
};

// The parsing functions, compiled from a Pecan grammar. The attributes of this
// pecan tag are the print patterns used to generate the functions.
//
// <pecan
//   comment  = "// %s"
//   declare  = "bool %s(parser *p);"
//   define   = "bool %s(parser *p) { %n return %r; %n}"
//   call     = "%s(p)"
//   act0     = "act(p,0,%s(length(p),start(p)))"
//   act2     = "act(p,2,%s(top(p,1),top(p,0)))"
//   escape1  = "\%3o"
//   escape2  = "\u%4x"
//   escape4  = "\U%8x"
// >

bool sum(parser *p);
bool expression(parser *p);
bool expression1(parser *p);
bool term(parser *p);
bool term1(parser *p);
bool atom(parser *p);
bool number(parser *p);
bool number1(parser *p);
bool plus(parser *p);
bool minus(parser *p);
bool times(parser *p);
bool over(parser *p);
bool open(parser *p);
bool close(parser *p);
bool digit(parser *p);
bool gap(parser *p);
bool gap1(parser *p);
bool end(parser *p);

// sum = gap expression end
bool sum(parser *p) { return gap(p) && expression(p) && end(p); }

// expression = term (plus term @2add / minus term @2subtract)*
bool expression(parser *p) { return term(p) && expression1(p); }

// expression1 = ((plus term @2add / minus term @2subtract) expression1)?
bool expression1(parser *p) {
  return opt(p,
    go(p) && (alt(p,
      (go(p) && plus(p) && term(p) && act(p,2,add(top(p,1),top(p,0)))) ||
      (ok(p) && minus(p) && term(p) && act(p,2,subtract(top(p,1),top(p,0))))
    )) && expression1(p)
  );
}

// term = atom (times atom @2multiply / over atom @2divide)*
bool term(parser *p) { return atom(p) && term1(p); }

// term1 = ((times atom @2multiply / over atom @2divide) term1)?
bool term1(parser *p) {
  return opt(p,
    go(p) && (alt(p,
      (go(p) && times(p) && atom(p) && act(p,2,multiply(top(p,1),top(p,0)))) ||
      (ok(p) && over(p) && atom(p) && act(p,2,divide(top(p,1),top(p,0))))
    )) && term1(p)
  );
}

// atom = number / open expression close
bool atom(parser *p) {
  return number(p) || (open(p) && expression(p) && close(p));
}

// number = #integer digit+ @value gap
bool number(parser *p) {
  return mark(p,integer) && number1(p) && act(p,0,value(length(p),start(p))) &&
  gap(p);
}

// number1 = digit number1?
bool number1(parser *p) { return digit(p) && (number1(p) || true); }

// plus = #operator '+' gap
bool plus(parser *p) { return mark(p,operator) && string(p,"+") && gap(p); }

// minus = #operator '-' gap
bool minus(parser *p) { return mark(p,operator) && string(p,"-") && gap(p); }

// times = #operator '*' gap
bool times(parser *p) { return mark(p,operator) && string(p,"*") && gap(p); }

// over = #operator '/' gap
bool over(parser *p) { return mark(p,operator) && string(p,"/") && gap(p); }

// open = #bracket '(' gap
bool open(parser *p) { return mark(p,bracket) && string(p,"(") && gap(p); }

// close = #bracket ')' gap
bool close(parser *p) { return mark(p,bracket) && string(p,")") && gap(p); }

// digit = '0..9'
bool digit(parser *p) { return range(p,'0','9'); }

// gap = (' ')* @
bool gap(parser *p) { return gap1(p) && drop(p,0); }

// gap1 = (' ' gap1)?
bool gap1(parser *p) { return ((string(p," ") && gap1(p)) || true); }

// end = #newline '\r'? '\n' @
bool end(parser *p) {
  return mark(p,newline) && (string(p,"\015") || true) && string(p,"\012") &&
  drop(p,0);
}

// </pecan>. End of generated functions.

// Read in a sum and evaluate it.
int main() {
  char in[100];
  printf("Type a sum: ");
  char *r = fgets(in, 100, stdin);
  if (r == NULL) printf("Can't read stdin\n");
  parser *p = newParser(strlen(in), in);
  bool ok = sum(p);
  if (ok) printf("%d\n", answer(p));
  else report(p, "Syntax error:\n", "Error: expecting %s, %s\n", names);
  freeParser(p);
}

// ----- Parsing support ------------------------------------------------------

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
    .look = 0, .marked = 0, .markers = 0L
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

// Push output item onto stack, after popping n items. Discard characters.
static inline bool act(parser *p, int n, output x) {
  if (n == 0 && p->out >= p->nouts) {
    p->nouts = p->nouts * 2;
    p->outs = realloc(p->outs, p->nouts);
  }
  p->out = p->out - n;
  p->outs[p->out++] = x;
  p->start = p->in;
  return true;
}

// Return n'th top output item.
static inline int top(parser *p, int n) {
  return p->outs[p->out-1-n];
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
static inline bool go(parser *p) {
  save(p, p->in);
  return true;
}

// Check whether a previous failed alternative has progressed.
static inline bool ok(parser *p) {
  return p->in == p->saves[p->save-1];
}

// Pop a saved position, and return the result of a choice.
static inline bool alt(parser *p, bool b) {
  unsave(p);
  return b;
}

// After parsing an optional item, pop saved position, and adjust the result.
static inline bool opt(parser *p, bool b) {
  return b || p->in == unsave(p);
}

// Backtrack to saved position, and return result of lookahead.
static inline bool has(parser *p, bool b) {
  p->in = unsave(p);
  return b;
}

// Backtrack to saved position and negate result of lookahead.
static inline bool not(parser *p, bool b) {
  p->in = unsave(p);
  return ! b;
}

// Backtrack on failure.
static inline bool see(parser *p, bool b) {
  int n = unsave(p);
  if (! b) p->in = n;
  return b;
}

// Record an error marker for the current input position.
static inline bool mark(parser *p, int m) {
  if (p->look > 0) return true;
  if (p->marked < p->in) {
    p->markers = 0L;
    p->marked = p->in;
  }
  p->markers = p->markers | (1L << m);
  return true;
}

// Check if next character is in given category.
static inline bool cat(parser *p, int c) {
  if (p->in >= p->end) return false;
  if (c == Uc) return true;
  if (table1 == NULL) readTables();
  uchar u = getUTF8(&p->ins[p->in]);
  int cat = table2[table1[u.code>>8]*256+(u.code&255)];
  bool ok = cat == c;
  if (ok) p->in += u.len;
  return ok;
}

// Check if a (UTF-8) character in a given range appears next in the input.
static inline bool range(parser *p, int first, int last) {
  if (p->in >= p->end) return false;
  uchar u = getUTF8(&p->ins[p->in]);
  if (u.code < first || u.code > last) return false;
  p->in = p->in + u.len;
  return true;
}

// Check for the given (UTF-8) string next in the input.
// Handles UTF-8, working byte-by-byte.
static inline bool string(parser *p, char *s) {
  int i;
  for (i = 0; s[i] != '\0'; i++) {
    if (p->in >= p->end || p->ins[p->in + i] != s[i]) return false;
  }
  p->in += i;
  return true;
}

// Check if one of the characters in a (UTF-8) set is next in the input.
static inline bool set(parser *p, char *s) {
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
static inline bool drop(parser *p, int n) {
  p->start = p->in;
  p->out = p->out - n;
  return true;
}

// Match end of input.
static inline bool eot(parser *p) {
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
