// Part of Pecan 4. Open source - see licence.txt.

package pecan;
import java.util.*;
import static pecan.Op.*;

/* An Info object represents information which is gathered about an expression
and stored in its node during the various passes. */

class Info {
    // Flags, counts, bitsets, and a temporary note which is used to produce
    // custom test output per pass.

    private int value;
    private int flags;
    private int NET, LOW, PC;
    private BitSet FIRST, START, FOLLOW;
    private String note;

    // Flag constants.
    public static enum Flag {
        IC, IT, SN, FN, SP, FP, WF, AA, AB, BP;
        int bit() { return 1 << ordinal(); }
    }

    // Construct an Info object
    Info() {
        FIRST = new BitSet();
        START = new BitSet();
        FOLLOW = new BitSet();
        note = "";
    }

    // Get/set the value.
    int value() { return value; }
    void value(int v) { value = v; }

    // Get, set or unset a flag.
    boolean has(Flag f) { return (flags & f.bit()) != 0; }
    void set(Flag f) { flags |= f.bit(); }
    void unset(Flag f) { flags &= ~f.bit(); }

    // Get/set counts and get bitsets.
    int NET() { return NET; }
    int LOW() { return LOW; }
    void NET(int n) { NET = n; }
    void LOW(int l) { LOW = l; }
    BitSet FIRST() { return FIRST; }
    BitSet START() { return START; }
    BitSet FOLLOW() { return FOLLOW; }

    // Get/set the note.
    String note() { return note; }
    void note(String n) { note = n; }

    // Get/set PC = address in bytecode.
    int PC() { return PC; }
    void PC(int i) { PC = i; }

    public static void main(String[] args) { }
}
