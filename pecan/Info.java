// Pecan 5 annotations. Free and open source. See licence.txt.

package pecan;
import java.util.*;

/* An Info object represents annotation information which is gathered about an
expression and stored in its node during the various passes.

The annotation information consists of various flags, counts, bitsets, and a
temporary note which is used to produce custom test output per pass. */

class Info {
    private int value;
    private int flags;
    private int NET, LOW, PC;
    private BitSet FIRST, START, FOLLOW;
    private String note;

    // Flag constants. For the meanings of the flags, see the Binder and
    // Analyser classes.
    public static enum Flag {
        TextInput, TokenInput, SN, FN, SP, FP, WF, AA, AB, BP;
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
