// Pecan 1.0 source. Free and open source. See licence.txt.

package pecan;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;

/* A Source string is a substring of the text from a UTF-8 file, or just a UTF-8
string. In the case of text from a file, the path name is retained, to support
the generation of error messages. The text is normalized, i.e. line endings are
converted to '\n', trailing spaces and trailing blank lines are removed, and a
final newline is added if necessary.
TODO: check well-formed UTF-8, ban control characters.

The Source class is in many ways a replacement for String. Source strings are
stored using byte arrays. Methods are provided for handling UTF-8 text (as an
alternative to using String.codePointAt, Character.charCount etc.) and for
handling Pecan's numerical escapes. If a subsource is preceded by a backslash
and a newline, then escapes are translated automatically.

There is a method for generating an accurate error message with path name, line
number and column information from a source fragment.

The implementation is similar to that of String, except that a source is a
segment of a byte array rather than a char array. In the case of a substring of
text from a file, the byte array contains the path name in the first line,
followed by the entire content of the file. Source strings are immutable, and
the byte arrays involved are not accessible outside this class.

The length of a source string, and positions within it, are measured in bytes.
Positions are assumed to be on code point boundaries. */

class Source {
    private byte[] bytes;
    private int start, end;

    // Construct a Source directly from its fields.
    private Source(byte[] bs, int s, int e) { bytes = bs; start = s; end = e; }

    // Construct a Source from a String.
    Source(String s) {
        bytes = s.getBytes(StandardCharsets.UTF_8);
        start = 0;
        end = bytes.length;
    }

    // Read in a Source from a UTF-8 file. The text is prefixed by "&path\n".
    // and spare bytes filled with '\0'.
    Source(File file) {
        InputStream is = null;
        try { is = new FileInputStream(file); }
        catch (Exception e) { err("can't read ", e); }
        int flen = (int) file.length();
        String path = file.getPath();
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        int plen = pathBytes.length;
        bytes = new byte[1 + plen + 1 + flen + 2];
        bytes[0] = '&';
        System.arraycopy(pathBytes, 0, bytes, 1, plen);
        bytes[plen + 1] = '\n';
        start = plen + 2;
        int r = 0;
        try { r = is.read(bytes, start, flen); is.close(); }
        catch (Exception e) { err("can't read ", e); }
        if (r != flen) err("can't read " + path, null);
        end = start + flen;
        for (int i = end; i < bytes.length; i++) bytes[i] = '\0';
        normalize();
    }

    // Return the length, in bytes.
    int length() { return end - start; }

    // Get the text as a string.
    String text() { return substring(0, end - start); }

    // Get the text as a string.
    public String toString() { return text(); }

    // Get the text as an unescaped string.
    String rawText() {
        StringBuilder sb = new StringBuilder();
        int n = 0, cn = 0;
        while (n < length()) {
            cn = rawLength(n);
            sb.appendCodePoint(rawChar(n, cn));
            n += cn;
        }
        return sb.toString();
    }

    // Construct a subsource, between two given byte-positions.
    Source sub(int s, int e) {
        check(s >= 0 && s <= e && start + e <= end);
        return new Source(bytes, start + s, start + e);
    }

    // Construct a subsource covering two other subsources.
    Source sub(Source s1, Source s2) {
        check(s1.bytes == bytes && s2.bytes == bytes);
        check(start <= s1.start && s1.end <= end);
        check(start <= s2.start && s2.end <= end);
        int b = Math.min(s1.start, s2.start);
        int e = Math.max(s1.end, s2.end);
        return new Source(bytes, b, e);
    }

    // Return a substring.
    String substring(int s, int e) {
        check(s >= 0 && s <= e && e <= end - start);
        return new String(bytes, start+s, e-s, StandardCharsets.UTF_8);
    }

    // Get the next character at position p.
    int charAt(int p) {
        return nextChar(p, nextLength(p));
    }

    // Get the next character at position p, interpreting escapes.
    int rawCharAt(int p) {
        return rawChar(p, rawLength(p));
    }

    // Get the next byte, for ASCII comparisons.
    byte nextByte(int p) {
        check(0 <= p && p < length());
        return bytes[start + p];
    }

    // Get the length of the next UTF-8 character in bytes.
    int nextLength(int p) {
        check(0 <= p && p < length());
        int b = bytes[start + p];
        if ((b & 0x80) == 0) return 1;
        else if ((b & 0xE0) == 0xC0) return 2;
        else if ((b & 0xF0) == 0xE0) return 3;
        else return 4;
    }

    // Get the next UTF-8 character, given its length (assumed correct).
    int nextChar(int p, int length) {
        check(0 <= p && p < length());
        int k = start + p;
        int c = bytes[k];
        if (length > 1) c = c & (0xFF >> length);
        for (int i = 1; i < length; i++) c = (c << 6) | (bytes[k+i] & 0x3F);
        return c;
    }

