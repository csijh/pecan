// Scanner for sums. Public domain.
#include "scan.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Action: the start address of the store will be the token array.
static token *array(parser *p) {
    token *ts = new(p, 0);
    return ts;
}

// Action: add a new token, return the array.
static token *add(parser *p, token *ts, int tag, int start, int at) {
    token *t = new(p, sizeof(token));
    *t = (token) { .tag = tag, .start = start, .length = at - start };
    return ts;
}

// Declare the entry point to the generated functions.
static bool Ptokens(parser *p);

// Parsing is bound to succeed. Errors are embedded as tokens with tag 'bad'.
token *scan(int n, char input[n]) {
    parser *p = newParser(n, input);
    Ptokens(p);
    token *ts = getStore(p);
    freeParser(p);
    return ts;
}

// The parsing functions, compiled from a Pecan grammar. The attributes of this
// pecan tag are the print patterns used to generate the functions.
//
// <pecan
//   comment  = "// %s"
//   declare  = "static bool P%s(parser *p);"
//   define   = "static bool P%s(parser *p) { %n return %r; %n}"
//   call     = "%s(p)"
//   id       = "P%s(p)"
//   act0     = "pushR(p,0,%s(p))"
//   act1     = "pushR(p,1,add(p,topR(p,0),%s,start(p),at(p)))"
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
  return Pgap(p) && pushR(p,0,array(p)) && Ptokens1(p) && Pend(p);
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
  return Pnumber1(p) && pushR(p,1,add(p,topR(p,0),number,start(p),at(p))) &&
  Pgap(p);
}

// number1 = '0..9' number1?
static bool Pnumber1(parser *p) {
  return range(p,'0','9') && (Pnumber1(p) || true);
}

// plus = '+' @1plus gap
static bool Pplus(parser *p) {
  return string(p,"+") && pushR(p,1,add(p,topR(p,0),plus,start(p),at(p))) &&
  Pgap(p);
}

// minus = '-' @1minus gap
static bool Pminus(parser *p) {
  return string(p,"-") && pushR(p,1,add(p,topR(p,0),minus,start(p),at(p))) &&
  Pgap(p);
}

// times = '*' @1times gap
static bool Ptimes(parser *p) {
  return string(p,"*") && pushR(p,1,add(p,topR(p,0),times,start(p),at(p))) &&
  Pgap(p);
}

// over = '/' @1over gap
static bool Pover(parser *p) {
  return string(p,"/") && pushR(p,1,add(p,topR(p,0),over,start(p),at(p))) &&
  Pgap(p);
}

// open = '(' @1open gap
static bool Popen(parser *p) {
  return string(p,"(") && pushR(p,1,add(p,topR(p,0),open,start(p),at(p))) &&
  Pgap(p);
}

// close = ')' @1close gap
static bool Pclose(parser *p) {
  return string(p,")") && pushR(p,1,add(p,topR(p,0),close,start(p),at(p))) &&
  Pgap(p);
}

// bad = '\r\n'! . @1bad
static bool Pbad(parser *p) {
  return not(p,go(p) && set(p,"\015\012")) && point(p) &&
  pushR(p,1,add(p,topR(p,0),bad,start(p),at(p)));
}

// end = '\r'? '\n' @1end / @1bad
static bool Pend(parser *p) {
  return alt(p,
    (go(p) && (string(p,"\015") || true) && string(p,"\012") &&
    pushR(p,1,add(p,topR(p,0),end,start(p),at(p)))) ||
    (ok(p) && pushR(p,1,add(p,topR(p,0),bad,start(p),at(p))))
  );
}

// gap = (' ')* @
static bool Pgap(parser *p) { return Pgap1(p) && drop(p,0); }

// gap1 = (' ' gap1)?
static bool Pgap1(parser *p) { return ((string(p," ") && Pgap1(p)) || true); }

// </pecan>. End of generated functions.
