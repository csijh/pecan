// Interpreter header.
#include <stdbool.h>
#include <stdint.h>

// The type of code bytes and UTF-8 input bytes.
typedef unsigned char byte;

// The opcodes: first those that take no argument or have an implicit argument
// of 1, then from O1 those that take a one-byte operand 0..255, and then from
// O2 those that take a two-byte big-endian argument 0..65535,
enum op {
    STOP, OR, AND, MAYBE, ONE, MANY, DO, LOOK, TRY, HAS, NOT, DROP,
    STRING1, LOW1, HIGH1, LESS1, SET1,
    O1, START = O1,  GO,  BACK,  EITHER,  BOTH,  STRING,  LOW,  HIGH,  LESS,
    SET, ACT, MARK, CAT, TAG,
    O2, GOL = O2, BACKL,
};

// The type of a function to perform an output action. It is passed the output
// state, the action code, and the characters most recently matched. It returns
// the possibly updated output state.
typedef void doAct(void *state, int a, int n, char s[n]);

// The type of a function to get the tag of the next token.
typedef int doNext(void *state);

// The type of an error report. If ok is true, 'at' holds how far parsing
// reached. Otherwise, it is where the error occurred, with its line
// number and column, the start and end positions of the line, and the error
// items marked at that position.
struct err {
    char *input;
    bool ok;
    int at, line, column, start, end;
    uint64_t markers;
};
typedef struct err err;

// Use the n'th entry point in the code to parse. For text, the in array is
// parsed and f can be NULL. For tokens, f is used to scan the tokens, and in
// can be NULL. The function g is used to carry out actions. The argument x is
// passed to f or g. The result is NULL or an error report. There is no
// automatic recovery.
void parse(int n, byte code[], byte in[], doNext *f, doAct *g, void *x, err *e);

// Print a report on stderr using s if there are no markers, or s1 followed by
// a list of markers separated by s2 followed by s3, using the names array.
// Print the line containing the error on stderr.
// Print spaces followed by a ^ character to report the error column on stderr.
void report(err *e, char *s, char *s1, char *s2, char *s3, char *names[]);
