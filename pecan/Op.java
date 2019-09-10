// Pecan 1.0 operators. Free and open source. See licence.txt.

package pecan;

/* Op constants represent parsing expression operators, and are used to classify
nodes. */

public enum Op {
    Error,   // Parse error, with message in note field
    Rule,    // Definition, name = rhs, text is name, child is rhs
    Bracket, // Bracketed subexpression (temporary node during parsing)
    Id,      // Identifier, with cross-reference to its definition
    Or,      // Choice, x / y, right associative
    And,     // Sequence, x y, right associative
    Opt,     // Optional, x?
    Any,     // Zero or more, x*
    Some,    // One or more, x+
    Try,     // Lookahead, [e]
    Has,     // Positive lookahead, e&
    Not,     // Negative lookahead, e!
    Mark,    // Error annotation, x #e
    Number,  // Single character, as a number
    String,  // Character sequence, "..."
    Set,     // Choice of characters, '...'
    Divider, // Lookahead for text less than string, <...>
    End,     // End of input, <>
    Range,   // Character range, x..y
    Cat,     // Unicode category, e.g. Lu
    Tag,     // Match a type of token.
    Drop,    // Drop unused matched characters, @
    Act;     // Carry out an action, e.g. @2add

    public static void main(String[] args) { }
}
