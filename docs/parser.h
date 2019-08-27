// Interpreter header.
#include <stdbool.h>
#include <stdint.h>

// The type of code bytes and UTF8 input bytes, and of error bitmaps.
typedef unsigned char byte;
typedef uint64_t bits;

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
typedef void doAction(int a, int start, int in, void *state);
typedef int doNext(void *state);

// Use the n'th entry point in the code to parse the input bytes, calling
// act(state) to perform each action. The result is 0L for success, or a bitmap
// of expected item markers for a failure. There is no automatic recovery.
bits parseText(int n, byte code[], byte input[], doAction *act, void *state);


// Use the n'th entry point in the code to parse tokens, obtaining the tag of
// the next token using next(state) and calling act(state) to perform actions.
bits parseTokens(int n, byte code[], doNext *next, doAction *act, void *state);

// Report a parse error.
