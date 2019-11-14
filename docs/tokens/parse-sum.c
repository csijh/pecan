#include "scan-sum.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Node types, also used as actions, and their spellings.
enum type { read, add, subtract, multiply, divide };
static char *typeNames[] = { "read", "add", "subtract", "multiply", "divide" };

// Error marker constants, and spellings.
enum marker { digit, op, bracket, newline };
static char *markNames[] = { "digit", "operator", "bracket", "newline" };

// Declare the entry point to the generated functions.
bool Psum(parser *p);

int main() {
    char in[100];
    printf("Type a sum: ");
    char *r = fgets(in, 100, stdin);
    if (r == NULL) printf("Can't read stdin\n");
    int n;
    token *ts = scan(strlen(in), in, &n);
    for (int i = 0; i < n; i++) {
      printf("%d %d %d\n", ts[i].tag, ts[i].at, ts[i].length);
    }
    parser *p = newTokenParser(n, ts, strlen(in), in);
    Psum(p);
    free(ts);
    freeParser(p);
}

// The parsing functions, compiled from a Pecan grammar. The attributes of this
// pecan tag are the print patterns used to generate the functions.
//
// <pecan
//   comment  = "// %s"
//   declare  = "bool P%s(parser *p);"
//   define   = "bool P%s(parser *p) { %n return %r; %n}"
//   call     = "%s(p)"
//   id       = "P%s(p)"
//   act0     = "true"
//   act1     = "pushT(p,0,create(%s,at(p),matched(p)))"
//   escape1  = "\%3o"
//   escape2  = "\u%4x"
//   escape4  = "\U%8x"
// >

// </pecan>. End of generated functions.
