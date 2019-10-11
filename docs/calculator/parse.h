/* This is a generic Pecan parser module for C. The small functions provided are
declared as extern inline so that, for most C compilers, an option such as
-flto can be used to enable cross-module inlining. */
#include <stdbool.h>

// The type of an input item, e.g. char or token *. Change for each application.
typedef char input;

// The type of an output item. Change this for each application.
typedef int output;

// The (opaque) structure of a current parsing state.
struct parser;
typedef struct parser parser;

// Unicode category codes, in the order used in table1.bin, table2.bin.
enum category {
    Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
    Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf
};

// Create a new parser object with the given input string. The string can be
// UTF-8, can contain nulls, and needn't end with a null.
parser *newParser(int n, char input[n]);

// Create a new parser with the given token array as input.
parser *newTokenParser(int n, void *tokens[n]); // pass act fns.

// Free up a parser object and its resources. Does not free the input array.
void freeParser(parser *p); // pass 'tag' fn, act fns

// These functions are support functions called from a compiled Pecan grammar.


// Provide a description of how to generate C from Pecan. This description can
// be read in by Pecan and used to generate suitable parsing functions which are
// compatible with the functions provided here.
