// Pecan 5 Unicode categories. Free and open source. See licence.txt.

package pecan;

import java.io.*;
import java.util.*;
import java.util.Scanner;
import static java.lang.Character.*;

/* These are the standard Unicode general categories. The name of each category
is its standard two letter abbreviation, and the ordinal of each category is its
Java type code (see Character.getType). One further constant is added, namely Uc
representing all Unicode characters. Each category has a bitset giving its ascii
content.

These Pecan classes get the category of a character using the Java library
method Character.getType. However, to support scanners written in other
languages, the main method of this class can be used to generate two byte-array
tables as binary files table1.bin and table2.bin. They form a two-stage table
for looking up the general category of a character.

The first table contains unsigned bytes which are indexes of blocks in the
second table. The second table consists of all the distinct 256-byte blocks from
the notional full table. A category can be found from these tables from an
integer code point ch using:

    category = table2[(table1[ch>>8]&255)*256+(ch&255)];

The masking of the entry from table1 is needed in Java because bytes are signed,
but is not needed in other languages where the bytes can be declared as
unsigned.

The file pecan/UnicodeData.txt file is the data file for version 12.0.0 of
Unicode, copied from http://www.unicode.org/Public/12.0.0/ucd/UnicodeData.txt
and the use of multi-stage tables is described in Chapter 5 of the Unicode
standard. */

enum Category {
    Cn, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp, Cc,
    Cf, Uc, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So, Pi, Pf;

    final BitSet ascii;

    Category() {
        ascii = new BitSet();
        int type = ordinal();
        for (int ch=0; ch<128; ch++) {
            if (Character.getType(ch) == type) ascii.set(ch);
        }
    }

    // Check that the ordinals correspond to Character.getType.
    static {
        for (Category cat : Category.values()) {
            if (cat.ordinal() != type(cat)) throw new Error("Bad");
        }
    }

    // Type 17 is unallocated. Use it for Uc.
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

    private static final int UNICODES = 1114112;
    private static final byte unassigned = (byte) Cn.ordinal();
    private static byte[] table;
    private static byte cat;
    private static int next;
    private static byte[] table1;
    private static byte[][] table2;
    private static int nblocks;

    public static void main(String[] args) throws Exception {
        table = new byte[UNICODES];
        table1 = new byte[UNICODES / 256];
        table2 = new byte[256][256];
        cat = unassigned;
        next = nblocks = 0;
        File file = new File("pecan/UnicodeData.txt");
        Scanner sc = new Scanner(file);
        while (sc.hasNextLine()) addLine(sc.nextLine());
        sc.close();
        while (next < UNICODES) table[next++] = unassigned;
        build();
        check();
        write();
    }

    // Deal with one line from file.
    private static void addLine(String line) {
        String[] data = line.split(";");
        int ch = Integer.parseInt(data[0], 16);
        while (ch > next) table[next++] = (byte) cat;
        cat = (byte) valueOf(data[2]).ordinal();
        table[next++] = (byte) cat;
        if (! data[1].endsWith(", First>")) cat = unassigned;
    }

    // Build the tables.
    private static void build() {
        byte[] block = new byte[256];
        for (int i = 0; i < UNICODES/256; i++) {
            for (int b=0; b<256; b++) block[b] = table[256*i + b];
            boolean done = false;
            for (int j=0; j<nblocks; j++) {
                if (! Arrays.equals(block, table2[j])) continue;
                table1[i] = (byte) j;
                done = true;
                break;
            }
            if (done) continue;
            table1[i] = (byte) nblocks;
            table2[nblocks++] = block;
            block = new byte[256];
        }
    }

    // Check the generated tables against the Java library, for some early
    // characters where the Unicode version isn't a problem.
    private static void check() {
        for (int ch = 0; ch < 512; ch++) {
            int cat1 = table2[table1[ch>>8]&255][ch&255];
            int cat2 = Character.getType(ch);
            if (cat1 != cat2) throw new Error("Bad tables " + ch);
        }
    }

    // Write out the files. Whole table for checking
    private static void write() throws Exception {
        File file = new File("pecan/table1.bin");
        OutputStream out = new FileOutputStream(file);
        out.write(table1);
        out.close();
        file = new File("pecan/table2.bin");
        out = new FileOutputStream(file);
        for (int i=0; i<nblocks; i++) out.write(table2[i]);
        out.close();
    }
}
