// Pecan 1.0 source text. Free and open source. See licence.txt.

package pecan;
import java.util.*;

/* Store a string representing a grammar, usually the contents or partial
contents of a file, and generate error messages based on ranges of text. */

class Source {
    private String text;
    private String filename;
    private int firstLine;
    private int[] rows;

    Source(String t, String f, int n) {
        text = t;
        filename = f;
        firstLine = n;
    }

    // Delegate string methods to the text.
    String substring(int start, int end) { return text.substring(start, end); }
    char charAt(int n) { return text.charAt(n); }
    int indexOf(char ch, int p) { return text.indexOf(ch, p); }

    // Get the line number for a given text position.
    int lineNumber(int p) {
        return firstLine + row(p);
    }

    // Create an error message, based on a range.
    String error(int start, int end, String message) {
        if (rows == null) findRows();
        int startRow = row(start);
        int endRow = row(end);
        if (! message.equals("")) message = " " + message;
        String line = text.substring(rows[startRow], rows[startRow + 1] - 1);
        int col = start - rows[startRow];
        if (endRow == startRow) {
            String s1 = "Error in " + filename + ", ";
            String s2 = "line " + (firstLine + startRow) + ":";
            message = s1 + s2 + message + "\n" + line + "\n";
        } else {
            String s1 = "Error in " + filename + ", ";
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
    }
}
