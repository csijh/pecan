// Scanner for sums. Public domain.
#include "scan-sum.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Error marker constants, and spellings.
enum marker { integer, operator, bracket, newline };
char *names[] = { "integer", "operator", "bracket", "newline" };

// Action: create a token.
static token create(int tag, int at, int length) {
    token t = { .tag = tag, .at = at, .length = length };
    return t;
}

// Declare the entry point to the generated functions.
static bool Ptokens(parser *p);

// Parsing is bound to succeed. Errors are embedded as tokens with tag 'bad'.
token *scan(int n, char input[n], int *pn) {
    parser *p = newCharParser(n, input);
    Ptokens(p);
    int nt = outputs(p);
    token *ts = getTokens(p);
    freeParser(p);
    *pn = nt;
    return ts;
}

// The parsing functions, compiled from a Pecan grammar. The attributes of this
// pecan tag are the print patterns used to generate the functions. The act0
// attribute is set to 'true' so that the action @array does nothing. The act1
// attribute ensures that any other action pushes a token onto the stack.
//
// <pecan
//   comment  = "// %s"
//   declare  = "static bool P%s(parser *p);"
//   define   = "static bool P%s(parser *p) { %n return %r; %n}"
//   call     = "%s(p)"
//   id       = "P%s(p)"
//   act0     = "true"
//   act1     = "pushT(p,0,create(%s,at(p),matched(p)))"
//   escape1  = "\%3o"
//   escape2  = "\u%4x"
//   escape4  = "\U%8x"
// >

static bool Ptokens(parser *p);
static bool Ptokens1(parser *p);
static bool Ptoken(parser *p);
static bool Pnumber(parser *p);
static bool Pnumber1(parser *p);
static bool Pplus(parser *p);
static bool Pminus(parser *p);
static bool Ptimes(parser *p);
static bool Pover(parser *p);
static bool Popen(parser *p);
static bool Pclose(parser *p);
static bool Pbad(parser *p);
static bool Pend(parser *p);
static bool Pgap(parser *p);
static bool Pgap1(parser *p);

// tokens = gap @array token* end
static bool Ptokens(parser *p) {
  return Pgap(p) && true && Ptokens1(p) && Pend(p);
}

// tokens1 = (token tokens1)?
static bool Ptokens1(parser *p) { return ((Ptoken(p) && Ptokens1(p)) || true); }

// token = number / plus / minus / times / over / open / close / bad
static bool Ptoken(parser *p) {
  return Pnumber(p) || Pplus(p) || Pminus(p) || Ptimes(p) || Pover(p) ||
  Popen(p) || Pclose(p) || Pbad(p);
}

// number = ('0..9')+ @1number gap
static bool Pnumber(parser *p) {
  return Pnumber1(p) && pushT(p,0,create(number,at(p),matched(p))) && Pgap(p);
}

// number1 = '0..9' number1?
static bool Pnumber1(parser *p) {
  return range(p,'0','9') && (Pnumber1(p) || true);
}

// plus = '+' @1plus gap
static bool Pplus(parser *p) {
  return string(p,"+") && pushT(p,0,create(plus,at(p),matched(p))) && Pgap(p);
}

// minus = '-' @1minus gap
static bool Pminus(parser *p) {
  return string(p,"-") && pushT(p,0,create(minus,at(p),matched(p))) && Pgap(p);
}

// times = '*' @1times gap
static bool Ptimes(parser *p) {
  return string(p,"*") && pushT(p,0,create(times,at(p),matched(p))) && Pgap(p);
}

// over = '/' @1over gap
static bool Pover(parser *p) {
  return string(p,"/") && pushT(p,0,create(over,at(p),matched(p))) && Pgap(p);
}

// open = '(' @1open gap
static bool Popen(parser *p) {
  return string(p,"(") && pushT(p,0,create(open,at(p),matched(p))) && Pgap(p);
}

// close = ')' @1close gap
static bool Pclose(parser *p) {
  return string(p,")") && pushT(p,0,create(close,at(p),matched(p))) && Pgap(p);
}

// bad = '\r\n'! . @1bad
static bool Pbad(parser *p) {
  return not(p,go(p) && set(p,"\015\012")) && point(p) &&
  pushT(p,0,create(bad,at(p),matched(p)));
}

// end = '\r'? '\n' / @1bad
static bool Pend(parser *p) {
  return alt(p,
    (go(p) && (string(p,"\015") || true) && string(p,"\012")) ||
    (ok(p) && pushT(p,0,create(bad,at(p),matched(p))))
  );
}

// gap = (' ')* @
static bool Pgap(parser *p) { return Pgap1(p) && drop(p,0); }

// gap1 = (' ' gap1)?
static bool Pgap1(parser *p) { return ((string(p," ") && Pgap1(p)) || true); }

// </pecan>. End of generated functions.
