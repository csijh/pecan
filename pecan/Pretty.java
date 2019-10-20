// Pecan 1.0 format strings. Free and open source. See licence.txt.

package pecan;
import java.util.*;
import static pecan.Pretty.Tag.*;

/* Provide support for formatted pretty printing. When reading a template
program, call readLine for each attribute line, to set up a format string and
check it for correctness. Then call fillDefaults to fill in defaults for any
attributes that haven't been given. When compiling, call print to print a string
or formatted items. Call text to get the printed text back as a string and clear
the buffer. */

class Pretty {
    // Attributes, and the types of parameter their formats allow (string,
    // expression, character, decimal, newline).
    enum Tag {
        ESCAPE1("c"), ESCAPE2("c"), ESCAPE4("c"), COMMENT("s"), TAB(""),
        RULE("sen"), RULE1("se"), AND(""), OR(""), TRUE(""), FALSE(""),
        CALL("s"), ID("s"), GO(""), OK(""), ALT("e"), OPT("e"), SEE("e"),
        HAS("e"), NOT("e"), TAG("s"), EOT(""), TEXT("s"), SET("s"), SPLIT("s"),
        RANGE("cd"), CAT("s"), MARK("s"), DROP("d"), ACT("ds"), ACT0("s"),
        ACT1("s"), ACT2("s"), ACT3("s"), ACT4("s"), ACT5("s"), ACT6("s"),
        ACT7("s"), ACT8("s"), ACT9("s");
        String allowed;
        Tag(String a) { allowed = a; }
    }

    // Formats as strings, and as split strings.
    String[] formats = new String[Tag.values().length];
    private String[][] splits = new String[Tag.values().length][];

    // The current items while reading an attribute.
    private String line, name, format, error;
    private int lineNumber, nameColumn, formatColumn, errorColumn;
    private Tag tag;

    // Get or set the format for an item.
    private String format(Tag it) { return formats[it.ordinal()]; }
    private void format(Tag it, String x) { formats[it.ordinal()] = x; }

    // The text buffer for printing.
    private StringBuilder text = new StringBuilder();

    // Read an attribute line, extract the info, print an error message.
    public void readLine(int n, String file, String line) {
        boolean ok = readAttribute(line);
        if (ok) ok = checkName(name);
        if (ok) format(tag, format);
        if (ok) return;
        System.err.print("Error on line " + n + " of " + file);
        System.err.println(": " + error);
    }

    // Set the error position and message and return false.
    private boolean err(int col, String s) {
        errorColumn = col;
        error = s;
        return false;
    }

    // Extract the name and format from an attribute line.
    private boolean readAttribute(String line) {
        int s = 0, e = 0, n = line.length();
        while (s < n && ! Character.isLetter(line.charAt(s))) s++;
        if (s == n) return err(s, "expecting attribute");
        e = s;
        while (e < n && Character.isLetter(line.charAt(e))) e++;
        name = line.substring(s, e);
        nameColumn = s;
        s = e;
        while (s < n && line.charAt(s) == ' ') s++;
        if (s == n || line.charAt(s) != '=') return err(s, "expecting equals");
        s++;
        while (s < n && line.charAt(s) == ' ') s++;
        if (s == n) return err(s, "expecting quote");
        char quote = line.charAt(s);
        if (quote != '"' && quote != '\'') return err(s, "expecting quote");
        s++;
        e = s;
        while (e < n && line.charAt(e) != quote) e++;
        if (e == n) return err(n, "expecting end quote");
        format = line.substring(s, e);
        formatColumn = s;
        return true;
    }

    // Check that the attribute name matches one of the allowed names.
    private boolean checkName(String name) {
        for (int i = 0; i < Tag.values().length; i++) {
            Tag t = Tag.values()[i];
            if (name.equals(t.toString().toLowerCase())) {
                tag = t;
                return true;
            }
        }
        errorColumn = nameColumn;
        error = "unrecognized attribute";
        return false;
    }

    // Split a format into verbatim strings and specifiers. A specifier is a
    // percent, possibly followed by a digit, possibly followed by a letter.
    private String[] split(String format) {
        List<String> list = new ArrayList<>();
        int start = 0, n = format.length();
        for (int i = 0; i < n; i++) {
            char ch = format.charAt(i);
            if (ch != '%') continue;
            if (i > start) {
                list.add(format.substring(start,i));
                start = i;
            }
            i++;
            if (i < n && Character.isDigit(format.charAt(i))) i++;
            if (i < n && Character.isLetter(format.charAt(i))) i++;
            String spec = format.substring(start, i);
            start = i;
            list.add(spec);
            i--;
        }
        if (start < n) list.add(format.substring(start,n));
        String[] array = new String[list.size()];
        list.toArray(array);
        return array;
    }

