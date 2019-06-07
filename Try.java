import java.text.*;

class Try {
    private int in, out;

    private interface pe {
        boolean f() throws ParseException;
    }

    private boolean yes() throws ParseException { return true; }

    private boolean no() throws ParseException { return false; }

    private boolean and(pe x, pe y) throws ParseException {
        if (! x.f()) return false;
        return y.f();
    }

    private boolean or(pe x, pe y) throws ParseException {
        int saveIn = in, saveOut = out;
        if (x.f()) return true;
        if (in > saveIn) return false;
        out = saveOut;
        return y.f();
    }

    void run() {
        try {
            System.out.println(and(this::yes, this::yes));
            System.out.println(and(this::yes, this::no));
            System.out.println(and(this::no, this::yes));
            System.out.println(and(this::no, this::no));
        } catch (ParseException e) { }
    }

    public static void main(String[] args) {
        Try program = new Try();
        program.run();
    }
}
