// Pecan 1.0 Unicode category support. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.util.*;
import static java.lang.Character.*;

/* Provide support for Unicode general categories, as used in grammars. Although
Java supports Unicode categories, the version of the standard supported varies,
e.g. Java 8 supports Unicode 6.2. This class supports Unicode 12.0, and makes
Pecan independent of the version of Java used to compile it.

Constants are provided for the Unicode general categories. The name of each
category is its two letter abbreviation, as defined by the Unicode standard and
as used in grammars. Its ordinal is the same as its Java type code, as in
Character.getType. One further constant is added, namely Uc representing all
Unicode characters. Each category has a bitset giving its ascii content.

The main method with no command line arguments carries out tests. The main
method with argument -g generates two arrays of bytes, as binary files
table1.bin and table2.bin. They are read in and used as a two-stage table for
looking up the general category of a character.

The files are generated from UnicodeData.txt, which has been copied from the
Unicode standard at http://www.unicode.org/Public/12.0.0/ucd/UnicodeData.txt.
The use of multi-stage tables is described in Chapter 5 of the Unicode standard.
The files can also be used by parsers generated or written in other languages.

The first array contains unsigned bytes which are indexes of blocks in the
second table. The second array consists of all the distinct 256-byte blocks from
the notional full table. By avoiding repetition of identical blocks, the tables
take up about 40K, instead of 1M for the full table. A category can be found
from these tables from an integer code point ch using one of these formulas.

    table2[(table1[ch>>8]&255)*256+(ch&255)];
    table2[table1[ch>>8]*256+(ch&255)];

The first is for languages like Java which have only signed bytes, the second is
for languages where the bytes can be declared as unsigned. */

enum Category {
    Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
    Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf;

    final BitSet ascii;
    private static byte[] table1, table2;

    Category() {
        ascii = new BitSet();
        int type = ordinal();
        for (int ch = 0; ch < 128; ch++) {
            if (Character.getType(ch) == type) ascii.set(ch);
        }
    }

    // Read a byte array from an input stream.
    private static byte[] readBytes(InputStream in) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            int len = in.read(buffer);
            while (len >= 0) {
                bs.write(buffer, 0, len);
                len = in.read(buffer);
            }
            in.close();
        }
        catch (IOException e) { throw new Error(e); }
        return bs.toByteArray();
    }

    // Get the category of a unicode character.
    static Category get(int ch) {
        Category[] cats = values();
        return cats[table2[(table1[ch>>8]&255)*256+(ch&255)]];
    }

    private static void readFiles() {
        table1 = readBytes(Category.class.getResourceAsStream("table1.bin"));
        table2 = readBytes(Category.class.getResourceAsStream("table2.bin"));
    }

    // Read the files.
    static {
        try { readFiles(); }
        catch (Exception err) { }
    }

    // Type 17 is unallocated in Java. Use it for Uc.
    private static int type(Category cat) {
        switch (cat) {
        case Uc: return 17;
        case Cc: return CONTROL;
        case Cf: return FORMAT;
        case Cn: return UNASSIGNED;
        case Co: return PRIVATE_USE;
        case Cs: return SURROGATE;
        case Ll: return LOWERCASE_LETTER;
        case Lm: return MODIFIER_LETTER;
        case Lo: return OTHER_LETTER;
        case Lt: return TITLECASE_LETTER;
        case Lu: return UPPERCASE_LETTER;
        case Mc: return COMBINING_SPACING_MARK;
        case Me: return ENCLOSING_MARK;
        case Mn: return NON_SPACING_MARK;
        case Nd: return DECIMAL_DIGIT_NUMBER;
        case Nl: return LETTER_NUMBER;
        case No: return OTHER_NUMBER;
        case Pc: return CONNECTOR_PUNCTUATION;
        case Pd: return DASH_PUNCTUATION;
        case Pe: return END_PUNCTUATION;
        case Pf: return FINAL_QUOTE_PUNCTUATION;
        case Pi: return INITIAL_QUOTE_PUNCTUATION;
        case Po: return OTHER_PUNCTUATION;
        case Ps: return START_PUNCTUATION;
        case Sc: return CURRENCY_SYMBOL;
        case Sk: return MODIFIER_SYMBOL;
        case Sm: return MATH_SYMBOL;
        case So: return OTHER_SYMBOL;
        case Zl: return LINE_SEPARATOR;
        case Zp: return PARAGRAPH_SEPARATOR;
        case Zs: return SPACE_SEPARATOR;
        }
        throw new Error("Unknown category");
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) test();
            else if (args.length == 1 && args[0].equals("-g")) generate();
            else throw new Error("use: java pecan.Category [-g]");
        }
        catch (Exception e) { throw new Error(e); }
    }

    // Check that the ordinals correspond to Character.getType. Check the
    // generated tables against the Java library, for some early characters
    // where the Unicode version isn't a problem.
    private static void test() {
        for (Category cat : Category.values()) {
            if (cat.ordinal() != type(cat)) throw new Error("Bad sequence");
        }
        if (table1 == null) {
            throw new Error(
                "Files not found: use java pecan.Category -g to generate.");
        }
        for (int ch = 0; ch < 512; ch++) {
            Category cat1 = get(ch);
            Category cat2 = values()[Character.getType(ch)];
            if (cat1 != cat2) throw new Error("Bad tables " + ch);
        }
        System.out.println("Category class OK");
    }

    private static final int UNICODES = 1114112;
    private static final byte unassigned = (byte) Cn.ordinal();
    private static byte[] genTable;
    private static byte cat;
    private static int next;
    private static byte[] genTable1;
    private static byte[][] genTable2;
    private static int nblocks;

    public static void generate() throws Exception {
        genTable = new byte[UNICODES];
        genTable1 = new byte[UNICODES / 256];
        genTable2 = new byte[256][256];
        cat = unassigned;
        next = nblocks = 0;
        InputStream in = Category.class.getResourceAsStream("UnicodeData.txt");
        Scanner sc = new Scanner(in);
        while (sc.hasNextLine()) addLine(sc.nextLine());
        sc.close();
        while (next < UNICODES) genTable[next++] = unassigned;
        build();
        write();
    }

    // Deal with one line from file.
    private static void addLine(String line) {
        String[] data = line.split(";");
        int ch = Integer.parseInt(data[0], 16);
        while (ch > next) genTable[next++] = (byte) cat;
        cat = (byte) valueOf(data[2]).ordinal();
        genTable[next++] = (byte) cat;
        if (! data[1].endsWith(", First>")) cat = unassigned;
    }

    // Build the tables.
    private static void build() {
        byte[] block = new byte[256];
        for (int i = 0; i < UNICODES/256; i++) {
            for (int b=0; b<256; b++) block[b] = genTable[256*i + b];
            boolean done = false;
            for (int j=0; j<nblocks; j++) {
                if (! Arrays.equals(block, genTable2[j])) continue;
                genTable1[i] = (byte) j;
                done = true;
                break;
            }
            if (done) continue;
            genTable1[i] = (byte) nblocks;
            genTable2[nblocks++] = block;
            block = new byte[256];
        }
    }

    // Write out the files. Whole table for checking
    private static void write() throws Exception {
        File file = new File("pecan/table1.bin");
        OutputStream out = new FileOutputStream(file);
        out.write(genTable1);
        out.close();
        file = new File("pecan/table2.bin");
        out = new FileOutputStream(file);
        for (int i=0; i<nblocks; i++) out.write(genTable2[i]);
        out.close();
    }
}
