// Scanner module for sums. Public domain.

// Token tags.
enum tag { number, plus, minus, times, over, open, close, bad, eot };

// Token structure: tag, plus position and length of source text.
struct token { int tag, at, length; };
typedef struct token token;

// Scan a sum. Return an array of tokens, terminated with an eot token.
token *scan(char *input);
