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

Source strings are stored using byte arrays. Methods are provided for handling
UTF-8 text (as an alternative to using String.codePointAt, Character.charCount
etc.). As well as handling multi-byte Unicode characters (code points), the
methods support Pecan's numerical escapes. Many of the methods mirror the
equivalent String methods. There is also a method for generating an accurate
error message with path name, line number and column information.

The implementation is similar to that of String, except that a source is a
segment of a byte array rather than a char array. In the case of a substring of
text from a file, the byte array contains the path name as the first line,
followed by the entire content of the file. Source strings are immutable, and
the byte arrays involved are not accessible outside this class.

The length of a source string, and positions within it, are in bytes. Positions
are assumed to be on code point boundaries. */

class Source {
    private byte[] bytes;
    private int start, end;

    // Structure to hold the value and byte-length of a UTF-8 character.
    static class Char { int value, length; }

    // Structure to hold the line number, column and start/end of a line.
    static class Line { int row, col, start, end; Source source; }

    // Construct a Source directly from its fields.
    private Source(byte[] bs, int s, int e) { bytes = bs; start = s; end = e; }

    // Construct a Source from a String.
    Source(String s) {
        bytes = s.getBytes(StandardCharsets.UTF_8);
        start = 0;
        end = bytes.length;
    }

    // Read in a Source from a UTF-8 file. The text is prefixed by "&path\n"
    Source(File file) {
        InputStream is = null;
        try { is = new FileInputStream(file); }
        catch (Exception e) { err("can't read " + file + ": " + e); }
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
        catch (Exception e) { err("can't read " + file + ": " + e); }
        if (r != flen) err("can't read " + path);
        end = start + flen;
        normalize();
    }

    // Extend the text to include the given text.
    Source extend(Source src) {
        assert(src.bytes == bytes);
        int s = start, e = end;
        if (src.start < s) s = src.start;
        if (src.end > e) e = src.end;
        return new Source(bytes, s, e);
    }

    // Get the file path where the text originated.
    String path() {
        if (bytes.length == 0 || bytes[0] != '&') return null;
        int n;
        for (n = 1; n < bytes.length; n++) if (bytes[n] == '\n') break;
        return new String(bytes, 1, n-1, StandardCharsets.UTF_8);
    }

    // Find a file path, relative to the path of this source, for inclusions.
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

    // Return the text as a string.
    String text() { return substring(0, end - start); }

    // Get the next character at position p.
    static Char temp = new Char();
    int charAt(int p) {
        next(p, temp);
        return temp.value;
    }

    boolean startsWith(String s) {
        byte[] bs = s.getBytes(StandardCharsets.UTF_8);
        if (length() < bs.length) return false;
        for (int j = 0; j < bs.length; j++) {
            if (bs[j] != bytes[start+j]) return false;
        }
        return true;
    }

    boolean endsWith(String s) {
        byte[] bs = s.getBytes(StandardCharsets.UTF_8);
        if (length() < bs.length) return false;
        for (int j = 0; j < bs.length; j++) {
            if (bs[j] != bytes[end - bs.length + j]) return false;
        }
        return true;
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
        return pos;
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
        return pos;
    }

    // Get the next character at position p, with byte-length.
    // Handle numerical escapes.
    void next(int p, Char ch) {
        assert(0 <= p && p < length());
        p += start;
        int v = bytes[p];
        if (v == '\\') { escape(p,ch); return; }
        if ((v & 0x80) == 0) { ch.length = 1; ch.value = v; return; }
        else if ((v & 0xE0) == 0xC0) { ch.length = 2; v = v & 0x3F; }
        else if ((v & 0xF0) == 0xE0) { ch.length = 3; v = v & 0x1F; }
        else if ((v & 0xF8) == 0xF0) { ch.length = 4; v = v & 0x0F; }
        for (int i = 1; i < ch.length; i++) v = (v << 6) | (bytes[p+i] & 0x3F);
        ch.value = v;
    }

    // Get the next character, represented as a numerical escape.
    void escape(int p, Char ch) {
        assert(start <= p && p < end-1 && bytes[start+p] == '\\');
        p += start;
        int n = p + 1;
        int v = 0;
        if (bytes[n] == '0') {
            while ("0123456789ABCDEFabcdef".indexOf(bytes[n]) >= 0) {
                int d = bytes[n];
                if (d <= '9') d = d -'0';
                else if (d <= 'F') d = d + 10 - 'A';
                else d = d + 10 - 'a';
                v = v * 16 + d;
                n++;
            }
        }
        else while (n < bytes.length && '0' <= bytes[n] && bytes[n] <= '9') {
            v = v * 10 + bytes[n] - '0';
            n++;
        }
        ch.value = v;
        ch.length = n - p;
    }

