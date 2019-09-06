// Pecan 1.0 bytecode interpreter in C. Free and open source. See licence.txt.
#include <stdbool.h>
#include <stdint.h>

// The type of code bytes and UTF-8 input bytes.
typedef unsigned char byte;

// The opcodes: first those that take no argument or have an implicit argument
// of 1, then from OP1 those that take a one-byte operand 0..255, and then from
// OP2 those that take a two-byte big-endian argument 0..65535,
enum op {
    STOP, OR, AND, MAYBE, ONE, MANY, DO, LOOK, TRY, HAS, NOT, DROP,
    STRING1, LOW1, HIGH1, LESS1, SET1,
    START,  GO,  BACK,  EITHER,  BOTH,  STRING,  LOW,  HIGH,  LESS,
    SET, ACT, MARK, CAT, TAG,
    GOL, BACKL,
    OP1 = START, OP2 = GOL
};

// Unicode category codes, in the same order as Java, using unused 17 for Uc.
enum category {
    Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
    Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf
};

// The type of functions to perform output actions. They take the state, the
// action code, and the position and number of items most recently matched.
typedef void doAct(void *state, int a, int p, int n);

// The type of a function to get the tag of a token.
typedef int doTag(void *token);

// The type of a parsing result. If ok is true, 'at' holds how far parsing
// reached. Otherwise, it is where the error occurred, with its line number and
// column, the start and end positions of the line, and the error items marked
// at that position.
struct result {
    char *input;
    bool ok;
    int at, line, column, start, end;
    uint64_t markers;
};
typedef struct result result;

// Parse character input according to the provided bytecode. Use function f to
// carry out actions, passing it x as an argument. Fill in the given result
// structure. There is no automatic recovery.
void parseC(byte code[], byte in[], doAct *f, void *x, result *r);

// Parse tokens according to the provided bytecode. Use function g to find the
// tags of tokens, and f to carry out actions, passing x as an argument to
// either function. Fill in the result structure provided.
void parseT(byte code[], void *in[], doTag *g, doAct *f, void *x, result *r);

// Print a report on stderr using s if there are no markers, or s1 followed by
// a list of markers separated by s2 followed by s3, using the names array.
// Print the line containing the error on stderr.
// Print spaces followed by a ^ character to report the error column on stderr.
void report(result *e, char *s, char *s1, char *s2, char *s3, char *names[]);
