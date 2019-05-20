#include <stdio.h>
#include <stdbool.h>
#include <assert.h>

typedef unsigned char byte;

// Opcodes before RULE have no argument. Other opcodes have a one-byte argument.
// An extended opcode EXTEND+OP has a two-byte, big-endian, unsigned argument.
enum op {
    STOP, OR, AND, MAYBE, OPT, MANY, DO, LOOK, TRY, HAS, NOT,
    RULE, START, GO, BACK, EITHER, BOTH, CHAR, SET, STRING, RANGE1, RANGE2, LT,
    CAT, TAG, MARK, ACT,
    EXTEND
};

// Action constants.
enum action { drop, number };

static byte code[] = {
    START, 8, RANGE1, 1, 48, RANGE2, 1, 57, ACT, number, STOP
};

// The parsing state, as a structure, allowing operations to be presented in
// separate functions. Inlining of the functions should eliminate any overhead.
struct state {
    byte *input;
    int *output, *stack;
    int pc, start, in, out, saveIn, saveOut;
    int ok, top, look, mark, markers, end;
};
typedef struct state state;

// Carry out delayed actions, which have been stored on the output stack,
// between saveOut and out. The drop action discards input characters between
// start and in, and the number actions turns them into an output integer.
static inline void doActions(state *s) {
    for (int i = s->saveOut; i < s->out; i++) {
        int n;
        switch (s->output[i]) {
            case drop:
                s->start = s->in;
                break;
            case number:
                n = 0;
                for (int i = s->start; i < s->in; i++) {
                    n = n * 10 + (s->input[s->start + i] - '0');
                }
                s->output[s->out++] = n;
                s->start = s->in;
                break;
        }
    }
}

// id = x: do nothing, continue with START. (RULE just labels an entry point.)
static inline void doRule(state *s, int arg) {
}

// id = x: entry point; continue with x returning to STOP.
static inline void doStart(state *s, int arg) {
    s->stack[s->top++] = s->pc + arg;
}

// id = x: after x, tidy up and return the result of parsing.
static inline void doStop(state *s, int arg) {
    if (s->ok && s->out > s->saveOut) doActions(s);
    // TODO report errors properly
    if (! s->ok) {
        if (s->in > s->mark) s->markers = 0;
        printf("Parsing failed\n");
        exit(1);
    }
    s->end = true;
}

// id: skip forwards in the code. (Tail-call a remote rule.)
static inline void doGo(state *s, int arg) {
    s->pc = s->pc + arg;
}

// id: skip backwards in the code, to tail-call a remote rule.
static inline void doBack(state *s, int arg) {
    s->pc = s->pc - arg;
}

// x / y: Save current state, call x, returning to OR.
static inline void doEither(state *s, int arg) {
    s->saveIn = s->in;
    s->saveOut = s->out;
    s->stack[s->top++] = s->pc + arg;
}

// x / y: after x, return or continue with y.
static inline void doOr(state *s, int arg) {
    if (s->ok || s->in > s->saveIn) s->pc = s->stack[--s->top];
    else s->out = s->saveOut;
}

// x y: continue with x, returning to AND.
static inline void doBoth(state *s, int arg) {
    s->stack[s->top++] = s->pc + arg;
}

// x y: after x, if it failed return, else continue with y.
static inline void doAnd(state *s, int arg) {
    if (! s->ok) s->pc = s->stack[--s->top];
}

// Parse an input string, producing an integer.
int parse(byte *input) {
    int output[1000], stack[1000];
    state sData = {
        .input = input, .output = output, .stack = stack,
        .pc = 0, .start = 0, .in = 0, .out = 0, .saveIn = 0, .saveOut = 0,
        .ok = 0, .top = 0, .look = 0, .mark = 0, .markers = 0, .end = 0
    };
    state *s = &sData;

    while(! s->end) {
        byte op = code[s->pc++];
        byte arg = 0;
        if (op >= RULE) arg = code[s->pc++];
        if (op >= EXTEND) {
            arg = (arg << 8) | code[s->pc++];
            op = op - EXTEND;
        }
        switch(op) {
            case RULE:      doRule(s, arg); break;
            case START:     doStart(s, arg); break;
            case STOP:      doStop(s, arg); break;
            case GO:        doGo(s, arg); break;
            case EITHER:    doEither(s, arg); break;
            case OR:        doOr(s, arg); break;
            case BOTH:      doBoth(s, arg); break;
            case AND:       doAnd(s, arg); break;
        }
    }
    return s->output[0];
}

