// Pecan 1.0 bytecode interpreter in C. Free and open source. See licence.txt.
#include <stdbool.h>
#include <stdint.h>

// The type of code bytes.
typedef unsigned char byte;

// The opcodes: first those that take no argument or have an implicit argument
// of 1, then from OP1 onwards those that take a one-byte operand 0..255, and
// then from OP2 those that take a two-byte big-endian argument 0..65535,
enum op {
    STOP, OR, AND, MAYBE, ONE, MANY, DO, LOOK, TRY, HAS, NOT, DROP, END,
    STRING1, LOW1, HIGH1, LESS1, SET1,
    OP1, START=OP1,  GO,  BACK,  EITHER,  BOTH,  STRING,  LOW,  HIGH,  LESS,
    SET, ACT, MARK, CAT, TAG,
    OP2, GOL=OP2, BACKL
};

// Unicode category codes, in the order used in the lookup tables.
enum category {
    Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
    Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf
};

// The type of functions to perform output actions. They take the state, the
// action code, and the position and number of items most recently matched.
typedef void doAct(void *state, int a, int p, int n);

// The type of a function to get the tag of the i'th token. The result is
// negative if there are no more tokens.
typedef int doTag(void *state, int i);

// The type of a parsing result. If ok is true, 'at' holds how far parsing
// reached. Otherwise, it is where the error occurred. In the case of token
// parsing, 'at' is the index in the token array.
struct result { bool ok; int at; uint64_t markers; };
typedef struct result result;

// Parse character input according to the provided bytecode. Use function f to
// carry out actions, passing it x as an argument. Fill in the given result
// structure. There is no automatic recovery. Since all offsets within bytecode
// sequences are relative, the first argument can be a pointer to an alternative
// rule in the grammar, other than the first.
void parseText(byte code[], char in[], doAct *f, void *x, result *r);

// Parse tokens according to the provided bytecode. Use function g to find the
// tags of tokens, and f to carry out actions, passing x as an argument to
// either function. Fill in the result structure provided.
void parseTokens(byte code[], doTag *g, doAct *f, void *x, result *r);

// Print a report on stderr using s0 if there are no markers, or s if there are,
// with s containing two copies of %s as an example print string for two
// markers. Print the line containing the error on stderr. Print spaces followed
// by a ^ character to report the error column on stderr.
void report(char *input, result *e, char *s0, char *s, char *names[]);
