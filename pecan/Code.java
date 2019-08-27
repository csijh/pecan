// Pecan 1.0 opcodes. Free and open source. See licence.txt.

package pecan;

/* Code constants represent bytecode ops. See Generator for translations and
Interpreter for semantics. */

public enum Code {
    START, STOP, GO, BACK,
    EITHER, OR, BOTH, AND, MAYBE, ONE, MANY, DO, LOOK, TRY, HAS, NOT,
    DROP, ACT, MARK, TAG,
    CAT, STRING, LESS, LOW, HIGH, SET;

    public static void main(String[] args) { }
}
