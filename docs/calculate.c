#include "parser.h"
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

// Action constants.
enum action { number = 0, add = 1 };

// Marker constants, and spellings.
enum marker { digit, operator, bracket };
char *names[] = { "digit", "operator", "bracket" };

static byte code[] = {
// <pecan>
//7) sum = number ('+' @ number @2add)?
//   number = ('0..9')+ @number
START1, 22, BOTH1, 2, GO1, 21, AND, MAYBE, ONE, BOTH1, 2, STRING, 43, AND,
BOTH, DROP, AND, BOTH1, 2, GO1, 6, AND, ACT, add, STOP, START1, 13, BOTH1, 8,
DO, AND, MAYBE, MANY, LOW, 48, HIGH, 57, AND, ACT, number, STOP
// </pecan>
};

// The state consists of the input and a stack of integers.
struct state { int top; int stack[1000]; };
typedef struct state state;

static inline void push(state *s, int n) {
    s->stack[s->top++] = n;
}

static inline int pop(state *s) {
    return s->stack[--s->top];
}

// Read a number from characters matched in the input, and push onto the stack.
static inline void doNumber(state *s, char *matched, int n) {
    int out = 0;
    for (int i = 0; i < n; i++) out = out * 10 + (matched[i] - '0');
    push(s, out);
}

// Add the top two numbers on the stack.
static inline void doAdd(state *s) {
    int m = pop(s);
    int n = pop(s);
    push(s, m + n);
}

// Carry out an action.
static inline void act(void *vs, int a, char *matched, int n) {
    state *s = vs;
    switch (a) {
        case number: doNumber(s, matched, n); break;
        case add: doAdd(s); break;
    }
}

int main(int n, char *args[n]) {
    setbuf(stdout, NULL);
    if (n != 2) {
        printf("Use: ./calculate \"sum\"\n");
        exit(1);
    }
    state sData;
    state *s = &sData;
    err eData;
    err *e = &eData;
    char *input = args[1];
    parse(0, code, input, NULL, act, s, e);
    if (e->ok) printf("%d\n", s->stack[0]);
    else {
        report(e, "Syntax error:\n", "Error: expecting ", ", ", "\n", names);
        reportLine(e, input);
        reportColumn(e);
    }
}