    // Procude a list of lines, including newlines, from a source.
    List<Source> lines() {
        List<Source> list = new ArrayList<>();
        int s = start;
        for (int e = 0; e < end; e++) {
            if (bytes[e] == '\n') {
                list.add(new Source(bytes, s, e+1));
                s = e+1;
            }
        }
        if (end > s) list.add(new Source(bytes, s, end));
        return list;
    }

    // Find the line number of the line containing p.
    int lineNumber(int p) {
        assert(start <= p && p < end);
        p += start;
        int r = 0, s = 0, e = 0;
        for (int i = 0; i < p; i++) if (bytes[i] == '\n') { r++; s = i+1; }
        if (bytes.length == 0 || bytes[0] != '&') r++;
        return r;
    }

    // Find the row number, start and end of the line containing p.
    void line(int p, Line line) {
        assert(start <= p && p < end);
        p += start;
        int r = 0, s = 0, e = 0;
        for (int i = 0; i < p; i++) if (bytes[i] == '\n') { r++; s = i+1; }
        for (e = p; e < bytes.length; e++) if (bytes[e] == '\n') break;
        if (bytes.length == 0 || bytes[0] != '&') r++;
        line.row = r;
        line.col = p - s;
        line.start = s;
        line.end = e;
        line.source = new Source(bytes, s, e);
    }

    // Return the length, in bytes.
    int length() { return end - start; }

    // Construct a substring, between two given byte-positions.
    Source sub(int s, int e) {
        assert(s >= 0 && s <= e && e <= end - start);
        Source sub = new Source(bytes, start + s, start + e);
        return sub;
    }

    String substring(int s, int e) {
        assert(s >= 0 && s <= e && e <= end - start);
        return new String(bytes, start+s, e-s, StandardCharsets.UTF_8);
    }

    // Return an unescaped version of this source.
    Source unescape() {
        byte[] bs = new byte[end - start];
        int out = 0;
        for (int i = start; i < end; i++) {
            byte b = bytes[i];
            if (b != '\\') { bs[out++] = b; continue; }
            escape(i, temp);
            int v = temp.value;
            if (v < 128) bs[out++] = (byte) temp.value;
            else if (v < 0x0800) {
                bs[out++] = (byte) (0xC0 | (v >> 6));
                bs[out++] = (byte) (0x80 | (v & 0x3F));
            }
            else if (v < 0x10000) {
                bs[out++] = (byte) (0xE0 | (v >> 12));
                bs[out++] = (byte) (0x80 | ((v >> 6) & 0x3F));
                bs[out++] = (byte) (0x80 | (v & 0x3F));
            }
            else {
                bs[out++] = (byte) (0xF0 | (v >> 18));
                bs[out++] = (byte) (0x80 | ((v >> 12) & 0x3F));
                bs[out++] = (byte) (0x80 | ((v >> 6) & 0x3F));
                bs[out++] = (byte) (0x80 | (v & 0x3F));
            }
            i = i + temp.length - 1;
        }
        return new Source(bs, 0, out);
    }

    // Create an error message, based on a text range.
    String error(int s, int e, String message) {
        s = start + s;
        e = start + e;
        int[] startRow = new int[3], endRow = new int[3];
        row(startRow, s);
        row(endRow, e);
        if (! message.equals("")) message = " " + message;
        int sl = startRow[1], el = startRow[2];
        String line = new String(bytes, sl, el-sl, StandardCharsets.UTF_8);
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
    // delete trailing lines, complain about missing last newline, complain
    // about control characters.
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

    private void err(String message) {
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
        Source.Char ch = new Source.Char();
        s.next(1,ch);
        assert(ch.value == 92 && ch.length == 3);
        s.next(5,ch);
        assert(ch.value == 0x3c0 && ch.length == 5);
        s = new Source("&file\nLine one\nLine two\n");
        s = s.sub(6,24);
        String out =
            "Error in file, line 1: message\n" +
            "Line one\n" +
            "^";
        assert(s.error(0,0,"message").equals(out));
        out =
            "Error in file, line 2: message\n" +
            "Line two\n" +
            "     ^^^";
        assert(s.error(14,17,"message").equals(out));
        out =
            "Error in file, lines 1 to 2: message\n" +
            "Line one...\n" +
            "     ^^^";
        assert(s.error(5,16,"message").equals(out));
        s = new Source("Line one\nLine two\n");
        out =
            "Error on line 2: message\n" +
            "Line two\n" +
            "     ^^^";
        assert(s.error(14,17,"message").equals(out));
        out =
            "Error on line 3: message\n" +
            "\n" +
            "^";
        assert(s.error(18,18,"message").equals(out));
        s = new Source("");
        out =
            "Error on line 1: message\n" +
            "\n" +
            "^";
        assert(s.error(0,0,"message").equals(out));
    }
}
