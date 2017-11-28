// Part of Pecan 4. Open source - see licence.txt.

package pecan;

/* Opcode constants represent bytecode ops.

TODO:
x <c y         if next char < c, do x, else y
x >c y         if next char > c, do x, else y
[<c] x / y     alternative, expresses binary search in grammar
[>c] x / y
[>=a] <=b      equivalent of range
               (similar to a sparse switch statement in many compilers)
 */

public enum Opcode {
    // First, opcodes which have a two-byte arg
    $RULE,    // x = y  ->  RULE framesize (followed by code for y)
    $EITHER,  // x / y  ->  EITHER &x OR &y
    $OR,
    $BOTH,    // x y    ->  BOTH &x AND &y
    $AND,
    $REPEAT,  // x?     ->  REPEAT &x ONCE
    $MANY,    // x*     ->  REPEAT &x MANY
    $DO,      // x+     ->  DO &x THEN REPEAT &x MANY
    $LOOK,    // [x]    ->  LOOK &x TRY
    $ACT,     // @a     ->  ACT a
    $MARK,    // #e     ->  MARK e
    // Next, opcodes with no arg or a one-byte arg or a string arg
    $START,   // Entry point
    $STOP,
    $ONCE,
    $THEN,
    $TRY,
    $HAS,     // x&     ->  LOOK &x HAS
    $NOT,     // x!     ->  LOOK &x NOT
    $STRING,  // "ab"   ->  STRING 2 'a' 'b' (UTF-8 bytes)
    $SET,     // 'ab'   ->  SET 2 'a' 'b' (ASCII only)
    $RANGE,   // a..b   ->  RANGE m a n b (UTF-8)
    $CAT,     // Nd     ->  CAT Nd
    $TAG,     // %t     ->  TAG t
    $DROP,    // @      ->  DROP
    // Next, opcodes when the code for x or y immediately follows
    $EITHER3, // x / y  ->  EITHER3 OR &y (x)
    $OR0,     // x / y  ->  EITHER &x OR0 (y)
    $BOTH3,   // x y    ->  BOTH3 AND &y (x)
    $AND0,    // x y    ->  BOTH &x AND0 (y)
    $REPEAT1, // x?     ->  REPEAT1 ONCE (x)
    $DO3,     // x+     ->  DO3 THEN REPEAT1 MANY (x)
    $LOOK1,   // [x]    ->  LOOK1 TRY (x)
    $GO;      // Used in generator, maybe obsolete.

    public static void main(String[] args) { }
}
