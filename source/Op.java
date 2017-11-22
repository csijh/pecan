// Part of Pecan 4. Open source - see licence.txt.

package pecan;

/* Op constants represent parsing expression operators, and are used to classify
nodes. */

public enum Op {
    RULE,    // Definition, name = rhs
    ID,      // Identifier, with cross-reference to its definition
    OR,      // Choice, x / y, right associative
    AND,     // Sequence, x y, right associative
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
    DROP,    // Drop unused matched characters, @
    TAG,     // Match a type of token.
    ACT;     // Carry out an action, e.g. @2add
}
