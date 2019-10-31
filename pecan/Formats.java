// Pecan 1.0 print formats. Free and open source. See licence.txt.

package pecan;

import java.util.*;
import static pecan.Op.*;
import static pecan.Node.Flag.*;
import static pecan.Node.Count.*;
import static pecan.Formats.Attribute.*;

/* Extract printf-style formats from attributes of the <pecan> tag in a template
program. Call readLine for each attribute line, to establish the language and
application specific print formats. Then call fillDefaults. Then extract the
formats with the get method. */

class Formats {
    private String[] formats = new String[Attribute.values().length];

    // Attributes, and the types of parameter their formats allow (string,
    // left, right, character, decimal, newline).
    enum Attribute {
        DECLARE("s"), COMMENT("s"), DEFINE("slrn"), TAB(""),
        AND(""), OR(""), TRUE(""), FALSE(""),
        CALL("s"), ID("s"), ESCAPE1("c"), ESCAPE2("c"), ESCAPE4("c"),
        ACT("ds"), ACT0("s"), ACT1("s"), ACT2("s"), ACT3("s"), ACT4("s"),
        ACT5("s"), ACT6("s"), ACT7("s"), ACT8("s"), ACT9("s"),
        GO(""), OK(""), ALT("l"), OPT("l"), SEE("l"),
        HAS("l"), NOT("l"), TAG("s"), EOT(""), STRING("s"), SET("s"),
        SPLIT("s"), RANGE("cd"), CAT("s"), MARK("s"), DROP("d");
        String allowed;
        Attribute(String a) { allowed = a; }
    }

    // Set the format for a particular attribute.
    void set(Attribute it, String x) { formats[it.ordinal()] = x; }

    // Get the format for a particular attribute.
    String get(Attribute it) { return formats[it.ordinal()]; }

    // Get the format for an action with a given arity.
    String getAct(int a) {
        String f;
        if (a < 10) f = formats[ACT0.ordinal() + a];
        else f = get(ACT);
        if (f == null) f = get(ACT);
        return f;
    }

    // Read an attribute line, extract the info, print an error message.
    void readLine(int n, String file, String line) {
        String s = readAttribute(line);
        if (s.startsWith("Error ")) report(n, file, s.substring(6));
        String name = s.substring(0, s.indexOf(' '));
        String format = s.substring(s.indexOf(' ') + 1);
        Attribute tag = findAttribute(name);
        if (tag == null) report(n, file, "unrecognized attribute");
        s = checkFormat(tag, format);
        if (s != null) report(n, file, s);
        set(tag, format);
    }

    // Extract the name and format from an attribute line. Return a string:
    // "name format" or an error: "Error message".
    private String readAttribute(String line) {
        String name, format;
        int s = 0, e = 0, n = line.length();
        while (s < n && ! Character.isLetter(line.charAt(s))) s++;
        if (s == n) return "Error expecting attribute";
        e = s;
        while (e < n && Character.isLetterOrDigit(line.charAt(e))) e++;
        name = line.substring(s, e);
        s = e;
        while (s < n && line.charAt(s) == ' ') s++;
        if (s == n || line.charAt(s) != '=') return "Error expecting equals";
        s++;
        while (s < n && line.charAt(s) == ' ') s++;
        if (s == n) return "Error expecting quote";
        char quote = line.charAt(s);
        if (quote != '"' && quote != '\'') return "Error expecting quote";
        s++;
        e = s;
        while (e < n && line.charAt(e) != quote) e++;
        if (e == n) return "Error expecting end quote";
        format = line.substring(s, e);
        return name + " " + format;
    }

    // Check that the attribute name matches one of the allowed names.
    private Attribute findAttribute(String name) {
        for (int i = 0; i < Attribute.values().length; i++) {
            Attribute t = Attribute.values()[i];
            if (name.equals(t.toString().toLowerCase())) {
                return t;
            }
        }
        return null;
    }

    // Check that each specifier in a format is a generally allowed one.
    // Also check that the specifier is allowed by a particular tag.
    private String checkFormat(Attribute it, String format) {
        String bad = "bad specifier ", ban = "inapplicable specifier ";
        String[] parts = split(format);
        for (String s : parts) {
            if (s.startsWith("%")) {
                if (s.length() < 2) return bad + s;
                if (s.length() > 3) return bad + s;
                if (s.length() == 3) {
                    char digit = s.charAt(1);
                    if (digit < '0' || digit > '9') return bad + s;
                    char letter = s.charAt(2);
                    if ("dox".indexOf(letter) < 0) return bad + s;
                    if (it.allowed.indexOf('d') < 0) return ban + s;
                } else {
                    char letter = s.charAt(1);
                    if ("nlrstfcdox".indexOf(letter) < 0) return bad + s;
                    if (letter == 't' || letter == 'f') letter = 's';
                    if (letter == 'o' || letter == 'x') letter = 'd';
                    if (it.allowed.indexOf(letter) < 0) return ban + s;
                }
            }
        }
        return null;
    }