    // Get the length of the next character, interpreting escapes.
    int rawLength(int p) {
        check(0 <= p && p < length());
        if (bytes[start+p] != '\\') return nextLength(p);
        int k = start + p + 1;
        if (bytes[k] == '0') {
            while ("0123456789ABCDEFabcdef".indexOf(bytes[k]) >= 0) k++;
        }
        else while ('0' <= bytes[k] && bytes[k] <= '9') k++;
        return k - start - p;
    }

    // Get the next character, given its length, interpreting escapes.
    int rawChar(int p, int length) {
        check(0 <= p && p < length());
        if (bytes[start+p] != '\\') return nextChar(p, length);
        int k = start + p + 1;
        length--;
        int v = 0;
        if (bytes[k] == '0') {
            for (int i = k; i < k + length; i++) {
                int d = bytes[i];
                if (d <= '9') d = d -'0';
                else if (d <= 'F') d = d + 10 - 'A';
                else d = d + 10 - 'a';
                v = v * 16 + d;
            }
        }
        else {
            for (int i = k; i < k + length; i++) v = v * 10 + bytes[i] - '0';
        }
        return v;
    }

    // Get the file path where the text originated.
    String path() {
        if (bytes.length == 0 || bytes[0] != '&') return null;
        int n;
        for (n = 1; n < bytes.length; n++) if (bytes[n] == '\n') break;
        return new String(bytes, 1, n-1, StandardCharsets.UTF_8);
    }

    // Find a file path, relative to the path of this source (for inclusions).
    String relativePath(String file) {
        String container = path();
        if (file == null) return null;
        Path path = Paths.get(file);
        if (path.isAbsolute()) return path.toString();
        if (container == null) return path.toString();
        Path p = Paths.get(container);
        if (p.getNameCount() == 1) return path.toString();
        return Paths.get(p.getParent().toString(), path.toString()).toString();
    }

    // Check whether the text starts with a given string, returning the number
    // of bytes matched or -1.
    int startsWith(String s) {
        byte[] bs = s.getBytes(StandardCharsets.UTF_8);
        if (length() < bs.length) return -1;
        for (int j = 0; j < bs.length; j++) {
            if (bs[j] != bytes[start+j]) return -1;
        }
        return bs.length;
    }

    // Check whether the text at p starts with a given string, returning the
    // number of bytes matched or -1.
    int startsWith(String s, int p) {
        check (0 <= p && p <= length());
        byte[] bs = s.getBytes(StandardCharsets.UTF_8);
        if (length() - p < bs.length) return -1;
        for (int j = 0; j < bs.length; j++) {
            if (bs[j] != bytes[start+p+j]) return -1;
        }
        return bs.length;
    }

    // Check whether the text ends with a given string, returning the number
    // of bytes matched or -1.
    int endsWith(String s) {
        byte[] bs = s.getBytes(StandardCharsets.UTF_8);
        if (length() < bs.length) return -1;
        for (int j = 0; j < bs.length; j++) {
            if (bs[j] != bytes[end - bs.length + j]) return -1;
        }
        return bs.length;
    }

    // Find the position of a substring.
    int indexOf(String s) {
        byte[] bs = s.getBytes(StandardCharsets.UTF_8);
        int pos = -1;
        for (int i = start; pos < 0 && i < end - bs.length; i++) {
            boolean reject = false;
            for (int j = 0; ! reject && j < bs.length; j++) {
                if (bs[j] != bytes[i+j]) reject = true;
            }
            if (! reject) pos = i;
        }
        return pos - start;
    }

    // Find the last position of a substring.
    int lastIndexOf(String s) {
        byte[] bs = s.getBytes(StandardCharsets.UTF_8);
        int pos = -1;
        for (int i = end - bs.length; pos < 0 && i >= start; i--) {
            boolean reject = false;
            for (int j = 0; ! reject && j < bs.length; j++) {
                if (bs[j] != bytes[i+j]) reject = true;
            }
            if (! reject) pos = i;
        }
        return pos - start;
    }

    // Produce a list of lines, each including its newline, from a source.
    List<Source> lines() {
        List<Source> list = new ArrayList<>();
        int s = start;
        for (int e = s; e < end; e++) {
            if (bytes[e] == '\n') {
                list.add(new Source(bytes, s, e+1));
                s = e+1;
            }
        }
        if (end > s) list.add(new Source(bytes, s, end));
        return list;
    }

    // Find the start of the file.
    private int fileStart() {
        if (bytes.length == 0 || bytes[0] != '&') return 0;
        int s = 0;
        while (bytes[s] != '\n') s++;
        return s + 1;
    }

    // Find the end of the file.
    private int fileEnd() {
        int e = bytes.length;
        while (e > 0 && bytes[e-1] == '\0') e--;
        return e;
    }

