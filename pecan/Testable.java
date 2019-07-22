// Pecan 1.0 test interface. Free and open source. See licence.txt.

package pecan;

/* A pecan class implements this interface to allow generic testing using the
Test class. For many classes, the test input is a grammar, but for some classes
and for user testing, the input is a grammar and an input string. */

interface Testable {
    // Set up a grammar for subsequent tests.
    void grammar(String g);
    // Run a test on an object, with input and output expressed as strings.
    String test(String in);
}
