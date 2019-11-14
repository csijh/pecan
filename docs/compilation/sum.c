// Parser for arithmetic sums. Public domain.
#include "parse.h"
#include <stdio.h>
#include <string.h>

// Error marker constants, and spellings.
enum marker { integer, operator, bracket, newline };
char *names[] = { "integer", "operator", "bracket", "newline" };

// Action: create a number from given text.
int read(int n, char s[n]) {
  int x = 0;
  for (int i = 0; i < n; i++) x = x * 10 + s[i] - '0';
  return x;
}

// Actions: arithmetic operations.
int add(int x, int y) { return x + y; }
int subtract(int x, int y) { return x - y; }
int multiply(int x, int y) { return x * y; }
int divide(int x, int y) { return x / y; }

// Declare the entry point.
bool sum(parser *p);

// Read in a sum and evaluate it.
int main() {
  char in[100];
  printf("Type a sum: ");
  char *r = fgets(in, 100, stdin);
  if (r == NULL) printf("Can't read stdin\n");
  parser *p = newCharParser(strlen(in), in);
  bool ok = sum(p);
  if (ok) printf("%d\n", topI(p,0));
  else report(p, "Syntax error:\n", "Error: expecting %s, %s\n", names);
  freeParser(p);
}

// The parsing functions, compiled from a Pecan grammar. The attributes of this
// pecan tag are the print patterns used to generate the functions.
//
// <pecan
//   comment  = "// %s"
//   declare  = "bool %s(parser *p);"
//   define   = "bool %s(parser *p) { %n return %r; %n}"
//   call     = "%s(p)"
//   act0     = "pushI(p,0,%s(matched(p),match(p)))"
//   act2     = "pushI(p,2,%s(topI(p,1),topI(p,0)))"
//   escape1  = "\%3o"
//   escape2  = "\u%4x"
//   escape4  = "\U%8x"
// >

// </pecan>. End of generated functions.
