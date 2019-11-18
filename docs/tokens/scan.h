// Scanner module for sums. Public domain.
#include "parser.h"

// Token tags.
enum tag { bad, number, plus, minus, times, over, open, close, end };

// Scan a sum. Return an array of tokens, with a final sentinel token with tag
// 'end'. Scanning never fails. Error tokens are included with tag 'bad'.
token *scan(int n, char input[n]);
