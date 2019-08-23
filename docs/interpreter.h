// Interpreter header.
#include <stdbool.h>
#include <stdint.h>

typedef unsigned char byte;
typedef uint64_t uint64;

// The opcodes.
enum op {
    STOP, OR, AND, MAYBE, ONE, MANY, DO, LOOK, TRY, HAS, NOT,
    START, GO, BACK, EITHER, BOTH, CHAR, SET, STRING, LOW, HIGH, LESS, CAT,
    TAG, MARK, ACT,
    START2, GO2, BACK2, EITHER2, BOTH2
};

// The type of a function to perform output actions.
typedef void doAction(int a, int start, int in, void *state);

// The generic text parser.
uint64 parseText(byte code[], byte input[], doAction *act, void *state);
