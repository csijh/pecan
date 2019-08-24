// Interpreter header.
#include <stdbool.h>
#include <stdint.h>

// Change the TEXT flag here, or by setting it before including this header, to
// indicate that the parser handles text as well as tokens. Similarly, change
// the CATEGORIES flag to indicate that, if TEXT is true, the parser handles
// Unicode categories via the two-stage table in table1.bin and table2.bin.
#ifndef TEXT
#define TEXT true
#endif
#ifndef CATEGORIES
#define CATEGORIES true
#endif

// The type of code bytes and UTF8 input bytes, and of error bitmaps.
typedef unsigned char byte;
typedef uint64_t uint64;

// The opcodes.
enum op {
    START, START2, STOP, GO, GO2, BACK, BACK2,
    EITHER, EITHER2, OR, BOTH, BOTH2, AND,
    MAYBE, ONE, MANY, DO, LOOK, TRY, HAS, NOT, DROP, ACT, MARK,
    CHAR, CHARS, LOW, LOWS, HIGH, HIGHS, BELOW, BELOWS, SET,
    CAT, TAG
};

// The type of a function to perform output actions.
typedef void doAction(int a, int start, int in, void *state);

#ifdef TEXT
// The generic text parser.
uint64 parseText(byte code[], byte input[], doAction *act, void *state);
#endif

uint64 parseTokens(byte code[], byte input[], doAction *act, void *state);
