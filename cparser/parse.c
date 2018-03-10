/* A generic Pecan 4 parser in C.  Open source - see licence.txt.

For each specific language, this parser needs a driver program.  The Pecan
program generates bytecode and some supporting information from the grammar,
which can be used to generate the driver. This parser is the interpreter for the
bytecode, in the form of a parse function.

The input to the parse function is a UTF-8 string.  The terminating null
character ('\0') is never matched, even if explicitly mentioned in the grammar.
Alternatively, the input can be a byte array of token tags in the range 0..254,
with a 255 terminator.

A function pointer for handling actions is passed to the parser, along with an
output object.  The parser does nothing with the output object, except pass it
to the action handler.  The object is assumed to be a stack of output items,
perhaps numbers or tree nodes.  If each action has the same net effect on the
stack as labelled in the grammar, then stack handling is guaranteed to work
properly and to result in a single output item.

A flag can be passed to the parser to say that the language being parsed is
context sensitive.  In that case, the action function is called with action code
-1 just before each tag is accessed, to give the driving program a chance to
change the tag at that moment.

Only one result, a success or failure, is reported by the parser. If error
recovery is desired, the parser can be called again.  The input passed in can be
a pointer to the remainder of the text or tags, and the bytecode can be started
at a different recovery rule.

The result returned by the parse function is NULL for success, or a pointer to
an array of integers for a failure.  The first number in the array is the
position in the input at which the error is being reported.  The second is the
number of markers being reported, and the remainder are the markers, as
indicated in the grammar. */

#include "parse.h"
#include <stdio.h>
#include <stdlib.h>
#include <limits.h>
#include <assert.h>

#define TRACE

// The type of the function which carries out actions; out is the stack, start
// and end mark the input items matched since the last action or discard.
void act(int action, void *out, int start, int end);

enum opcode {
    STOP, RULE, GO, EITHER, OR, BOTH, AND, REPEAT, ONCE, MANY, LOOK, TRY, HAS,
    NOT, STRING, SET, RANGE, CAT, TAG, MARK, REPORT, DROP, ACT
};

char *names[] = {
    "STOP", "RULE", "GO", "EITHER", "OR", "BOTH", "AND", "REPEAT", "ONCE",
    "MANY", "LOOK", "TRY", "HAS", "NOT", "STRING", "SET", "RANGE", "CAT", "TAG",
    "MARK", "REPORT", "DROP", "ACT"
};

// state structure:
// pc, calls [size,top], fails [size,top], backtracks [size,top]

// Macros for readability: use with extreme caution.
#define PUSH(x) calls[callTop++] = x
#define POP() (calls[--callTop])
#define CALL(x) PUSH(pc); pc = x
#define RETURN() pc = calls[--callTop]

//static inline void PUSH(int x) { calls[callTop++] = x; }
//static inline int POP() { return calls[--callTop]; }
//static inline void CALL(int x) { PUSH(pc); pc = x; }
//static inline void RETURN() { pc = calls[--callTop]; }

