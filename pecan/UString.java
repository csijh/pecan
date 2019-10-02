// Pecan 1.0 transformer. Free and open source. See licence.txt.

package pecan;
import java.nio.file.*;

/* Provide UTF-8 strings, which directly use byte arrays. This is an alternative
to using String, String.codePointAt, Character.charCount and so on.

UStrings are immutable. Substrings share the same byte array but the the byte
arrays are not accessible from any other class.

The length of a UString and positions within UStrings are in bytes. Positions
are assumed to be on code point boundaries. */

class UString {
    private byte[] bytes;
    private int start, end;

    // Make a copy.
    UString(UString s) {
        bytes = s.bytes;
        start = s.start;
        end = s.end;
    }

    // Construct a UString from a String.
    UString(String s) {
        try { bytes = s.getBytes("UTF-8"); }
        catch (Exception e) { throw new Error(e); }
        end = bytes.length;
    }

    // Read in a UString from a UTF-8 file.
    UString(Path path) {
        try { bytes = Files.readAllBytes(path);
        } catch (Exception e) { throw new Error(e); }
        end = bytes.length;
    }

    // Return the length, in bytes.
    int length() { return end - start; }

    // Construct a substring, between two given byte-positions.
    UString substring(int s, int e) {
        assert(s >= 0 && s <= e && e <= end - start);
        UString sub = new UString(this);
        sub.start = start + s;
        sub.end = start + e;
        return sub;
    }

    public static void main(String[] args) {
        UString s = new UString("abc");
    }
}
