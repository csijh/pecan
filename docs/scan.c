#include "parser.h"
#include <stdio.h>
#include <stdlib.h>

// Action constants, which are used as token tags, and their names.
enum action { tokens, number, plus, minus, times, over, open, close, bad, end };
char *names[] = {
    "tokens","number", "plus", "minus", "times", "over", "open", "close",
    "bad", "end"
};

// Array to hold scanner bytecode generated by pecan.
static byte code[] = {
// <pecan>
// </pecan>
};

// Token structure: tag, plus position and length of source text.
struct token { int tag, at, length; };
typedef struct token token;

// Structure to hold the input and list of tokens.
struct state { char *input; int length, max; token *list; };
typedef struct state state;

// Create a new state with a given input and empty token list of size n.
static inline state *newState(char *in, int n) {
    state *s = malloc(sizeof(state));
    *s = (state) {
        .input = in, .length = 0, .max = n, .list = malloc(n * sizeof(token))
    };
    return s;
}

// Free the state and its list of tokens.
static inline void freeState(state *s) {
    free(s->list);
    free(s);
}

// Add a token, doubling the size of the list if necessary.
static inline void add(state *s, int tag, int p, int n) {
    if (s->length >= s->max) {
        s->max = s->max * 2;
        s->list = realloc(s->list, s->max * sizeof(token));
    }
    token *t = &s->list[s->length++];
    *t = (token) { .tag = tag, .at = p, .length = n };
}

// Carry out an action. The action is normally a token tag.
static inline void act(void *vs, int a, int p, int n) {
    state *s = vs;
    if (a == tokens) return;
    add(s, a, p, n);
}

int main() {
    char input[100];
    char *out = fgets(input, 100, stdin);
    if (out == NULL) printf("Can't read stdin\n");
    state *s = newState(input, 8);
    result *r = malloc(sizeof(result));
    parseText(code, input, act, s, r);
    for (int i = 0; i < s->length; i++) {
        token *t = &s->list[i];
        printf("%s %d %d\n", names[t->tag], t->at, t->length);
    }
    free(r);
    freeState(s);
}
