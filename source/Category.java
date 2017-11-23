// Part of Pecan 4. Open source - see licence.txt.

package pecan;

import java.io.*;
import java.util.*;
import java.util.Scanner;
import static java.lang.Character.*;

/* These are the standard Unicode general categories. The name of each category
is its standard two letter abbreviation, and the ordinal of each category is
its Java type code (see Character.getType).  One further constant is added,
namely Uc representing all Unicode characters.  Each category has a bitset
giving its ascii content. */

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

    private static int type(Category cat) {
        switch (cat) {
        // Type 17 is un
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

/* The file pecan/UnicodeData.txt file is the data file for version 7.0.0
of Unicode, copied from http://www.unicode.org/Public/7.0.0/ucd/UnicodeData.txt

The main() method generates two byte-array tables as binary files cats1.bin and
cats2.bin.  They form a normal two-stage table for looking up the general
category of a character.  The second table consists of all the distinct
256-byte blocks from the notional full table.  This code can be used:

    category = table2[table1[ch>>8]*256+(ch&255)];

The number of blocks in the second table is 123 for Unicode 7.0.0.  This is less
than 128, so it doesn't matter if the bytes in the tables are signed (as in
Java). */

    public static void main(String[] args) throws Exception {
        table = new byte[UNICODES];
        table1 = new byte[UNICODES / 256];
        table2 = new byte[128][256];
        cat = unassigned;
        next = nblocks = 0;
        File file = new File("pecan/UnicodeData.txt");
        Scanner sc = new Scanner(file);
        while (sc.hasNextLine()) addLine(sc.nextLine());
        sc.close();
        while (next < UNICODES) table[next++] = unassigned;
        build();
        if (nblocks > 128) System.out.println("WARNING: #blocks > 128");
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

    // Write out the files.
    private static void write() throws Exception {
        // Whole table for checking
        File file0 = new File("pecan/cats0.txt");
        PrintWriter out0 = new PrintWriter(file0);
        for (int i=0; i<1114112/256/16; i++) {
            for (int j=0; j<16; j++) {
                out0.print(table1[i*16+j]);
                out0.print(",");
            }
            out0.println();
        }
        out0.close();
        File file = new File("pecan/cats1.bin");
        OutputStream out = new FileOutputStream(file);
        out.write(table1);
        out.close();
        file = new File("pecan/cats2.bin");
        out = new FileOutputStream(file);
        for (int i=0; i<nblocks; i++) out.write(table2[i]);
        out.close();
    }
}
