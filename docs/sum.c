#include <stdio.h>

// Action constants.
enum action { number, add, subtract, multiply, divide };

// Error marker constants, and spellings.
enum marker { digit, operator, bracket, newline };
char *names[] = { "digit", "operator", "bracket", "newline" };

// Structure to hold input, stack of output numbers, saved input positions, and
// depth of lookahead, during calculations.
struct state {
    int in, end; char *input;
    int out; int output[100];
    int save; int saves[100];
    int look, marked, markers;
};
typedef struct state state;

// Output stack and save stack operations.
static inline void push(state *s, int n) { s->output[s->out++] = n; }
static inline int pop(state *s) { return s->output[--s->out]; }
static inline void save(state *s int n) { s->saves[s->save++] = n; }
static inline int unsave(state *s) { return s->saves[--s->save]; }

// Carry out a 'number' action with text as argument.
static inline void ACT(state *s, int p, int n) {
    int x = 0;
    for (int i = 0; i < n; i++) x = x * 10 + (s->input[p + i] - '0');
    push(s, x);
}

// Carry out an arithmetic operation.
static inline void ACT2(state *s, int a) {
    int x, y;
    switch (a) {
        case add: y = pop(s); x = pop(s); push(s, x + y); break;
        case subtract: y = pop(s); x = pop(s); push(s, x - y); break;
        case multiply: y = pop(s); x = pop(s); push(s, x * y); break;
        case divide: y = pop(s); x = pop(s); push(s, x / y); break;
    }
}

// Support functions.
static inline bool DO(state *s) { save(s, s->in); return true; }
static inline bool OR(state *s) { return s->in == unsave(s); }
static inline bool ALT(state *s, bool b) { unsave(s); return b; }
static inline bool OPT(state *s, bool b) { return b || s->in == unsave(s); }
static inline bool HAS(state *s, bool b) { s->in = unsave(s); return b; }
static inline bool NOT(state *s, bool b) { s->in = unsave(s); return !b; }

static inline bool TRY(state *s, bool b) {
    int n = unsave(s);
    if (! b) s->in = n;
    return b;
}

static inline bool MARK(state *s, int m) {
    if (s->look > 0) return true;
    if (s->marked < s->in) {
        s->markers = 0;
        s->marked = s->in;
    }
    s->markers = s->markers | (1 << m);
    return true;
}

// Check if a character (ascii) appears next in the input.
static inline bool CHAR(state *s, char ch) {
    if (s->in >= s->end) return false;
    if (s->input[s->in] != ch) return false;
    s->in++;
    return true;
}

// Check if a character (ascii) in a given range appears next in the input.
static inline bool RANGE(state *s, char first, char last) {
    if (s->in >= s->end) return false;
    if (s->input[in] < first || s->input[in] > last) return false;
    s->in++;
    return true;
}

// Check for the given (ascii) string next in the input.
static inline bool STRING(state *s, char *t) {
    int i;
    for (i = 0; t[i] != '\0'; i++) {
        if (s->input[s->in + i] != t[i]) return false;
    }
    s->in += i;
    return true;
}

// Check if a character (ascii) in a given range appears next in the input.
static bool SET(state *s, char *t) {
    char ch = s->input[in];
    if (ch == '\0') return false;
    bool found = false;
    for (int i = 0; t[i] != '\0' && ! found; i++) {
        if (ch == t[i]) found = true;
    }
    if (found) s->in++;
    return found;
}

static bool END() {
    return s->input[s->in] == '\0';
}

<pecan>
</pecan>

int main() {
    char in[100];
    printf("Type a sum: ");
    char *out = fgets(in, 100, stdin);
    if (out == NULL) printf("Can't read stdin\n");
    state sData;
    state *s = &sData;
    result rData;
    result *r = &rData;
    new(s, in);
    parseText(code, in, act, s, r);
    if (r->ok) printf("%d\n", pop(s));
    else report(in, r, "Syntax error:\n", "Error: expecting %s, %s\n", names);
}
