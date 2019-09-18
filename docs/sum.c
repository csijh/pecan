#include "interpret.h"
#include <stdio.h>

// Action constants.
enum action { number, add, subtract, multiply, divide };

// Error marker constants, and spellings.
enum marker { digit, operator, bracket, newline };
char *names[] = { "digit", "operator", "bracket", "newline" };

// Array to hold parser bytecode generated by pecan.
static byte code[] = {
// <pecan>
// </pecan>
};

// Structure to hold input and stack of output numbers during calculations.
struct state { char *input; int top; int stack[100]; };
typedef struct state state;

// Stack operations.
static inline void new(state *s, char *in) { s->input = in; s->top = 0; }
static inline void push(state *s, int n) { s->stack[s->top++] = n; }
static inline int pop(state *s) { return s->stack[--s->top]; }

// Carry out an action.
static inline void act(void *vs, int a, int p, int n) {
    state *s = vs;
    int x, y;
    switch (a) {
        case number:
            x = 0;
            for (int i = 0; i < n; i++) x = x * 10 + (s->input[p + i] - '0');
            push(s, x); break;
        case add: y = pop(s); x = pop(s); push(s, x + y); break;
        case subtract: y = pop(s); x = pop(s); push(s, x - y); break;
        case multiply: y = pop(s); x = pop(s); push(s, x * y); break;
        case divide: y = pop(s); x = pop(s); push(s, x / y); break;
    }
}

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