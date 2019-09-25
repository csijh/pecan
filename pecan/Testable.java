// Pecan 1.0 test interface. Free and open source. See licence.txt.

package pecan;

/* A pecan class implements this interface to allow generic testing using the
Test class. */

interface Testable {
    // Run a test on an object. The input is a source, i.e. a string with
    // filename and start line number for error messages. If the grammar flag is
    // set on the input, it is a default grammar to be used for subsequent
    // tests. The output is a string, or has a meaningful toString value.
    Object run(Source in);
}
