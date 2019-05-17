// Pecan 5 operators. Free and open source. See licence.txt.

package pecan;

/* Op constants represent parsing expression operators, and are used to classify
nodes. Later ops are used to generate bytecode. */

public enum Op {
    Rule,    // Definition, name = rhs, text is name, child is rhs
    Id,      // Identifier, with cross-reference to its definition
    Or,      // Choice, x / y, right associative
    And,     // Sequence, x y, right associative
    OPT,     // Optional, x?
    MANY,    // Zero or more, x*
    SOME,    // One or more, x+
    TRY,     // Lookahead, [e]
    HAS,     // Positive lookahead, e&
    NOT,     // Negative lookahead, e!
    MARK,    // Error annotation, x #e
    CHAR,    // Single unicode character as a number, e.g. 13 or 0D
    STRING,  // Character sequence, "..."
    SET,     // Choice of characters, '...'
    RANGE,   // Character range, x..y
    CAT,     // Unicode category, e.g. Lu
    TAG,     // Match a type of token.
    DROP,    // Drop unused matched characters, @
    ACT,     // Carry out an action, e.g. @2add

// Extras for generating bytecode:
    EXTEND,  //                  EXTEND n1 OP n2 for two-byte args
    GO,      // id               GO &x
    EITHER,  // x / y            EITHER n <x> OR <y>
    BOTH,    // x y              BOTH n <x> AND <y>
    MAYBE,   // x? or x*         MAYBE OPT/MANY <x>
    DO,      // x+               DO THEN MAYBE MANY <x>
    THEN,
    LOOK,    // [x] or x& or x!  LOOK TRY/HAS/NOT <x>
//    RANGE,   // "a".."z"         RANGE "a" "z" (UTF-8)
    LT,      // <"xyz"           LT "xyz"
    START,   // id = x           START STOP <x>
    STOP,
    ACTN,    // @any             action with sequence number as argument
    ACT0;    // @any             action with sequence number added to bytecode

    public static void main(String[] args) { }
}
