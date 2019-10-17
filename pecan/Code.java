// Pecan 1.0 opcodes. Free and open source. See licence.txt.

package pecan;

/* Code constants represent bytecode ops. See Generator for translations and
Interpreter for semantics. */

public enum Code {
    START, STOP, GO, BACK,
    EITHER, OR, BOTH, AND, MAYBE, ONE, MANY, DO, LOOK, SEE, HAS, NOT,
    DROP, ACT, MARK, TAG,
    CAT, STRING, LESS, LOW, HIGH, SET, END;

    // Codes such as STRING followed by characters have a compact version such
    // as STRING1 to avoid an operand when the number of bytes is one.
    boolean hasText() {
        switch (this) {
            case STRING: case LESS: case LOW: case HIGH: case SET: return true;
            default: return false;
        }
    }

    // Codes such as GO with an arbitrary offset have an extended version such
    // as GOL with a two-byte operand, when it is greater than 255.
    boolean hasOffset() {
        return this == GO || this == BACK;
    }

    public static void main(String[] args) { }
}
