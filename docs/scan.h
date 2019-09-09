// Example scanner module for sums. Public domain.

// Token tags.
enum tag { number, plus, minus, times, over, open, close, bad };

// Token structure: tag, plus position and length of source text.
struct token { int tag, at, length; };
typedef struct token token;

// Scan a sum. Return tokens, terminated with a token having a negative tag.
token *scan(char *input);
