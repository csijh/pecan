// Parser support in C. Public domain.
#include <stdbool.h>
#include <stdint.h>

// Provide a parser state and primitive parsing functions. Many of the functions
// are small and are defined as 'extern inline', so that cross-module
// optimisation can be used to inline them, e.g. using the -flto compiler flag.

// A simple scheme is provided for allocating objects using a single block which
// is reallocated as necessary. This avoids the time and memory overheads of
// individual allocations for each object. For tokens, the result is a compact
// array of token structures. For tree nodes, child pointers can't be used
// because the nodes move, but compact relative references work well.

// The opaque type of the parser state structure.
struct parser;
typedef struct parser parser;

// The type of a token, to use as output from scanning and input to token-based
// parsing. This structure can be changed, provided it contains a tag field and
// no pointers to other tokens.
struct token { int tag, start, length; };
typedef struct token token;

// Unicode category codes, in the alphabetical order used in the lookup tables.
enum category {
    Cc, Cf, Cn, Co, Cs, Ll, Lm, Lo, Lt, Lu, Mc, Me, Mn, Nd, Nl, No, Pc, Pd,
    Pe, Pf, Pi, Po, Ps, Sc, Sk, Sm, So, Zl, Zp, Zs
};

// Create a new text parser. Only text-matching functions should be called.
parser *newParser(int n, char text[n]);

// Create a new token parser. Only token-matching functions should be called.
// The tokens argument is an array of token structures, not pointers. If m is
// negative, then it is assumed that the end of the token array is marked with a
// sentinel token, and the eot function is not used.
parser *newTokenParser(int n, char text[n], int m, token tokens[m]);

// Free up a parser.
void freeParser(parser *p);

// The type of a function to get the tag of a token, for context sensitive
// parsing. It is only called if the original tag of a token is negative,
// indicating an unresolved tag, and it should return a non-negative tag value.
typedef int tagFunction(token *t, void *context);

// Enable context sensitive parsing, by defining a tag function and a context
// object to be passed to it.
void setContext(parser *p, tagFunction *tag, void *context);

// Get the length of the source text.
int lengthText(parser *p);

// Get the source text.
char *text(parser *p);

// Get the length of the input token array (as passed in, possibly negative).
int lengthTokens(parser *p);

// Get the input tokens.
token *getTokens(parser *p);

// Get the start of the recently matched characters or tokens.
int start(parser *p);

// Get the current position, at the end of the recently matched items.
int at(parser *p);

// Allocate memory for an object in the store. The size can be zero to find the
// address in the store where the next object will be allocated. Object pointers
// may change during parsing, so are only valid during a single action, and
// should only be pushed on the stack or used to form relative addresses, not
// stored anywhere.
void *new(parser *p, int size);

// Return the size of the object store in bytes.
int storeSize(parser *p);

// Get the object store, after parsing is complete. This does a final
// reallocation to remove spare space from the store, so this should be called
// before a final topR(p,0) is called to get the result object otherwise
// the pointer would be invalidated.
void *getStore(parser *p);

// Return the value n'th from the top of the stack. Use topR for objects
// allocated in the parser's store, which are only valid for the duration of an
// action. Use topP for objects allocated elsewhere. Note that a pop() function
// is not provided because in a call push(pop(),pop()) there is no guarantee
// about the order in which the two pop calls are executed.
int64_t topI(parser *p, int n);
double topD(parser *p, int n);
void *topP(parser *p, int n);
void *topR(parser *p, int n);

// Push a value onto the output stack, popping n previous output items and
// discarding any recently matched input characters or tokens. Use pushR for a a
// store object and pushP for an object allocated elsewhere.
bool pushI(parser *p, int n, int64_t x);
bool pushD(parser *p, int n, double x);
bool pushP(parser *p, int n, void *x);
bool pushR(parser *p, int n, void *x);

// Find the line number at the given position within the text.
int lineNumber(parser *p, int at);

// Print a message to stderr, pointing to a given position in the text and
// listing markers. The format string s0 is used if there are no error markers.
// Otherwise, the format string s is used. It should contain two occurrences of
// %s. The separator text in between them is repeated as necessary. The format
// strings should contain no other specifiers. For example, a filename and/or
// line number should be printed first, before calling this. The last argument
// is an array of description strings indexed by marker.
void report(parser *p, int at, char *s0, char *s, char *names[]);

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

// Accept the next token if its tag is t.
bool tag(parser *p, int t);

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
