#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "parser.h"

// Action constants.
enum action { number = 0, add = 1 };

static byte code[] = {
    // <pecan>
//1) digit = '0..9' @number
//START1, 9, BOTH1, 4, LOW, 48, HIGH, 57, AND, ACT1, number, STOP
//2) number = ('0..9')+ @number
//START1, 13, BOTH1, 8, DO, AND, MAYBE, MANY, LOW, 48, HIGH, 57, AND, ACT1,
//number, STOP
//3) sum = number / number '+' number @2add
//number = ('0..9')+ @number
//START1, 22, EITHER1, 2, GO1, 21, OR, BOTH1, 2, GO1, 16, AND, BOTH1, 2, STRING,
//43, AND, BOTH1, 2, GO1, 6, AND, ACT1, add, STOP, START1, 13, BOTH1, 8, DO,
//AND, MAYBE, MANY, LOW, 48, HIGH, 57, AND, ACT1, number, STOP
//4) sum = number '+' number @2add / number
//number = ('0..9')+ @number
//START1, 22, EITHER1, 17, BOTH1, 2, GO1, 19, AND, BOTH1, 2, STRING, 43, AND,
//BOTH1, 2, GO1, 9, AND, ACT1, add, OR, GO1, 3, STOP, START1, 13, BOTH1, 8, DO,
//AND, MAYBE, MANY, LOW, 48, HIGH, 57, AND, ACT1, number, STOP
//5) sum = [number '+'] number @2add / number
//number = ('0..9')+ @number
//START1, 24, EITHER1, 19, BOTH1, 9, LOOK, TRY, BOTH1, 2, GO1, 17, AND, STRING,
//43, AND, BOTH1, 2, GO1, 9, AND, ACT1, add, OR, GO1, 3, STOP, START1, 13,
//BOTH1, 8, DO, AND, MAYBE, MANY, LOW, 48, HIGH, 57, AND, ACT1, number, STOP
//7) sum = number ('+' @ number @2add)?
//   number = ('0..9')+ @number
START1, 22, BOTH1, 2, GO1, 21, AND, MAYBE, ONE, BOTH1, 2, STRING, 43, AND,
BOTH, DROP, AND, BOTH1, 2, GO1, 6, AND, ACT1, add, STOP, START1, 13, BOTH1, 8,
DO, AND, MAYBE, MANY, LOW, 48, HIGH, 57, AND, ACT1, number, STOP
    // </pecan>
};

// The state consists of the input and a stack of integers.
struct state { char *input; int top; int stack[1000]; };
typedef struct state state;

// Carry out an action.
static inline void act(int a, int start, int end, void *vs) {
    printf("act a = %d, start = %d, end = %d\n", a, start, end);
    state *s = vs;
    switch (a) {
    case number: {
        int n = 0;
        for (int i = start; i < end; i++) n = n * 10 + (s->input[i] - '0');
        s->stack[s->top++] = n;
        break; }
    case add: {
        printf("add: top=%d\n", s->top);
        int m = s->stack[--s->top];
        int n = s->stack[--s->top];
        printf("add: m=%d n=%d\n", m, n);
        s->stack[s->top++] = m + n;
        break; }
    }
}

// Call with one argument.
int main(int n, char *args[n]) {
    setbuf(stdout, NULL);
    if (n != 2) {
        printf("Use: ./calc sum\n");
        exit(1);
    }
    state sData;
    state *s = &sData;
    s->input = args[1];
    bits result = parseText(0, code, args[1], act, s);
    if (result == 0L) printf("%d\n", s->stack[0]);
    else printf("Error\n");
}
