// Pecan 1.0 source text. Free and open source. See licence.txt.

package pecan;
import java.util.*;

/* A source is a string, with a filename and line number, so that error messages
can be generated based on ranges of text. There is a flag to say that the
string represents a default grammar during a series of tests. There is also a
flag to specify tracing during testing. */

class Source {
    private String text;
    private String fileName;
    private int firstLine;
    private boolean grammar, trace;
    private int[] rows;

    // Create a source object. The filename can be null.
    Source(String t, String f, int n) {
        text = t;
        fileName = f;
        firstLine = n;
        grammar = trace = false;
        findRows();
    }

    // Get or set the fields.
    int firstLine() { return firstLine; }
    String fileName() { return fileName; }
    boolean grammar() { return grammar; }
    void grammar(boolean b) { grammar = b; }
    boolean trace() { return trace; }
    void trace(boolean b) { trace = b; }

    // Delegate string methods to the text.
    String substring(int start, int end) { return text.substring(start, end); }
    char charAt(int n) { return text.charAt(n); }
    int indexOf(char ch, int p) { return text.indexOf(ch, p); }

    // Get the line number for a given text position.
    int lineNumber(int p) {
        return firstLine + row(p);
    }

    // Create an error message, based on a text range.
    String error(int start, int end, String message) {
        int startRow = row(start);
        int endRow = row(end);
        if (! message.equals("")) message = " " + message;
        String line = text.substring(rows[startRow], rows[startRow + 1] - 1);
        int col = start - rows[startRow];
        String s1;
        if (fileName != null) s1 = "Error in " + fileName + ", ";
        else s1 = "Error on ";
        if (endRow == startRow) {
            String s2 = "line " + (firstLine + startRow) + ":";
            message = s1 + s2 + message + "\n" + line + "\n";
        } else {
            String s2 = "lines " + (firstLine + startRow) + " ";
            String s3 = "to " + (firstLine + endRow) + ":";
            message = s1 + s2 + s3 + message + "\n" + line + "...\n";
            start = col;
            end = line.length();
        }
        for (int i = 0; i < col; i++) message += ' ';
        for (int i = 0; i < (end-start); i++) message += '^';
        if (end == start) message += '^';
        return message;
    }

    // Find the positions where lines start
    private void findRows() {
        int count = 1;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\n') count++;
        }
        rows = new int[count + 1];
        count = 0;
        rows[count++] = 0;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\n') rows[count++] = i + 1;
        }
        rows[count] = text.length();
    }

    // Find the row number of the line containing a given position.
    private int row(int p) {
        int row = 0;
        while (rows[row] < p) row++;
        return row - 1;
    }

    public static void main(String[] args) {
        Source s = new Source("Line one\nLine two\n", "file", 1);
        String out =
            "Error in file, line 2: message\n" +
            "Line two\n" +
            "     ^^^";
        assert(s.error(14,17,"message").equals(out));
        out =
            "Error in file, lines 1 to 2: message\n" +
            "Line one...\n" +
            "     ^^^";
        assert(s.error(5,16,"message").equals(out));
        s.fileName = null;
        out =
            "Error on line 2: message\n" +
            "Line two\n" +
            "     ^^^";
        assert(s.error(14,17,"message").equals(out));
    }
}
