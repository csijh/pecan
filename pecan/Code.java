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

public enum Code {
    CHAR,    // Single unicode character as a number, e.g. 13 or 0D
    ACT;

    RULE,    // x = y  ->  RULE framesize (followed by code for y)
    ID;      // Used in generator, maybe obsolete.
    OR,
    $AND,
    $OPT,
    $MANY,    // x*     ->  MAYBE &x MANY
    $SOME,    // x+     ->  SOME &x THEN MAYBE &x MANY
    $TRY,
    $HAS,     // x&     ->  LOOK &x HAS
    $NOT,     // x!     ->  LOOK &x NOT
    $MARK,    // #e     ->  MARK e
    $STRING,  // "ab"   ->  STRING 2 'a' 'b' (UTF-8 bytes)
    $SET,     // 'ab'   ->  SET 2 'a' 'b' (ASCII only)
    $RANGE,   // a..b   ->  RANGE m a n b (UTF-8)
    $CAT,     // Nd     ->  CAT Nd
    $TAG,     // %t     ->  TAG t
    $DROP,    // @      ->  DROP
    $ACT,     // @a     ->  ACT a

    $EITHER,  // x / y  ->  EITHER &x OR &y
    $EITHERI, // x / y  ->  EITHERI OR &y (x)
    $BOTH,    // x y    ->  BOTH &x AND &y
    $REPEAT,  // x?     ->  MAYBE &x OPT
    $LOOK,    // [x]    ->  LOOK &x TRY,  LOOK &x HAS,  LOOK &x NOT
    $START,   // Entry point
    $STOP,    // Exit point
    $THEN,
    $SWITCH,  // x / y  ->  SWITCH &x &y ch (UTF-8 bytes)
    $ORI,     // x / y  ->  EITHER &x OR0 (y)
    $BOTHI,   // x y    ->  BOTH3 AND &y (x)
    $ANDI,    // x y    ->  BOTH &x AND0 (y)
    $REPEATI, // x?     ->  REPEAT1 ONCE (x)
    $DOI,     // x+     ->  DO3 THEN REPEAT1 MANY (x)
    $LOOKI,   // [x]    ->  LOOK1 TRY (x)

    public static void main(String[] args) { }
}
