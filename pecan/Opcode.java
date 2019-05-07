// Pecan 5 opcodes. Free and open source. See licence.txt.

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
    $RULE,    // x = y  ->  RULE framesize (followed by code for y)
    $EITHER,  // x / y  ->  EITHER &x OR &y
    $EITHERI, // x / y  ->  EITHERI OR &y (x)
    $OR,
    $BOTH,    // x y    ->  BOTH &x AND &y
    $AND,
    $REPEAT,  // x?     ->  REPEAT &x ONCE
    $MANY,    // x*     ->  REPEAT &x MANY
    $DO,      // x+     ->  DO &x THEN REPEAT &x MANY
    $LOOK,    // [x]    ->  LOOK &x TRY,  LOOK &x HAS,  LOOK &x NOT
    $ACT,     // @a     ->  ACT a
    $MARK,    // #e     ->  MARK e
    $START,   // Entry point
    $STOP,    // Exit point
    $ONCE,
    $THEN,
    $TRY,
    $HAS,     // x&     ->  LOOK &x HAS
    $NOT,     // x!     ->  LOOK &x NOT
    $STRING,  // "ab"   ->  STRING 2 'a' 'b' (UTF-8 bytes)
    $SET,     // 'ab'   ->  SET 2 'a' 'b' (ASCII only)
    $RANGE,   // a..b   ->  RANGE m a n b (UTF-8)
    $CAT,     // Nd     ->  CAT Nd
    $SWITCH,  // x / y  ->  SWITCH &x &y ch (UTF-8 bytes)
    $TAG,     // %t     ->  TAG t
    $DROP,    // @      ->  DROP
    $ORI,     // x / y  ->  EITHER &x OR0 (y)
    $BOTHI,   // x y    ->  BOTH3 AND &y (x)
    $ANDI,    // x y    ->  BOTH &x AND0 (y)
    $REPEATI, // x?     ->  REPEAT1 ONCE (x)
    $DOI,     // x+     ->  DO3 THEN REPEAT1 MANY (x)
    $LOOKI,   // [x]    ->  LOOK1 TRY (x)
    $GO;      // Used in generator, maybe obsolete.

    public static void main(String[] args) { }
}