/*
            // x?, x*: jump to x, returning to OPT or MANY.
            case MAYBE:
                saveIn = in;
                saveOut = out;
                stack[top++] = pc + arg;
                pc++;
                break;

            // x?: after x, check success and return.
            case OPT:
                if (!ok && in == saveIn) {
                    out = saveOut;
                    ok = true;
                }
                pc = stack[--top];
                break;

            // x*: after x, check success and re-try x or return.
            case MANY:
                if (ok) {
                    saveIn = in;
                    saveOut = out;
                }
                else {
                    if (!ok && in == saveIn) {
                        out = saveOut;
                        ok = true;
                    }
                    pc = stack[--top];
                }
                break;

            // x+: jump to x, returning to AND.  (DO AND MAYBE MANY <x>)
            case DO:
                stack[top++] = pc;
                pc = pc + 3;
                break;

            // [x], x&, x!: start a lookahead.
            case LOOK:
                saveIn = in;
                saveOut = out;
                look++;
                stack[top++] = pc;
                pc = pc + 1;
                break;

            // x&: after x, backtrack and report success.
            case HAS:
                look--;
                out = saveOut;
                in = saveIn;
                break;

            // x!: after x, backtrack and report inverted result.
            case NOT:
                look--;
                out = saveOut;
                in = saveIn;
                ok = ! ok;
                break;

            // [x]: succeed and perform actions, or fail and discard them.
            case TRY:
                look--;
                in = saveIn;
                if (ok && look == 0) doActions(output, saveOut, out);
                else out = saveOut;
                break;

            // 'a': recognise an ascii character.
            case CHAR:
                ok = (input[in] == arg);
                if (ok) {
                    if (look == 0 && out > saveOut) {
                        doActions(output, saveOut, out);
                    }
                    in++;
                }
                break;

            // 'abc': check for each character in a set.
            // TODO: support UTF-8
            case SET:
                ok = false;
                for (int i = 0; i < arg && !ok; i++) {
                    assert(input[in+i] < 128);
                    if (input[in+i] == code[pc+i]) ok = true;
                }
                if (ok) {
                    in++;
                    if (look == 0 && out > saveOut) {
                        doActions(output, saveOut, out);
                    }
                }
                break;

            // "abc": check for string and return
            case STRING:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch != code[pc+i]) ok = false;
                    if (ch == '\0') break;
                }
                if (ok) {
                    in = in + arg;
                    if (look == 0 && out > saveOut) doActions();
                }
                pc = stack[--top];
                break;

            // "a".."z": check >= "a", return or continue with RANGE2
            case RANGE1:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch < code[pc+i]) ok = false;
                    if (ch == '\0') break;
                }
                if (ok) pc = pc + arg;
                else pc = stack[--top];
                break;

            // "a".."z": check second half of range and return.
            case RANGE2:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch > code[pc+i]) ok = false;
                }
                if (ok) {
                    in = in + arg; // TODO check!
                    if (look == 0 && out > saveOut) doActions();
                }
                pc = stack[--top];
                break;

            // <a>: check if input < "a", return
            case LT:
                ok = true;
                for (int i = 0; i < arg; i++) {
                    byte ch = input[in+i];
                    if (ch == '\0') break;
                    if (ch >= code[pc+i]) ok = false;
                }
                pc = stack[--top];
                break;

            // Categories are not implemented and other tags are not expected.
            case CAT: case TAG: case ACT1: case ACT2: case ACT3: case ACTN:
                printf("Opcode %d not implemented\n", op);
                exit(1);
                break;

            // Record an error marker.
            case MARK:
                assert(arg < 32);
                if (look = 0) {
                    if (mark != in) {
                        mark = in;
                        markers = 0;
                    }
                    markers = markers | (1 << arg);
                }
                ok = true;
                pc = stack[--top];
                break;

            // Delay any action and return success.
            case ACT:
                output[out++] = arg;
                ok = true;
                pc = stack[--top];
                break;
        }
    }
}
*/
/*

int saveIn, saveOut, saveMark, length;
String text;
if (tracing && ! skipTrace) System.out.println(node.trace());
skipTrace = false;
switch(node.op()) {
case SOME:
    saveIn = in;
    saveOut = out;
    parse(node.left());
    if (!ok && in == saveIn) out = saveOut;
    if (!ok) return;
    saveIn = in;
    saveOut = out;
    while (ok) {
        saveIn = in;
        saveOut = out;
        parse(node.left());
    }
    if (!ok && in == saveIn) {
        out = saveOut;
        ok = true;
    }
    break;
case TRY:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    in = saveIn;
    if (ok && lookahead == 0) takeActions();
    if (ok) parse(node.left());
    else out = saveOut;
    break;
case HAS:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    out = saveOut;
    in = saveIn;
    break;
case NOT:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    out = saveOut;
    in = saveIn;
    ok = !ok;
    break;
case CHAR:
    if (in >= input.length()) ok = false;
    else {
        int ch = input.codePointAt(in);
        ok = (ch == node.value());
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case STRING:
    length = node.text().length() - 2;
    text = node.text().substring(1, length+1);
    ok = true;
    if (in + length > input.length()) ok = false;
    else for (int i=0; i<length; i++) {
        if (input.charAt(in+i) != text.charAt(i)) { ok = false; break; }
    }
    if (ok) {
        if (lookahead == 0 && out > 0) takeActions();
        in += length;
        if (tracing) traceInput();
    }
    break;
case SET:
    length = node.text().length() - 2;
    text = node.text().substring(1, length+1);
    ok = false;
    if (in >= input.length()) { }
    else for (int i=0; i<length; i++) {
        if (input.charAt(in) != text.charAt(i)) continue;
        if (lookahead == 0 && out > 0) takeActions();
        if (Character.isHighSurrogate(text.charAt(i))) {
            i++;
            if (input.charAt(in+1) != text.charAt(i)) continue;
            in++;
        }
        in++;
        if (tracing) traceInput();
        ok = true;
    }
    break;
case RANGE:
    int low = node.left().value();
    int high = node.right().value();
    ok = false;
    if (in < input.length()) {
        int ch = input.codePointAt(in);
        ok = (ch >= low) && (ch <= high);
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case CAT:
    ok = false;
    int cats = node.value();
    if (in < input.length()) {
        int ch = input.codePointAt(in);
        int bit = 1 << Character.getType(ch);
        ok = ((cats & bit) != 0);
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case TAG:
    String query;
    if (node.text().charAt(0) == '%') query = node.text().substring(1);
    else query = node.text().substring(1, node.text().length()-1);
    if (query.length() == 0) ok = in == input.length();
    else ok = input.startsWith(query, in);
    if (ok) {
        start = in;
        if (lookahead == 0 && out > 0) takeActions();
        in += query.length();
        while (in < input.length() &&
            (input.charAt(in) == ' ' || input.charAt(in) == '\n')) in++;
        if (tracing) traceInput();
    }
    break;
case MARK:
    ok = true;
    if (lookahead > 0) break;
    if (mark != in) { mark = in; failures.clear(); }
    failures.set(node.value());
    break;
case DROP:
    ok = true;
    delay[out++] = node;
    break;
case ACT:
    ok = true;
    if (lookahead > 0) break;
    delay[out++] = node;
    break;
*/
