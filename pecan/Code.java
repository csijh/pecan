// Pecan 1.0 interpreter opcodes. Free and open source. See licence.txt.

package pecan;

/* Code constants represent bytecode ops, in alphabetical order. See Generator
for translations. */

public enum Code {
    ACT, AND, ARITY, BACK, BOTH, CAT, DO, DROP, EITHER, EOT, GO, HAS, HIGH,
    LOOK, LOW, MANY, MARK, MAYBE, NOT, ONE, OR, SEE, SET, SPLIT, START, STOP,
    STRING, TAG;

    // Check alphabetical order.
    public static void main(String[] args) {
        Code[] values = Code.values();
        assert(values.length <= 32);
        for (int i = 0; i < values.length - 1; i++) {
            assert(values[i].toString().compareTo(values[i+1].toString()) < 0);
        }
        System.out.println("Code class OK");
    }
}
