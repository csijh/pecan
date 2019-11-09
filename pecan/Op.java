// Pecan 1.0 operators. Free and open source. See licence.txt.

package pecan;

/* Op constants represent parsing expression operators, and are used to classify
nodes. */

public enum Op {
    Error,     // Parse error, with message in note field
    Temp,      // Temporary node used during parsing
    List,      // Linked list of rules
    Empty,     // Empty linked list
    Rule,      // Definition, name = rhs
    Id,        // Identifier, with cross-reference to its definition
    Or,        // Choice, x / y, right associative
    And,       // Sequence, x y, right associative
    Opt,       // Optional, x?
    Any,       // Zero or more, x*
    Some,      // One or more, x+
    See,       // Lookahead, [e]
    Has,       // Positive lookahead, e&
    Not,       // Negative lookahead, e!
    Mark,      // Error annotation, x #e
    Text,      // Character sequence, "abc"
    Set,       // Choice of characters, 'abc'
    Split,     // Lookahead for text less than or equal to string, <...>
    Char,      // Single character, "a" or 'a'
    Point,     // Any Unicode character (code point)
    Success,   // Always succeed, ""
    Fail,      // Always fail, ''
    Eot,       // End of text or tokens, <>
    Range,     // Character range, 'x..y'
    Cat,       // Unicode category, e.g. Lu
    Tag,       // Match a type of token.
    Drop,      // Drop unused matched characters, e.g. @ or @3
    Act;       // Carry out an action, e.g. @2add

    // No testing.
    public static void main(String[] args) {
        System.out.println("Op class OK");
    }
}
