// Pecan 1.0 opcodes. Free and open source. See licence.txt.

package pecan;

/* Opcode constants represent bytecode ops.

<id = x>    RULE id START nx <x> STOP
<id>        GO n     or     BACK n
[ */

public enum Code {
    RULE,   // Entry point, argument is rule name
    START,  // Initialize, push address of STOP, jump to <x>
    STOP,   // End parsing
    GO,     // skip forwards
    BACK,   // skip backwards, negates its unsigned arg

    EITHER, // x / y            EITHER n <x> OR <y>
    OR,
    BOTH,   // x y              BOTH n <x> AND <y>
    AND,
    MAYBE,  // x? or x*         MAYBE ONE/MANY <x>
    ONE,
    MANY,
    DO,     // x+               DO THEN MAYBE MANY <x>
    THEN,
    LOOK,   // [x] or x& or x!  LOOK TRY/HAS/NOT <x>
    TRY,
    HAS,
    NOT,

    CHAR,    // One-byte character
    SET,     // 'ab'   ->  SET 2 'a' 'b' (UTF-8)
    STRING,  // "ab"   ->  STRING 2 'a' 'b' (UTF-8 byte sequence)
    LOW,     // "a".."z" -> LOW m ... HIGH n ...
    HIGH,
    CAT,     // Nd     ->  CAT Nd
    TAG,     // %t     ->  TAG t

    MARK,    // #e     ->  MARK e
    DROP,    // @      ->  DROP
    ACT;     // @a     ->  ACT a

    public static void main(String[] args) { }
}
