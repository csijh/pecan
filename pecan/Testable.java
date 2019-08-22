// Pecan 1.0 test interface. Free and open source. See licence.txt.

package pecan;

/* A pecan class implements this interface to allow generic testing using the
Test class. */

interface Testable {
    // Run a test on an object, with input and output expressed as strings.
    // If the input is prefixed with "GRAMMAR:\n" it is a grammar to be used
    // for subsequent tests.
    String test(String in);
}