// Carry out parsing:
// code:   the bytecode generated from a grammar by Pecan (or a rule within it)
// input:  the UTF-8 text or token tag array (with 0xFF sentinel);
// out:    an object to be passed to the output handler;
report *parse(byte *code, char *input, void *out) {
    // Create a local call stack and stack of failure markers.
    int pc = 0, callSize = 1024, callTop = 0, failSize = 32, failTop = 0;
    unsigned short *calls = malloc(callSize * sizeof(unsigned short));
    report *error = malloc(sizeof(report) + failSize * sizeof(int));
    int *fails = error->expecting;
    // Old and current positions in input, high water mark, success flag.
    int in = 0, start = 0, mark = 0, ok = true;
    // Temporary variables for use within cases.
    int arg, saveIn, length, ch;
    // Push a sentinel return address, pointing to a STOP opcode.  Execute.
    assert(code[0] == STOP);
    PUSH(0);
    if (pc == 0) pc = 1;
    while (true) {
#ifdef TRACE
    printf("%d: %s", pc, names[code[pc]]);
    int n;
    switch (code[pc]) {
        case RULE: case EITHER: case BOTH: case REPORT: case ACT: case GO:
            printf(" %d", (code[pc+1]<<8) + code[pc+2]);
            break;
        case AND:
            printf(" %d", ok);
            break;
        case REPEAT: case LOOK: case MARK:
            printf(" %s", names[code[pc+1]]);
            break;
        case STRING: case SET:
            n = code[pc+1];
            printf(" ");
            for (int i=0; i<n; i++) printf("%c", code[pc+2+i]);
            break;
        case RANGE:
            printf(" %c %c", code[pc+2], code[pc+4]);
            break;
        case CAT: case TAG:
            printf(" %d", code[pc+1]);
            break;
        default:
            break;
    }
    printf("\n");
#endif
    switch (code[pc++]) {

    // Report success or failure.  Normalize the fail markers.
    case STOP:
        free(calls);
        if (ok) {
            free(error);
            return NULL;
        }
        // Insertion sort.
        for (int i=0; i<failTop; i++) {
            int x = fails[i];
            int j = i - 1;
            while (j >= 0 && fails[j] > x) {
                fails[j+1] = fails[j];
                j--;
            }
            fails[j+1] = x;
        }
        // Get rid of duplicates.
        int j = 0;
        for (int i=0; i<failTop; i++) {
            if (j > 0 && fails[i] != fails[j-1]) fails[j++] = fails[i];
        }
        failTop = j;
        error->text = input;
        error->position = mark;
        error->expects = failTop;
        return error;

    // x = rhs    RULE frame rhs    check call stack big enough, do rhs
    case RULE:
        arg = (code[pc]<<8) + code[pc+1];   // frameSize
        pc += 2;
        while (callTop + arg > callSize) {
            callSize += 1024;
            calls = realloc(calls, callSize);
        }
        break;

    // x        GO &x               (identifier, when encoded directly)
    case GO:
        pc = (code[pc]<<8) + code[pc+1];
        break;

    // x / y    EITHER &x OR y x    save in, call x, return to OR
    case EITHER:
        arg = (code[pc]<<8) + code[pc+1];
        pc += 2;
        PUSH(in);
        CALL(arg);
        break;

    // x / y    check progress, continue with y
    case OR:
        saveIn = POP();
        if (ok || in > saveIn) RETURN();
        break;

    // x y    BOTH &x AND y x    call x, returning to AND
    case BOTH:
        arg = (code[pc]<<8) + code[pc+1];
        pc += 2;
        CALL(arg);
        break;

    // x y    if x succeeded, continue with y
    case AND:
        if (!ok) RETURN();
        break;

    // x? or x*    REPEAT ONCE/MANY x    call x, return to ONCE or MANY
    case REPEAT:
        PUSH(in);
        CALL(pc+1);
        break;

    // x?    check success or soft failure of x
    case ONCE:
        saveIn = POP();
        if (!ok && in == saveIn) ok = true;
        RETURN();
        break;

    // x*    check success of x, and re-call
    case MANY:
        saveIn = POP();
        if (ok) {
            // Go back to REPEAT
            pc = pc - 1;
            PUSH(in);
            CALL(pc + 1);
            break;
        }
        if (in == saveIn) ok = true;
        RETURN();
        break;

    // [x] or x& or x!    LOOK TRY/HAS/NOT x    prepare to lookahead
    case LOOK:
        // Save the input position, save the high water mark, set it high to
        // avoid error reporting during lookahead, and call x.
        PUSH(in);
        PUSH(mark);
        mark = INT_MAX;
        CALL(pc + 1);
        break;

    // [x]    possibly backtrack
    case TRY:
        mark = POP();
        saveIn = POP();
        if (! ok) in = saveIn;
        if (! ok && mark < in) mark = in;
        RETURN();
        break;

    // x&    backtrack
    case HAS:
        mark = POP();
        in = POP();
        if (! ok && mark < in) mark = in;
        RETURN();
        break;

    // x!    backtrack and invert
    case NOT:
        mark = POP();
        in = POP();
        ok = !ok;
        if (! ok && mark < in) mark = in;
        RETURN();
        break;

    // "abc"    match against input
    case STRING:
        length = code[pc++];
        ok = true;
        for (int i=0; i<length; i++) {
            if (input[in+i] != code[pc+i]) { ok = false; break; }
        }
        if (ok) in += length;
        else if (mark < in) mark = in;
        RETURN();
        break;

    // 'abc'    match against next character in input
    case SET:
        // Only ascii characters supported.
        length = code[pc++];
        ok = false;
        for (int i=0; i<length; i++) {
            if (input[in] == code[pc+i]) { ok = true; break; }
        }
        if (ok) in += 1;
        else if (mark < in) mark = in;
        RETURN();
        break;

    // x..y    check if the next character is between x and y
    case RANGE:
        ok = true;
        length = code[pc++];
        int diff = 0;
        for (int i=0; i<length; i++) {
            diff = input[in+i] - code[pc+i];
            if (diff != 0) break;
        }
        if (diff < 0) ok = false;
        else {
            pc += length;
            length = code[pc++];
            for (int i=0; i<length; i++) {
                diff = input[in+i] - code[pc+i];
                if (diff != 0) break;
            }
            if (diff > 0) ok = false;
        }
        if (ok) in += unilength(&input[in]);
        else if (mark < in) mark = in;
        RETURN();
        break;

    // Match character with given Unicode category
    case CAT:
        arg = code[pc++];
        if (arg == Uc) { ok = true; RETURN(); break; }
        ch = unichar(&input[in]);
        ok = (unitype(ch) == arg);
        if (ok) in += unilength(&input[in]);
        else if (mark < in) mark = in;
        RETURN();
        break;

    // %t or `t`    match a token tag
    case TAG:
//        if (cs) act(-1, out, in, in);
        arg = code[pc++];
        ok = (input[in] == arg);
        if (ok) in++;
        else if (mark < in) mark = in;
        RETURN();
        break;

    // x #m    MARK REPORT m x    call x, possibly reporting m
    case MARK:
        // For a new high water mark, discard previous errors
        if (mark < in) { mark = in; failTop = 0; }
        // If the high water mark is beyond here, no need to report
        if (mark > in) pc = pc + 3;
        else CALL(pc + 3);
        break;

    // e #m    if e has failed, report mark
    case REPORT:
        if (! ok) {
            // If e moved the mark forward, bring it back
            mark = in;
            if (failTop >= failSize) {
                failSize += 32;
                fails = realloc(fails, failSize);
            }
            arg = (code[pc]<<8) + code[pc+1];
            fails[failTop++] = arg;
        }
        RETURN();
        break;

    // @    discard input bytes since last action
    case DROP:
        start = in;
        ok = true;
        RETURN();
        break;

    // @x    call handler, pass x, out object, range of input bytes
    case ACT:
        arg = (code[pc]<<8) + code[pc+1];
        act(arg, out, start, in);
        start = in;
        ok = true;
        RETURN();
        break;

    default:
        fprintf(stderr, "Unrecognised opcode.\n");
        exit(1);
        break;
    }}
}