    // Check that each specifier in a format is a generally allowed one.
    // Also check that the specifier is allowed by a particular tag.
    private boolean checkSpecifiers(Tag it, String format) {
        String bad = "bad specifier", ban = "inappropriate specifier";
        String[] parts = split(format);
        int pos = formatColumn;
        for (String s : parts) {
            if (s.startsWith("%")) {
                if (s.length() < 2) return err(pos, bad);
                if (s.length() > 3) return err(pos, bad);
                if (s.length() == 3) {
                    char digit = s.charAt(1);
                    if (digit < '0' || digit > '9') return err(pos, bad);
                    char letter = s.charAt(2);
                    if ("dox".indexOf(letter) < 0) return err(pos, bad);
                    if (it.allowed.indexOf('d') < 0) return err(pos, ban);
                } else {
                    char letter = s.charAt(1);
                    if ("nestfcdox".indexOf(letter) < 0) return err(pos, bad);
                    if (letter == 't' || letter == 'f') letter = 's';
                    if (letter == 'o' || letter == 'x') letter = 'd';
                    if (it.allowed.indexOf(letter) < 0) return err(pos, ban);
                }
            }
            pos += s.length();
        }
        return true;
    }

    // Substitute a name into a call format to produce a new format. Assume the
    // name is all lower case letters.
    private String call(String format, String name) {
        int s = format.indexOf("%s");
        int t = format.indexOf("%t");
        int f = format.indexOf("%f");
        if (s >= 0) return format.replace("%s", name);
        if (t >= 0) return format.replace("%t", name);
        name = name.substring(0,1).toUpperCase() + name.substring(1);
        if (f >= 0) return format.replace("%f", name);
        return format;
    }

    // Substitute a name and (extra) argument into a call format to produce
    // a new format. Change "()" to "(x)" or "...)" to "...,x)" or "..."
    // to "... x". That covers brackets-and-commas and Haskell call styles.
    private String call(String format, String name, String x) {
        format = call(format, name);
        int b = format.indexOf("()");
        int c = format.indexOf(")");
        if (b >= 0) return format.replace("()", "(" + x + ")");
        if (c >= 0) return format.replace(")", "," + x + ")");
        return format + " " + x;
    }

    // Fill in any unspecified defaults. Return null or an error message.
    public void fillDefaults(int n, String file) {
        String rule = format(RULE);
        if (rule == null) {
            System.err.print("Error on line " + n + " of " + file);
            System.err.println(": no rule format");
        }
        int s = 0;
        if (! rule.startsWith(" ")) s = rule.indexOf("%n ") + 2;
        if (s >= 0) {
            int e = s;
            while (e < rule.length() && rule.charAt(e) == ' ') e++;
            format(TAB, rule.substring(s,e));
        }
        if (format(RULE1) == null) format(RULE1, format(RULE));
        if (format(TAB) == null) format(TAB, "  ");
        if (format(AND) == null) format(AND, " && ");
        if (format(OR) == null) format(OR, " || ");
        if (format(TRUE) == null) format(TRUE, "true");
        if (format(FALSE) == null) format(FALSE, "false");
        if (format(CALL) == null) format(CALL, "%s()");
        if (format(ID) == null) format(ID, format(CALL));
        String f = format(CALL);
        if (format(GO) == null) format(GO, call(f, "go"));
        if (format(OK) == null) format(OK, call(f, "ok"));
        if (format(ALT) == null) format(ALT, call(f, "alt", "%e"));
        if (format(OPT) == null) format(OPT, call(f, "opt", "%e"));
        if (format(SEE) == null) format(SEE, call(f, "see", "%e"));
        if (format(HAS) == null) format(HAS, call(f, "has", "%e"));
        if (format(NOT) == null) format(NOT, call(f, "not", "%e"));
        if (format(TAG) == null) format(TAG, call(f, "tag", "%e"));
        if (format(EOT) == null) format(EOT, call(f, "eot"));
        if (format(TEXT) == null) format(TEXT, call(f, "text", "\"%s\""));
        if (format(SET) == null) format(SET, call(f, "set", "\"%s\""));
        if (format(SPLIT) == null) format(SPLIT, call(f, "split", "\"%s\""));
        if (format(RANGE) == null) format(RANGE, call(f, "range", "'%c','%c'"));
        if (format(CAT) == null) format(CAT, call(f, "cat", "\"%s\""));
        if (format(MARK) == null) format(MARK, call(f, "mark", "\"%s\""));
        if (format(DROP) == null) format(DROP, call(f, "drop", "\"%d\""));
        if (format(ACT) == null) format(ACT, call(f, "act%d", "\"%s\""));
        if (format(ACT0) == null) format(ACT0, format(ACT));
        if (format(ACT1) == null) format(ACT1, format(ACT));
        if (format(ACT2) == null) format(ACT2, format(ACT));
        if (format(ACT3) == null) format(ACT3, format(ACT));
        if (format(ACT4) == null) format(ACT4, format(ACT));
        if (format(ACT5) == null) format(ACT5, format(ACT));
        if (format(ACT6) == null) format(ACT6, format(ACT));
        if (format(ACT7) == null) format(ACT7, format(ACT));
        if (format(ACT8) == null) format(ACT8, format(ACT));
        if (format(ACT9) == null) format(ACT9, format(ACT));
        if (format(ESCAPE1) == null) format(ESCAPE1, "\\%3o");
        if (format(ESCAPE2) == null) format(ESCAPE2, "\\u%4x");
        if (format(ESCAPE4) == null) format(ESCAPE4, "\\U%8x");
        for (Tag t : Tag.values()) {
            if (format(t) == null) System.out.println("null for " + t);
            else splits[t.ordinal()] = split(format(t));
        }
    }

