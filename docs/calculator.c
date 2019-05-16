// Read input string from user, parse, print. Loop.

typedef unsigned char byte;

enum op { START, STOP, GO, EITHER, OR };

static int arg0, in, out, saveIn, saveOut, ok;
static byte *pc;
static byte code[];

static inline void arg() { return arg0 + *pc++; }
static inline void ret() { ps = pop(); }

static inline void start() {
    push(pc + arg());
}

static inline void go() {
    pc = code + arg();
}

static inline void either() {
    saveIn = in;
    saveOut = out;
    push(pc + arg());
}

static inline void or() {
    if (ok || in > saveIn) ret();
    else out = saveOut;
}

static inline void both() {
    push(pc + arg());
}

static inline void and() {
    if (! ok) ret();
}

static inline void maybe() {
    saveIn = in;
    saveOut = out;
    push(pc);
    pc++;
}

static inline void opt() {
    if (!ok && in == saveIn) {
        out = saveOut;
        ok = true;
    }
    ret();
}

static inline void many() {
    if (ok) {
        saveIn = in;
        saveOut = out;
    }
    else {
        if (!ok && in == saveIn) {
            out = saveOut;
            ok = true;
        }
        ret();
    }
}
/*

int saveIn, saveOut, saveMark, length;
String text;
if (tracing && ! skipTrace) System.out.println(node.trace());
skipTrace = false;
switch(node.op()) {
case SOME:
    saveIn = in;
    saveOut = out;
    parse(node.left());
    if (!ok && in == saveIn) out = saveOut;
    if (!ok) return;
    saveIn = in;
    saveOut = out;
    while (ok) {
        saveIn = in;
        saveOut = out;
        parse(node.left());
    }
    if (!ok && in == saveIn) {
        out = saveOut;
        ok = true;
    }
    break;
case TRY:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    in = saveIn;
    if (ok && lookahead == 0) takeActions();
    if (ok) parse(node.left());
    else out = saveOut;
    break;
case HAS:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    out = saveOut;
    in = saveIn;
    break;
case NOT:
    saveIn = in;
    saveOut = out;
    lookahead++;
    parse(node.left());
    lookahead--;
    out = saveOut;
    in = saveIn;
    ok = !ok;
    break;
case CHAR:
    if (in >= input.length()) ok = false;
    else {
        int ch = input.codePointAt(in);
        ok = (ch == node.value());
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case STRING:
    length = node.text().length() - 2;
    text = node.text().substring(1, length+1);
    ok = true;
    if (in + length > input.length()) ok = false;
    else for (int i=0; i<length; i++) {
        if (input.charAt(in+i) != text.charAt(i)) { ok = false; break; }
    }
    if (ok) {
        if (lookahead == 0 && out > 0) takeActions();
        in += length;
        if (tracing) traceInput();
    }
    break;
case SET:
    length = node.text().length() - 2;
    text = node.text().substring(1, length+1);
    ok = false;
    if (in >= input.length()) { }
    else for (int i=0; i<length; i++) {
        if (input.charAt(in) != text.charAt(i)) continue;
        if (lookahead == 0 && out > 0) takeActions();
        if (Character.isHighSurrogate(text.charAt(i))) {
            i++;
            if (input.charAt(in+1) != text.charAt(i)) continue;
            in++;
        }
        in++;
        if (tracing) traceInput();
        ok = true;
    }
    break;
case RANGE:
    int low = node.left().value();
    int high = node.right().value();
    ok = false;
    if (in < input.length()) {
        int ch = input.codePointAt(in);
        ok = (ch >= low) && (ch <= high);
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case CAT:
    ok = false;
    int cats = node.value();
    if (in < input.length()) {
        int ch = input.codePointAt(in);
        int bit = 1 << Character.getType(ch);
        ok = ((cats & bit) != 0);
        if (ok) {
            if (lookahead == 0 && out > 0) takeActions();
            int n = Character.charCount(ch);
            in += n;
            if (tracing) traceInput();
        }
    }
    break;
case TAG:
    String query;
    if (node.text().charAt(0) == '%') query = node.text().substring(1);
    else query = node.text().substring(1, node.text().length()-1);
    if (query.length() == 0) ok = in == input.length();
    else ok = input.startsWith(query, in);
    if (ok) {
        start = in;
        if (lookahead == 0 && out > 0) takeActions();
        in += query.length();
        while (in < input.length() &&
            (input.charAt(in) == ' ' || input.charAt(in) == '\n')) in++;
        if (tracing) traceInput();
    }
    break;
case MARK:
    ok = true;
    if (lookahead > 0) break;
    if (mark != in) { mark = in; failures.clear(); }
    failures.set(node.value());
    break;
case DROP:
    ok = true;
    delay[out++] = node;
    break;
case ACT:
    ok = true;
    if (lookahead > 0) break;
    delay[out++] = node;
    break;
*/
