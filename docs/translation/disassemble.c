#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

typedef unsigned char byte;

// The opcodes.
enum op {
    ACT, AND, ARITY, BACK, BOTH, CAT, DO, DROP,
    EITHER, EOT, GO, HAS, HIGH, LOOK, LOW, MANY,
    MARK, MAYBE, NOT, ONE, OR, POINT, SEE, SET,
    SPLIT, START, STOP, STRING, TAG
};

// Whether opcodes have operands.
bool hasArg[] = {
    true, false, true, true, true, true, false, true,
    true, false, true, false, true, false, true, false,
    true, false, false, false, false, false, false, true,
    true, true, false, true, true
};

// Whether operands are relative.
bool relative[] = {
    false, false, false, true, true, false, false, false,
    true, false, true, false, false, false, false, false,
    false, false, false, false, false, false, false, false,
    false, true, false, false, false
};

// Names of opcodes.
static char *opnames[] = {
    "ACT", "AND", "ARITY", "BACK", "BOTH", "CAT", "DO", "DROP",
    "EITHER", "EOT", "GO", "HAS", "HIGH", "LOOK", "LOW", "MANY",
    "MARK", "MAYBE", "NOT", "ONE", "OR", "POINT", "SEE", "SET",
    "SPLIT", "START", "STOP", "STRING", "TAG"
};

// Read in a binary file.
byte *readFile(char *filename) {
  FILE *fp = fopen(filename, "rb");
  if (fp == NULL) { printf("Can't read %s\n", filename); exit(1); }
  fseek(fp, 0, SEEK_END);
  long size = ftell(fp);
  fseek(fp, 0, SEEK_SET);
  byte *content = malloc(size+1);
  int n = fread(content, 1, size, fp);
  if (n != size) { printf("Can't read %s\n", filename); exit(1); }
  fclose(fp);
  content[size-1] = 0x7F;
  return content;
}

int main() {
  byte *code = readFile("sum.bin");
  int b, op, arg;
  for (int pc = 0; code[pc] != 0x7F;) {
      printf("%d:", pc);
      b = code[pc++];
      op = b & 0x1F;
      arg = (b >> 5) & 0x3;
      while ((b & 0x80) != 0) {
          b = code[pc++];
          arg = (arg << 7) | (b & 0x7F);
      }
      if (op == BACK) arg = pc - arg;
      else if (relative[op]) arg = pc + arg;
      if (hasArg[op]) printf("%s %d\n", opnames[op], arg);
      else printf("%s\n", opnames[op]);
  }
}
