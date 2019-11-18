#include "scan.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Error marker constants, and spellings.
enum marker { digit, operator, bracket, newline };
static char *names[] = { "digit", "operator", "bracket", "newline" };

// Action constants used as tags for tree nodes.
enum action { integer, add, subtract, multiply, divide };
static char *opNames[] = { "integer", "add", "subtract", "multiply", "divide" };

// A node is defined as a union so nodes all have the same type and size.
union node {
    int tag;
    struct leaf { int tag, number; } leaf;
    struct branch { int tag, left, right; } branch;
};
typedef union node node;

// Action: The integer action converts the most recently matched number token
// into a leaf node.
static void *act0(parser *p, int tag, int at) {
    token *ts = getTokens(p);
    token *t = &ts[at - 1];
    char *s = text(p);
    int n = 0;
    for (int i = 0; i < t->length; i++) n = n * 10 + s[t->start+i] - '0';
    node *x = new(p, sizeof(node));
    x->leaf = (struct leaf) { .tag = tag, .number = n };
    return x;
}

// Actions: create a branch node representing an operation.
static void *act2(parser *p, int tag, node *x, node *y) {
    node *z = new(p, sizeof(node));
    z->branch = (struct branch) { .tag = tag, .left = x-z, .right = y-z };
    return z;
}

// Declare the entry point to the generated functions.
static bool Psum(parser *p);

// Print out a tree with given indent.
void print (node *x, int indent) {
    for (int i = 0; i < indent; i++) printf("  ");
    if (x->tag == integer) printf("%s %d\n", opNames[x->tag], x->leaf.number);
    else {
        printf("%s\n", opNames[x->tag]);
        print(x + x->branch.left, indent + 1);
        print(x + x->branch.right, indent + 1);
    }
}

int main() {
    char in[100];
    printf("Type a sum: ");
    char *r = fgets(in, 100, stdin);
    if (r == NULL) printf("Can't read stdin\n");
    token *ts = scan(strlen(in), in);
    parser *p = newTokenParser(strlen(in), in, -1, ts);
    bool b = Psum(p);
    if (!b) {
        int s = ts[at(p)].start;
        report(p, s, "Syntax error:\n", "Error: expecting %s, %s\n", names);
        exit(1);
    }
    void *nodes = getStore(p);
    node *root = topR(p, 0);
    freeParser(p);
    free(ts);
    print(root, 0);
    free(nodes);
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
//   act0     = "pushR(p,0,act0(p,%s,at(p)))"
//   act2     = "pushR(p,2,act2(p,%s,topR(p,1),topR(p,0)))"
//   escape1  = "\%3o"
//   escape2  = "\u%4x"
//   escape4  = "\U%8x"
// >

static bool Psum(parser *p);
static bool Pexp(parser *p);
static bool Pexp1(parser *p);
static bool Pterm(parser *p);
static bool Pterm1(parser *p);
static bool Patom(parser *p);

// sum = exp #newline %end
static bool Psum(parser *p) { return Pexp(p) && mark(p,newline) && tag(p,end); }

// exp = term (#operator %plus term @2add / #operator %minus term @2subtract)*
static bool Pexp(parser *p) { return Pterm(p) && Pexp1(p); }

// exp1 = ((#operator %plus term @2add / #operator %minus term @2subtract) exp1)?
static bool Pexp1(parser *p) {
  return opt(p,
    go(p) && (alt(p,
      (go(p) && mark(p,operator) && tag(p,plus) && Pterm(p) &&
      pushR(p,2,act2(p,add,topR(p,1),topR(p,0)))) ||
      (ok(p) && mark(p,operator) && tag(p,minus) && Pterm(p) &&
      pushR(p,2,act2(p,subtract,topR(p,1),topR(p,0))))
    )) && Pexp1(p)
  );
}

// term = atom (#operator %times atom @2multiply / #operator %over atom @2divide)*
static bool Pterm(parser *p) { return Patom(p) && Pterm1(p); }

// term1 = ((#operator %times atom @2multiply / #operator %over atom @2divide) term1)?
static bool Pterm1(parser *p) {
  return opt(p,
    go(p) && (alt(p,
      (go(p) && mark(p,operator) && tag(p,times) && Patom(p) &&
      pushR(p,2,act2(p,multiply,topR(p,1),topR(p,0)))) ||
      (ok(p) && mark(p,operator) && tag(p,over) && Patom(p) &&
      pushR(p,2,act2(p,divide,topR(p,1),topR(p,0))))
    )) && Pterm1(p)
  );
}

// atom = #digit %number @integer / #bracket %open exp #bracket %close
static bool Patom(parser *p) {
  return (mark(p,digit) && tag(p,number) && pushR(p,0,act0(p,integer,at(p)))) ||
  (mark(p,bracket) && tag(p,open) && Pexp(p) && mark(p,bracket) && tag(p,close));
}

// </pecan>. End of generated functions.