    private void test() {
        assert(readAttribute("  name = 'value'  "));
        assert(name.equals("name"));
        assert(format.equals("value"));
        assert(readAttribute("  name = \"value\"  "));
        assert(name.equals("name"));
        assert(format.equals("value"));
        assert(! readAttribute("//  "));
        assert(errorColumn == 4 && error.equals("expecting attribute"));
        assert(! readAttribute("// x"));
        assert(errorColumn == 4 && error.equals("expecting equals"));
        assert(! readAttribute("/* x */"));
        assert(errorColumn == 5 && error.equals("expecting equals"));
        assert(! readAttribute("// x ="));
        assert(errorColumn == 6 && error.equals("expecting quote"));
        assert(! readAttribute("/* x = */"));
        assert(errorColumn == 7 && error.equals("expecting quote"));
        assert(! readAttribute("// x = \"abc"));
        assert(errorColumn == 11 && error.equals("expecting end quote"));
        assert(! readAttribute("// x = 'abc"));
        assert(errorColumn == 11 && error.equals("expecting end quote"));
        assert(checkName("go"));
        assert(tag == GO);
        assert(! checkName("stop"));
        assert(error.equals("unrecognized attribute"));
        String[] x = split("%nabc%d%odef");
        assert(Arrays.equals(x, new String[] {"%n","abc","%d","%o","def"}));
        x = split("abc%4x%?def%");
        assert(Arrays.equals(x, new String[] {"abc","%4x","%","?def","%"}));
        assert(checkSpecifiers(RULE, "%n%e%s%t%f"));
        assert(checkSpecifiers(RANGE, "%c%d%o%x%4d%4o%4x"));
        assert(checkSpecifiers(DROP, "%d%o%x%4d%4o%4x"));
        formatColumn = 10;
        assert(! checkSpecifiers(RULE, "  %"));
        assert(errorColumn == 12 && error.equals("bad specifier"));
        assert(! checkSpecifiers(RULE, "  %wabc"));
        assert(errorColumn == 12 && error.equals("bad specifier"));
        assert(! checkSpecifiers(RULE, "  %4c"));
        assert(errorColumn == 12 && error.equals("bad specifier"));
        assert(! checkSpecifiers(RULE, "  %c"));
        assert(errorColumn == 12 && error.equals("inappropriate specifier"));
        assert(! checkSpecifiers(RULE, "  %d"));
        assert(errorColumn == 12 && error.equals("inappropriate specifier"));
        assert(! checkSpecifiers(RANGE, "  %s"));
        assert(errorColumn == 12 && error.equals("inappropriate specifier"));
        assert(! checkSpecifiers(DROP, "  %c"));
        assert(errorColumn == 12 && error.equals("inappropriate specifier"));
        assert(call("%s()","go").equals("go()"));
        assert(call("p%f(s)","go").equals("pGo(s)"));
        assert(call("%s()","alt","%e").equals("alt(%e)"));
        assert(call("%s(p)","alt","%e").equals("alt(p,%e)"));
        assert(call("%s p","alt","%e").equals("alt p %e"));
    }

    public static void main(String[] args) {
        Pretty f = new Pretty();
        f.test();
    }
}