    // Fill in any unspecified defaults. Return null or an error message.
    public void fillDefaults(int n, String file) {
        if (get(DECLARE) == null) set(DECLARE, "");
        if (get(COMMENT) == null) set(COMMENT, "");
        if (get(DEFINE) == null) {
            set(DEFINE, "bool %l() { %n%treturn %r; %n}");
        }
        String rule = get(DEFINE);
        int s = 0;
        if (! rule.startsWith(" ")) s = rule.indexOf("%n ") + 2;
        if (s >= 0) {
            int e = s;
            while (e < rule.length() && rule.charAt(e) == ' ') e++;
            set(TAB, rule.substring(s,e));
        }
        if (get(TAB) == null) set(TAB, "  ");
        if (get(AND) == null) set(AND, "&&");
        if (get(OR) == null) set(OR, "||");
        if (get(TRUE) == null) set(TRUE, "true");
        if (get(FALSE) == null) set(FALSE, "false");
        if (get(CALL) == null) set(CALL, "%s()");
        if (get(ID) == null) set(ID, get(CALL));
        String f = get(CALL);
        if (get(GO) == null) set(GO, call(f, "go"));
        if (get(OK) == null) set(OK, call(f, "ok"));
        if (get(ALT) == null) set(ALT, call(f, "alt", "%l"));
        if (get(OPT) == null) set(OPT, call(f, "opt", "%l"));
        if (get(SEE) == null) set(SEE, call(f, "see", "%l"));
        if (get(HAS) == null) set(HAS, call(f, "has", "%l"));
        if (get(NOT) == null) set(NOT, call(f, "not", "%l"));
        if (get(TAG) == null) set(TAG, call(f, "tag", "%s"));
        if (get(EOT) == null) set(EOT, call(f, "eot"));
        if (get(STRING) == null) set(STRING, call(f, "string", "\"%s\""));
        if (get(SET) == null) set(SET, call(f, "set", "\"%s\""));
        if (get(SPLIT) == null) set(SPLIT, call(f, "split", "\"%s\""));
        if (get(RANGE) == null) set(RANGE, call(f, "range", "'%c','%c'"));
        if (get(CAT) == null) set(CAT, call(f, "cat", "%s"));
        if (get(MARK) == null) set(MARK, call(f, "mark", "%s"));
        if (get(DROP) == null) set(DROP, call(f, "drop", "%d"));
        if (get(ACT) == null) set(ACT, call(f, "act%d", "%s"));
        if (get(ACT0) == null) set(ACT0, get(ACT));
        if (get(ACT1) == null) set(ACT1, get(ACT));
        if (get(ACT2) == null) set(ACT2, get(ACT));
        if (get(ACT3) == null) set(ACT3, get(ACT));
        if (get(ACT4) == null) set(ACT4, get(ACT));
        if (get(ACT5) == null) set(ACT5, get(ACT));
        if (get(ACT6) == null) set(ACT6, get(ACT));
        if (get(ACT7) == null) set(ACT7, get(ACT));
        if (get(ACT8) == null) set(ACT8, get(ACT));
        if (get(ACT9) == null) set(ACT9, get(ACT));
        if (get(ESCAPE1) == null) set(ESCAPE1, "\\u%4x");
        if (get(ESCAPE2) == null) set(ESCAPE2, "\\u%4x");
        if (get(ESCAPE4) == null) set(ESCAPE4, "\\U%8x");
        for (Attribute t : Attribute.values()) {
            if (get(t) == null) System.out.println("null for " + t);
        }
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



    private void report(int n, String file, String error) {
        System.err.print("Error on line " + n + " of " + file);
        System.err.println(": " + error);
        System.exit(1);
    }

    private void test() {
        assert(readAttribute("  name = 'value'  ").equals("name value"));
        assert(readAttribute("  name = \"value\"  ").equals("name value"));
        assert(readAttribute("//  ").equals("Error expecting attribute"));
        assert(readAttribute("// x").equals("Error expecting equals"));
        assert(readAttribute("/* x */").equals("Error expecting equals"));
        assert(readAttribute("// x =").equals("Error expecting quote"));
        assert(readAttribute("/* x = */").equals("Error expecting quote"));
        assert(readAttribute("// x = \"a").equals("Error expecting end quote"));
        assert(readAttribute("// x = 'a").equals("Error expecting end quote"));
        assert(findAttribute("go") == GO);
        assert(findAttribute("stop") == null);
        String[] x = split("%nabc%d%odef");
        assert(Arrays.equals(x, new String[] {"%n","abc","%d","%o","def"}));
        x = split("abc%4x%?def%");
        assert(Arrays.equals(x, new String[] {"abc","%4x","%","?def","%"}));
        assert(checkFormat(DEFINE, "%n%l%s%t%f") == null);
        assert(checkFormat(RANGE, "%c%d%o%x%4d%4o%4x") == null);
        assert(checkFormat(DROP, "%d%o%x%4d%4o%4x") == null);
        assert(checkFormat(DEFINE, "%").equals("bad specifier %"));
        assert(checkFormat(DEFINE, "%wabc").equals("bad specifier %w"));
        assert(checkFormat(DEFINE, "%4c").equals("bad specifier %4c"));
        assert(checkFormat(DEFINE, "%c").equals("inapplicable specifier %c"));
        assert(checkFormat(DEFINE, "%d").equals("inapplicable specifier %d"));
        assert(checkFormat(RANGE, "%s").equals("inapplicable specifier %s"));
        assert(checkFormat(DROP, "%c").equals("inapplicable specifier %c"));
        assert(call("%s()","go").equals("go()"));
        assert(call("p%f(s)","go").equals("pGo(s)"));
        assert(call("%s()","alt","%l").equals("alt(%l)"));
        assert(call("%s(p)","alt","%l").equals("alt(p,%l)"));
        assert(call("%s p","alt","%l").equals("alt p %l"));
    }

    public static void main(String[] args) {
        Formats formats = new Formats();
        formats.test();
    }
}
