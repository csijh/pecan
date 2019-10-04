// Pecan 1.0 source text. Free and open source. See licence.txt.

package pecan;
import java.util.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;

/* A source is a string, with a file path and line number, so that error
messages can be generated based on ranges of text. There is a flag to say that
the string represents a default grammar during a series of tests. There is also
a flag to specify tracing during testing. */

class Source {
    private String text;
    private Path path;
    private int firstLine;
    private boolean grammar, trace;
    private int[] rows;

    // The default container is null.
    Source(String t, String file, int n) {
        if (t.length() > 0 && ! t.endsWith("\n")) t += "\n";
        text = t;
        if (file != null) path = Paths.get(file);
        else path = null;
        firstLine = n;
        grammar = trace = false;
        findRows();
    }

    // The default first line is 1.
    Source(String t, String file) {
        this(t, file, 1);
    }

    // The default file is null.
    Source(String t) {
        this(t, null, 1);
    }

    // Find a file path, relative to a containing file, for inclusions.
    static String relativeFile(String container, String file) {
        if (file == null) return null;
        Path path = Paths.get(file);
        if (path.isAbsolute()) return path.toString();
        if (container == null) return path.toString();
        Path p = Paths.get(container);
        if (p.getNameCount() == 1) return path.toString();
        return Paths.get(p.getParent().toString(), path.toString()).toString();
    }

    // Read in a file as a string. Use readAllLines, not readAllBytes, in order
    // to normalize line endings,
    static String readFile(String file) {
        Path path = Paths.get(file);
        try {
            List<String> lines =
                Files.readAllLines(path, StandardCharsets.UTF_8);
            return String.join("\n", lines) + "\n";
        } catch (Exception e) { throw new Error(e); }
    }

    // Get or set the fields.
    String text() { return text; }
    int firstLine() { return firstLine; }
    String fileName() { return path.toString(); }
    boolean grammar() { return grammar; }
    void grammar(boolean b) { grammar = b; }
    boolean trace() { return trace; }
    void trace(boolean b) { trace = b; }

    // Delegate string methods to the text.
    int length() { return text.length(); }
    String substring(int start, int end) { return text.substring(start, end); }
    int indexOf(char ch, int p) { return text.indexOf(ch, p); }
    char charAt(int n) { return text.charAt(n); }
    int codePointAt(int n) { return text.codePointAt(n); }
    boolean startsWith(String s) { return text.startsWith(s); }
    boolean startsWith(String s, int i) { return text.startsWith(s, i); }

    // Get the line number for a given text position.
    int lineNumber(int p) {
        return firstLine + row(p);
    }

    // Replace a substring of the source, to support transformations. Update all
    // the nodes in the given tree, assuming the tree is e.g. a rule where all
    // the nodes are guaranteed to be described by this source. Assume that
    // node source positions inside the new text are ok.
    void replace(int s, int e, String t, Node tree) {
        text = text.substring(0, s) + t + text.substring(e);
        replaceNode(e, s + t.length(), tree);
    }

    // Change the source extent of each node in a tree to match the new text.
    void replaceNode(int e1, int e2, Node node) {
        if (node.source() != this) throw new Error("Wrong source");
        int ns = node.start(), ne = node.end();
        if (ns >= e1) ns = ns + e2 - e1;
        if (ne >= e1) ne = ne + e2 - e1;
        if (node.left() != null) replaceNode(e1, e2, node.left());
        if (node.right() != null) replaceNode(e1, e2, node.right());
    }

    // Create an error message, based on a text range.
    String error(int start, int end, String message) {
        int startRow = row(start);
        int endRow = row(end);
        if (! message.equals("")) message = " " + message;
        int startLine = rows[startRow], endLine = startLine;
        if (startRow < rows.length - 1) endLine = rows[startRow + 1] - 1;
        String line = text.substring(startLine, endLine);
        int col = start - rows[startRow];
        String s1;
        if (path != null) s1 = "Error in " + path + ", ";
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
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        rows = new int[count + 1];
        count = 0;
        rows[count++] = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') rows[count++] = i + 1;
        }
    }

    // Find the row number of the line containing a given position.
    private int row(int p) {
        int row = 0;
        while (row < rows.length && rows[row] <= p) row++;
        return row - 1;
    }

    public static void main(String[] args) {
        Source s = new Source("Line one\nLine two\n", "file");
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
        s = new Source("", null);
        out =
            "Error on line 1: message\n" +
            "\n" +
            "^";
        assert(s.error(0,0,"message").equals(out));
    }
}
