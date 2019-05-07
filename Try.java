import static java.lang.Character.*;
class Try {
public static void main(String[] args) {
    for (int i = 1114111; i>=0; i--) {
        int cat = Character.getType(i);
        if (cat == CONTROL) continue;
        if (cat == UNASSIGNED) continue;
        if (cat == PRIVATE_USE) continue;
        if (cat == SURROGATE) continue;
        if (cat == LINE_SEPARATOR) continue;
        if (cat == PARAGRAPH_SEPARATOR) continue;
        System.out.println("i=" + i + " cat=" + cat);
        System.exit(1);
    }
}
}
// visible = (Cc/Cn/Co/Cs/Zl/Zp)! Uc
/*
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
*/
