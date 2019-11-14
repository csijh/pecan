// Scanner module for sums. Public domain.
#include "parse.h"

// Token tags.
enum tag { number, plus, minus, times, over, open, close, bad };

// Scan a sum. Return an array of tokens, and provide the length in *pn.
// Scanning never fails. Instead, error tokens are included with tag 'bad'.
token *scan(int n, char input[n], int *pn);
