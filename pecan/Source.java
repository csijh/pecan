// Pecan 1.0 source. Free and open source. See licence.txt.

package pecan;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/* A Source string is a substring of the text from a UTF-8 file, or just a UTF-8
string. In the case of text from a file, the path name is retained, to support
the generation of error messages. The text is normalized, i.e. line endings are
converted to '\n', trailing spaces and trailing blank lines are removed, and a
final newline is added if necessary.

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

    // Invalid UTF-8 byte to mark filename prefix or unused bytes.
    private static byte MARK = (byte) 0xFF;

    // Construct a Source directly from its fields.
    private Source(byte[] bs, int s, int e) { bytes = bs; start = s; end = e; }

    // Construct a Source from a String.
    Source(String s) {
        bytes = s.getBytes(UTF_8);
        start = 0;
        end = bytes.length;
        String e = checkUTF_8(bytes, start, end);
        if (e != null) err("Error: string contains " + e, null);
    }

    // Read in a Source from a UTF-8 file. Prefix the text by "&path\n" and fill
    // spare bytes.
    Source(File file) {
        InputStream is = null;
        try { is = new FileInputStream(file); }
        catch (Exception e) { err("can't read ", e); }
        int flen = (int) file.length();
        String path = file.getPath();
        byte[] pathBytes = path.getBytes(UTF_8);
        int plen = pathBytes.length;
        bytes = new byte[1 + plen + 1 + flen + 2];
        bytes[0] = MARK;
        System.arraycopy(pathBytes, 0, bytes, 1, plen);
        bytes[plen + 1] = '\n';
        start = plen + 2;
        int r = 0;
        try { r = is.read(bytes, start, flen); is.close(); }
        catch (Exception e) { err("can't read ", e); }
        if (r != flen) err("can't read " + path, null);
        end = start + flen;
        for (int i = end; i < bytes.length; i++) bytes[i] = MARK;
        normalize();
        String e = checkUTF_8(bytes, start, end);
        if (e != null) err("Error: file contains " + e, null);
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
        return new String(bytes, start+s, e-s, UTF_8);
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
        if (bytes.length == 0 || bytes[0] != MARK) return null;
        int n;
        for (n = 1; n < bytes.length; n++) if (bytes[n] == '\n') break;
        return new String(bytes, 1, n-1, UTF_8);
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
    boolean startsWith(String s) {
        return match(s, 0) > 0;
    }

    // Check whether the text at p starts with a given string.
    boolean startsWith(String s, int p) {
        return match(s, p) > 0;
    }

    // Check whether the text ends with a given string.
    boolean endsWith(String s) {
        byte[] bs = s.getBytes(UTF_8);
        return match(bs, length() - bs.length) > 0;
    }

    // Match a string at position p, returning the number of bytes matched or 0.
    int match(String s, int p) {
        check (0 <= p && p <= length());
        byte[] bs = s.getBytes(UTF_8);
        return match(bs, p);
    }

    // Match a byte array at position p, returning the number of bytes matched.
    int match(byte[] bs, int p) {
        check (0 <= p && p <= length());
        if (length() - p < bs.length) return 0;
        for (int j = 0; j < bs.length; j++) {
            if (bs[j] != bytes[start+p+j]) return 0;
        }
        return bs.length;
    }

    // Find the position of a substring.
    int indexOf(String s) {
        byte[] bs = s.getBytes(UTF_8);
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
        byte[] bs = s.getBytes(UTF_8);
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
        if (bytes.length == 0 || bytes[0] != MARK) return 0;
        int s = 0;
        while (bytes[s] != '\n') s++;
        return s + 1;
    }

    // Find the end of the file.
    private int fileEnd() {
        int e = bytes.length;
        while (e > 0 && bytes[e-1] == MARK) e--;
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
        String line = new String(bytes, sl, ll, UTF_8);
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
        if (bytes.length == 0 || bytes[0] != MARK) r++;
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

    // Check that a, b form a valid character code (8 to 11 bits).
    // Byte values are passed as int to represent them as unsigned.
    private static boolean check2(int a, int b) {
        return ((0xC2 <= a && a <= 0xDF) && (0x80 <= b && b <= 0xBF));
    }

    // Check that a, b, c are valid (12..16 bits) excluding surrogates.
    // Byte values are passed as int to represent them as unsigned.
    private static boolean check3(int a, int b, int c) {
        if (a == 0xE0) {
            if ((0xA0 <= b && b <= 0xBF) && (0x80 <= c && c <= 0xBF)) return true;
        }
        else if ((0xE1 <= a && a <= 0xEC) || a == 0xEE || a == 0xEF) {
            if ((0x80 <= b && b <= 0xBF) && (0x80 <= c && c <= 0xBF)) return true;
        }
        else if (a == 0xED) {
            if ((0x80 <= b && b <= 0x9F) && (0x80 <= c && c <= 0xBF)) return true;
        }
        return false;
    }

    // Check that a, b, c, d are valid (17..21 bits up to 1114111).
    // Byte values are passed as int to represent them as unsigned.
    private static boolean check4(int a, int b, int c, int d) {
        if (a == 0xF0) {
            if ((0x90 <= b && b <= 0xBF) &&
            (0x80 <= c && c <= 0xBF) &&
            (0x80 <= d && d <= 0xBF)) return true;
        }
        else if (0xF1 <= a && a <= 0xF3) {
            if ((0x80 <= b && b <= 0xBF) &&
            (0x80 <= c && c <= 0xBF) &&
            (0x80 <= d && d <= 0xBF)) return true;
        }
        else if (a == 0xF4) {
            if ((0x80 <= b && b <= 0x8F) &&
            (0x80 <= c && c <= 0xBF) &&
            (0x80 <= d && d <= 0xBF)) return true;
        }
        return false;
    }

    // Check that a byte array contains valid, control-free UTF-8 text.
    // Return an error message or null. Use int for unsigned byte values.
    private static String checkUTF_8(byte[] bs, int start, int end) {
        int a, b, c, d;
        for (int i = start; i < end; i++) {
            a = bs[i] & 0xFF;
            if (' ' <= a && a <= '~') continue;
            if (a == '\r' || a == '\n') continue;
            if (a == '\0') return "nulls";
            if (a == '\t') return "tabs";
            if (a < 0x80) return "control characters";
            b = bs[++i] & 0xFF;
            if (check2(a, b)) continue;
            c = bs[++i] & 0xFF;
            if (check3(a, b, c)) continue;
            d = bs[++i] & 0xFF;
            if (check4(a, b, c, d)) continue;
            return "invalid UTF-8";
        }
        return null;
    }

    private static void testCheck2() {
        assert(check2(0xC2, 0x80));   // 8 bits
        assert(check2(0xC2, 0xBF));
        assert(check2(0xDF, 0x80));   // 11 bits
        assert(check2(0xDF, 0xBF));
        assert(! check2(0xC0, 0xBF)); // < 8 bits
        assert(! check2(0xC1, 0xBF));
        assert(! check2(0xC2, 0x7F)); // bad 2nd byte
        assert(! check2(0xC2, 0xC0));
        assert(! check2(0xE0, 0xBF)); // > 11 bits
    }

    private static void testCheck3() {
        assert(check3(0xE0, 0xA0, 0x80));   // 12 bits
        assert(check3(0xE0, 0xBF, 0xBF));
        assert(check3(0xE8, 0x80, 0x80));   // 15 bits
        assert(check3(0xEF, 0xBF, 0xBF));
        assert(! check3(0xE0, 0x9F, 0xBF)); // < 12 bits
        assert(! check3(0xED, 0xA0, 0x80)); // UTF-16 surrogates
        assert(! check3(0xED, 0xBF, 0xBF)); // UTF-16 surrogates
        assert(! check3(0xF0, 0x80, 0x80)); // > 15 bits
    }

    private static void testCheck4() {
        assert(check4(0xF0, 0x90, 0x80, 0x80));   // 16 bits
        assert(check4(0xF4, 0x8F, 0xBF, 0xBF));   // limit 1114111
        assert(! check4(0xF0, 0x8F, 0xBF, 0xBF)); // < 16 bits
        assert(! check4(0xF4, 0x90, 0x80, 0x80)); // > limit
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
        testCheck2();
        testCheck3();
        testCheck4();
        Source s = new Source("abc");
        assert(s.bytes.length == 3 && s.start == 0 && s.end == 3);
        assert(s.substring(0,3).equals("abc"));
        s = new Source(new File("pecan/Source.java"));
        assert(s.bytes[0] == MARK);
        assert(s.path().equals("pecan/Source.java"));
        assert(s.substring(0,2).equals("//"));
        assert(s.substring(s.length()-2, s.length()).equals("}\n"));
        s = new Source("a\\92b\\03c0x");
        assert(s.nextLength(1) == 1 && s.nextChar(1,1) == '\\');
        assert(s.rawLength(1) == 3 && s.rawChar(1,3) == 92);
        assert(s.rawLength(5) == 5 && s.rawChar(5,5) == 0x3c0);
        s = new Source("&file\nLine one\nLine two\n");
        s.bytes[0] = MARK;
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
