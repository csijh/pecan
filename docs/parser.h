// Interpreter header.
#include <stdbool.h>
#include <stdint.h>

// The type of code bytes and UTF-8 input bytes.
typedef unsigned char byte;

// The opcodes: first those that take no argument, then those that take an
// implicit argument of 1, then variations that take a one-byte arument 0..255
// and then variations that take a two-byte big-endian argument 0..65535.
enum op {
    STOP, OR, AND, MAYBE, ONE, MANY, DO, LOOK, TRY, HAS, NOT, DROP, MARK,
    START, GO, BACK, EITHER, BOTH, ACT, STRING, LOW, HIGH, LESS, SET, CAT, TAG,
    START1,GO1,BACK1,EITHER1,BOTH1,ACT1,STRING1,LOW1,HIGH1,LESS1,SET1,CAT1,TAG1,
    START2,GO2,BACK2,EITHER2,BOTH2,ACT2,STRING2,LOW2,HIGH2,LESS2,SET2,CAT2,TAG2,
};

// The type of a function to perform an output action, and of a function to get
// the tag of the next token.
typedef void doAct(int a, int start, int in, void *state);
typedef int doNext(void *state);

// The type of an error report.
struct err {
    bool ok;
    int line, column, start, end;
    uint64_t markers;
};
typedef struct err err;

// Use the n'th entry point in the code to parse the input bytes, calling
// f(arg) to perform each action. The result is NULL or an error report.
// There is no automatic recovery.
void parseText(int n, byte code[], byte input[], doAct *f, void *arg, err *e);

// Use the n'th entry point in the code to parse tokens, obtaining the tag of
// the next token using g(arg) and calling f(arg) to perform actions.
void parseTokens(int n, byte code[], doNext *g, doAct *f, void *arg, err *e);

// Print a report on stderr using s if there are no markers, or s1 followed by
// a list of markers separated by s2 followed by s3, using the names array.
void report(err *e, char *s, char *s1, char *s2, char *s3, char *names[]);

// Print the line containing the error on stderr.
void reportLine(err *e, char *input);

// Print spaces followed by a ^ character to report the error column on stderr.
void reportColumn(err *e);
