#include "parse.h"
#include <stdio.h>

//enum opcode {
//    STOP, RULE, GO, EITHER, OR, BOTH, AND, REPEAT, ONCE, MANY, LOOK, TRY, HAS,
//    NOT, STRING, SET, RANGE, CAT, TAG, MARK, REPORT, DROP, ACT
//};

// 0:STOP, 1:RULE 1, 4:BOTH, 0, TRY, AND,
// Tutorial step 1;  inputs: "2"
unsigned char t1[] = {
    0, 1, 0, 1, 5, 0, 11, 6, 22, 0, 0, 16, 1, 48, 1, 57
};

// Tutorial step 2: "2" "42"
unsigned char t2[] = {
    0, 1, 0, 3, 5, 0, 11, 6, 22, 0, 0, 5, 0, 17, 6, 7, 9, 16, 1, 48, 1, 57
};

// Tutorial step 3: "2" "42" "2+40"  (third test only recognises 2)
unsigned char t3[] = {
    0, 1, 0, 2, 3, 0, 26, 4, 5, 0, 26, 6, 5, 0, 23, 6, 5, 0, 26, 6, 22, 0, 1,14,
    1, 43, 1, 0, 3, 5, 0, 36, 6, 22, 0, 0, 5, 0, 42, 6, 7, 9, 16, 1, 48, 1, 57
};

// Tutorial step 4: "2" (expecting op)
unsigned char t4[] = {
    0, 1, 0, 3, 3, 0, 11, 4, 2, 0, 29, 5, 0, 29, 6, 5, 0, 26, 6, 5, 0, 29, 6,
    22, 0, 1, 14, 1, 43, 1, 0, 3, 5, 0, 39, 6, 22, 0, 0, 5, 0, 45, 6, 7, 9, 16,
    1, 48, 1, 57
};

// Tutorial step 5: "2" "42" "2+40" (3rd test doesn't discard "+")
unsigned char t5[] = {
    0, 1, 0, 8, 3, 0, 11, 4, 2, 0, 38, 5, 0, 22, 6, 5, 0, 38, 6, 22, 0, 1, 5, 0,
    29, 6, 2, 0, 0, 10, 12, 5, 0, 59, 6, 14, 1, 43, 1, 0, 3, 5, 0, 48, 6, 22, 0,
    0, 5, 0, 54, 6, 7, 9, 16, 1, 48, 1, 57, 1, 0, 2, 5, 0, 54, 6, 7, 9, 16, 1,
    48, 1, 57
};

void act(int act, void *out, int start, int end) {
    printf("%d  %d %d\n", act, start, end);
}

// TODO: find position, position of start/end of line, line number.  Return
// in structure defined in parse.h.

int main() {
    report *fail = parse(t5, 0, "2+40", act, NULL, false);
    if (fail == NULL) printf("ok\n");
    else printf("fail %d %d\n", fail->position, fail->expects);
}
