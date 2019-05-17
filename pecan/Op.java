// Pecan 5 operators. Free and open source. See licence.txt.

package pecan;

/* Op constants represent parsing expression operators, and are used to classify
nodes. Later ops are used to generate bytecode. */

public enum Op {
    Rule,    // Definition, name = rhs, text is name, child is rhs
    Id,      // Identifier, with cross-reference to its definition
    Or,      // Choice, x / y, right associative
    And,     // Sequence, x y, right associative
    Opt,     // Optional, x?
    Many,    // Zero or more, x*
    Some,    // One or more, x+
    Try,     // Lookahead, [e]
    Has,     // Positive lookahead, e&
    Not,     // Negative lookahead, e!
    Mark,    // Error annotation, x #e
    Char,    // Single unicode character as a number, e.g. 13 or 0D
    String,  // Character sequence, "..."
    Set,     // Choice of characters, '...'
    Range,   // Character range, x..y
    Cat,     // Unicode category, e.g. Lu
    Tag,     // Match a type of token.
    Drop,    // Drop unused matched characters, @
    Act;     // Carry out an action, e.g. @2add

    public static void main(String[] args) { }
}
