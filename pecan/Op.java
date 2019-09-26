// Pecan 1.0 operators. Free and open source. See licence.txt.

package pecan;

/* Op constants represent parsing expression operators, and are used to classify
nodes. */

public enum Op {
    Bracketed, // Bracketed subexpression (temporary node during parsing)
    Bracket,   // Bracket (temporary node during parsing)
    Op,        // Postfix operator (temporary node during parsing)
    Error,     // Parse error, with message in note field
    Include,   // Temporary node representing a file inclusion
    List,      // Linked list of rules (RHS is rule or list)
    Rule,      // Definition, name = rhs
    Id,        // Identifier, with cross-reference to its definition
    Or,        // Choice, x / y, right associative
    And,       // Sequence, x y, right associative
    Opt,       // Optional, x?
    Any,       // Zero or more, x*
    Some,      // One or more, x+
    Try,       // Lookahead, [e]
    Has,       // Positive lookahead, e&
    Not,       // Negative lookahead, e!
    Mark,      // Error annotation, x #e
    Code,      // Character code
    String,    // Character sequence, "..."
    Set,       // Choice of characters, '...'
    Split,     // Lookahead for text less than or equal to string, <...>
    End,       // End of input, <>
    Range,     // Character range, x..y
    Cat,       // Unicode category, e.g. Lu
    Tag,       // Match a type of token.
    Drop,      // Drop unused matched characters, @
    Act;       // Carry out an action, e.g. @2add

    public static void main(String[] args) { }
}
