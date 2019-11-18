// Parser for arithmetic sums. Public domain.
#include "parser.h"
#include <stdio.h>
#include <string.h>

// Error marker constants, and spellings.
enum marker { integer, operator, bracket, newline };
char *names[] = { "integer", "operator", "bracket", "newline" };

// Action: create a number from given text.
int read(char *s, int start, int end) {
  int x = 0;
  for (int i = start; i < end; i++) x = x * 10 + s[i] - '0';
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
  parser *p = newParser(strlen(in), in);
  bool ok = sum(p);
  if (ok) printf("%ld\n", topI(p,0));
  else report(p, at(p), "Syntax error:\n", "Error: expecting %s, %s\n", names);
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
//   act0     = "pushI(p,0,%s(text(p),start(p),at(p)))"
//   act2     = "pushI(p,2,%s(topI(p,1),topI(p,0)))"
//   escape1  = "\%3o"
//   escape2  = "\u%4x"
//   escape4  = "\U%8x"
// >

bool sum(parser *p);
bool expression(parser *p);
bool expression1(parser *p);
bool term(parser *p);
bool term1(parser *p);
bool atom(parser *p);
bool number(parser *p);
bool number1(parser *p);
bool plus(parser *p);
bool minus(parser *p);
bool times(parser *p);
bool over(parser *p);
bool open(parser *p);
bool close(parser *p);
bool digit(parser *p);
bool gap(parser *p);
bool gap1(parser *p);
bool end(parser *p);

// sum = gap expression end
bool sum(parser *p) { return gap(p) && expression(p) && end(p); }

// expression = term (plus term @2add / minus term @2subtract)*
bool expression(parser *p) { return term(p) && expression1(p); }

// expression1 = ((plus term @2add / minus term @2subtract) expression1)?
bool expression1(parser *p) {
  return opt(p,
    go(p) && (alt(p,
      (go(p) && plus(p) && term(p) && pushI(p,2,add(topI(p,1),topI(p,0)))) ||
      (ok(p) && minus(p) && term(p) && pushI(p,2,subtract(topI(p,1),topI(p,0))))
    )) && expression1(p)
  );
}

// term = atom (times atom @2multiply / over atom @2divide)*
bool term(parser *p) { return atom(p) && term1(p); }

// term1 = ((times atom @2multiply / over atom @2divide) term1)?
bool term1(parser *p) {
  return opt(p,
    go(p) && (alt(p,
      (go(p) && times(p) && atom(p) && pushI(p,2,multiply(topI(p,1),topI(p,0)))) ||
      (ok(p) && over(p) && atom(p) && pushI(p,2,divide(topI(p,1),topI(p,0))))
    )) && term1(p)
  );
}

// atom = number / open expression close
bool atom(parser *p) {
  return number(p) || (open(p) && expression(p) && close(p));
}

// number = #integer digit+ @read gap
bool number(parser *p) {
  return mark(p,integer) && number1(p) &&
  pushI(p,0,read(text(p),start(p),at(p))) && gap(p);
}

// number1 = digit number1?
bool number1(parser *p) { return digit(p) && (number1(p) || true); }

// plus = #operator '+' gap
bool plus(parser *p) { return mark(p,operator) && string(p,"+") && gap(p); }

// minus = #operator '-' gap
bool minus(parser *p) { return mark(p,operator) && string(p,"-") && gap(p); }

// times = #operator '*' gap
bool times(parser *p) { return mark(p,operator) && string(p,"*") && gap(p); }

// over = #operator '/' gap
bool over(parser *p) { return mark(p,operator) && string(p,"/") && gap(p); }

// open = #bracket '(' gap
bool open(parser *p) { return mark(p,bracket) && string(p,"(") && gap(p); }

// close = #bracket ')' gap
bool close(parser *p) { return mark(p,bracket) && string(p,")") && gap(p); }

// digit = '0..9'
bool digit(parser *p) { return range(p,'0','9'); }

// gap = (' ')* @
bool gap(parser *p) { return gap1(p) && drop(p,0); }

// gap1 = (' ' gap1)?
bool gap1(parser *p) { return ((string(p," ") && gap1(p)) || true); }

// end = #newline '\r'? '\n' @
bool end(parser *p) {
  return mark(p,newline) && (string(p,"\015") || true) && string(p,"\012") &&
  drop(p,0);
}

// </pecan>. End of generated functions.
