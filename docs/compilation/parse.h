// Parser support in C. Public domain.
#include <stdbool.h>
#include <stdint.h>

// Instead of being fully generic, a small range of possibilities is provided,
// according to which functions are called. The input should be char[], in which
// case text matching functions are called, or token[], in which case the tag
// function is called. Output items can be int (I), long (L), double (D),
// generic void* pointer (P), token (T) or node* pointer (N). Functions for
// handling output are topI, topL, ... and pushI, pushL, ... Note that pop()
// functions are not provided because in a call push(pop(),pop()) there would be
// no guarantee about the order in which the two pop calls are executed.

// Nodes are allocated in an array which is extended as necessary. That means
// pointers to nodes may change during parsing, and are valid only for the
// duration of one action. On the other hand, the overheads of an allocation per
// node are avoided, and relative child pointers of type int can be used.

// Many of the functions provided are defined as 'extern inline', so that
// cross-module optimisation can be used, e.g. using the -flto compiler flag.

// Token structure: tag, plus position and length of source text.
struct token { int tag, at, length; };
typedef struct token token;

// Node structure: tag, position and length of source text spanned by the node,
// and left/right children. A child 'pointer' is relative, for example set by
// p1->left = p2 - p1 or used by p2 = p1 + p1->left where p1, p2 point to nodes.
struct node { int tag, at, length, left, right; };
typedef struct node node;

// Unicode category codes, in the alphabetical order used in the lookup tables.
enum category {
    Cc, Cf, Cn, Co, Cs, Ll, Lm, Lo, Lt, Lu, Mc, Me, Mn, Nd, Nl, No, Pc, Pd,
    Pe, Pf, Pi, Po, Ps, Sc, Sk, Sm, So, Zl, Zp, Zs
};

// The opaque type of the parser state structure.
struct parser;
typedef struct parser parser;

// Create a new parser for char[] or token[]. In the token[] case, also pass in
// the original text, for error messages.
parser *newCharParser(int n, char input[n]);
parser *newTokenParser(int n, token input[n], int nc, char text[nc]);

// Free up a parser.
void freeParser(parser *p);

// Find the current position of recently matched items.
int at(parser *p);

// Return the current number of outputs.
int outputs(parser *p);

// After scanning, return an array of the tokens in the output stack. No further
// scanning can be done, and the caller is responsible for freeing the array.
token *getTokens(parser *p);

// Return the n'th item from the top of the stack.
int topI(parser *p, int n);
int64_t topL(parser *p, int n);
double topD(parser *p, int n);
void *topP(parser *p, int n);
token topT(parser *p, int n);
node *popN(parser *p, int n);

// Push an item onto the output stack, popping n previous output items and any
// recently matched input items.
bool pushI(parser *p, int n, int x);
bool pushL(parser *p, int n, int64_t x);
bool pushD(parser *p, int n, double x);
bool pushP(parser *p, int n, void *x);
bool pushT(parser *p, int n, token x);
bool pushN(parser *p, int n, node x);

// Find the line number at the current position within the text.
int lineNumber(parser *p);

// Print a parser error message to stderr. The format string s0 is used if
// there are no error markers. The format string s is used if there are markers,
// and it should contain two occurrences of %s. The separator text in between
// them is repeated as necessary. The last argument is an array of description
// strings indexed by error marker.
void report(parser *p, char *s0, char *s, char *names[]);

// Return the size of the most recently matched input items, in bytes.
int matched(parser *p);

// Return a pointer to the most recently matched input items.
void *match(parser *p);

// Save the current input position by pushing it onto a stack. Return true.
bool go(parser *p);

// Check if the current input position equals the most recent saved one.
bool ok(parser *p);

// Pop a saved position, and return b (the result of a choice).
bool alt(parser *p, bool b);

// Pop a saved position, change b to true if there has been no progress.
bool opt(parser *p, bool b);

// Pop a saved position and backtrack to it. Return b.
bool has(parser *p, bool b);

// Pop a saved position, backtrack to it, and return !b.
bool not(parser *p, bool b);

// Pop a saved position, and backtrack to it if b is false. Return b.
bool see(parser *p, bool b);

// Record an error marker for the current input position.
bool mark(parser *p, int m);

// Accept the next token if its tag nt is t.
bool tag(parser *p, int t, int nt);

// Accept any character.
bool point(parser *p);

// Accept the next character if it is in the given category.
bool cat(parser *p, int c);

// Accept the next character if it is in the given range.
bool range(parser *p, int low, int high);

// Accept characters if they match s.
bool string(parser *p, char *s);

// Accept a character if it is in s.
bool set(parser *p, char *s);

// Drop n items from the output stack, and discard recently matched items.
bool drop(parser *p, int n);

// Check whether the end of input has been reached.
bool eot(parser *p);