    // Find the line number within the surrounding file.
    int lineNumber() {
        int fs = fileStart(), fe = fileEnd();
        check(fs <= start && end <= fe);
        int r = 1;
        for (int i = fs; i < start; i++) if (bytes[i] == '\n') r++;
        return r;
    }

    // Create an error message referring to this, within its file.
    String error(String message) {
        int fs = fileStart(), fe = fileEnd();
        check(fs <= start && end <= fe);
        int s = start;
        int e = end;
        int[] startRow = new int[3], endRow = new int[3];
        row(startRow, s);
        row(endRow, e);
        if (! message.equals("")) message = " " + message;
        int sl = startRow[1], ll = startRow[2] - sl;
        String line = new String(bytes, sl, ll, StandardCharsets.UTF_8);
        int col = s - startRow[1];
        String s1;
        String path = path();
        if (path == null) s1 = "Error on ";
        else s1 = "Error in " + path + ", ";
        if (endRow[0] == startRow[0]) {
            String s2 = "line " + startRow[0] + ":";
            message = s1 + s2 + message + "\n" + line + "\n";
        } else {
            String s2 = "lines " + startRow[0] + " ";
            String s3 = "to " + endRow[0] + ":";
            message = s1 + s2 + s3 + message + "\n" + line + "...\n";
            s = col;
            e = startRow[2] - startRow[1];
        }
        for (int i = 0; i < col; i++) message += ' ';
        for (int i = 0; i < (e-s); i++) message += '^';
        if (e == s) message += '^';
        return message;
    }

    // Find the row number, start and end of the line containing p.
    private void row(int[] row, int p) {
        int r = 0, s = 0, e = 0;
        for (int i = 0; i < p; i++) if (bytes[i] == '\n') { r++; s = i+1; }
        if (bytes.length == 0 || bytes[0] != '&') r++;
        row[0] = r;
        row[1] = s;
        for (e = p; e < bytes.length; e++) if (bytes[e] == '\n') break;
        row[2] = e;
    }

    // Normalize text. Convert line endings to \n, delete trailing spaces,
    // delete trailing lines, add missing last newline.
    private void normalize() {
        bytes[end++] = '\n';
        int out = start;
        for (int i = start; i < end; i++) {
            byte b = bytes[i];
            if (b != '\r' && b != '\n') { bytes[out++] = b; continue; }
            if (b == '\r' && bytes[i + 1] == '\n') i++;
            while (out >= start && bytes[out - 1] == ' ') out--;
            bytes[out++] = '\n';
        }
        while (out > start && bytes[out - 1] == '\n') out--;
        bytes[out++] = '\n';
        end = out;
    }

    // Like assert, but always switched on.
    private void check(boolean b) {
        if (!b) throw new Error("Check failed");
    }

    private void err(String message, Exception e) {
        if (e != null) message += e.getMessage();
        System.err.println("Error: " + message);
        System.exit(1);
    }

    public static void main(String[] args) {
        Source s = new Source("abc");
        assert(s.bytes.length == 3 && s.start == 0 && s.end == 3);
        assert(s.substring(0,3).equals("abc"));
        s = new Source(new File("pecan/Source.java"));
        assert(s.bytes[0] == '&');
        assert(s.path().equals("pecan/Source.java"));
        assert(s.substring(0,2).equals("//"));
        assert(s.substring(s.length()-2, s.length()).equals("}\n"));
        s = new Source("a\\92b\\03c0x");
        assert(s.nextLength(1) == 1 && s.nextChar(1,1) == '\\');
        assert(s.rawLength(1) == 3 && s.rawChar(1,3) == 92);
        assert(s.rawLength(5) == 5 && s.rawChar(5,5) == 0x3c0);
        s = new Source("&file\nLine one\nLine two\n");
        s = s.sub(6,24);
        String out =
            "Error in file, line 1: message\n" +
            "Line one\n" +
            "^";
        assert(s.sub(0,0).error("message").equals(out));
        out =
            "Error in file, line 2: message\n" +
            "Line two\n" +
            "     ^^^";
        assert(s.sub(14,17).error("message").equals(out));
        out =
            "Error in file, lines 1 to 2: message\n" +
            "Line one...\n" +
            "     ^^^";
        assert(s.sub(5,16).error("message").equals(out));
        s = new Source("Line one\nLine two\n");
        out =
            "Error on line 2: message\n" +
            "Line two\n" +
            "     ^^^";
        assert(s.sub(14,17).error("message").equals(out));
        out =
            "Error on line 3: message\n" +
            "\n" +
            "^";
        assert(s.sub(18,18).error("message").equals(out));
        s = new Source("");
        out =
            "Error on line 1: message\n" +
            "\n" +
            "^";
        assert(s.sub(0,0).error("message").equals(out));
    }
}
