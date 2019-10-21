// Pecan 1.0 test interface. Free and open source. See licence.txt.

package pecan;

/* A pecan class implements this interface to allow generic testing using the
Test class. */

interface Testable {
    // Run a test on an object. The input is a source, i.e. a string, or a
    // substring of text from a file allowing error messages to be generated.
    // The output is a string, or has a meaningful toString value.
    Object run(Source in);

    // Call to set up a default grammar for subsequent tests.
    default void grammar(Source g) {}

    // Call to switch on tracing.
    default void tracing(boolean on) {}

    // No testing.
    public static void main(String[] args) {}
}
