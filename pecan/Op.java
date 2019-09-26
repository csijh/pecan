// Pecan 1.0 operators. Free and open source. See licence.txt.

package pecan;

/* Op constants represent parsing expression operators, and are used to classify
nodes. */

public enum Op {
    Bracketed, // Bracketed subexpression (temporary node during parsing)
    Bracket,   // Bracket (temporary node during parsing)
    Postop,    // Postfix operator (temporary node during parsing)
    Error,     // Parse error, with message in note field
    Include,   // Temporary node representing a file inclusion
    Empty,     // An empty linked list of rules, or end of list.
    List,      // Linked list of rules
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
    Code,      // Character code, 127
    String,    // Character sequence, "abc"
    Set,       // Choice of characters, 'abc'
    Split,     // Lookahead for text less than or equal to string, <...>
    Char,      // Single character, "a" or 'a'
    Success,   // Always succeed, ""
    Fail,      // Always fail, ''
    End,       // End of input, <>
    Range,     // Character range, 'x..y'
    Codes,     // Code range, 0..127
    Cat,       // Unicode category, e.g. Lu
    Tag,       // Match a type of token.
    Drop,      // Drop unused matched characters, @ or @3
    Act;       // Carry out an action, e.g. @2add

    public static void main(String[] args) { }
}
