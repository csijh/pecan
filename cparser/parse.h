/* A generic Pecan 4 parser in C.  Open source - see licence.txt.

Run the Pecan 4 bytecode interpreter.  See the file parse.c for more details. */

#include "unicode.h"
#include <stdbool.h>

typedef unsigned char byte;

// The type of the function which carries out actions; out is the stack, start
// and end mark the input items matched since the last action or discard.
// typedef void actor(int act, void *out, int start, int end);

// Structure used to report an error.
struct report {
    char *text;
    int lineNumber, lineStart, position, lineEnd;
    int expects;
    int expecting[];
};
typedef struct report report;

// Carry out parsing:
// code:   the bytecode generated from a grammar by Pecan (or a rule in it)
// input:  the UTF-8 text, or token tag array with 0xFF sentinel
// out:    an object to be passed to the action handler
report *parse(byte *code, char *input, void *out);
