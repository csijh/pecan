/* UTF-8 and Unicode.  Open source - see licence.txt.

Functions unilength and unichar read a Unicode character (code point) from a
UTF-8 source text. Functions asciiType and unicodeType are provided which find
the utype (Unicode general category) of a character, except that '\0' is given
category Nu (non-Unicode) so that it can be used as a sentinel terminator.

The function which was used to generate the tables from UnicodeData.txt is
included in unicode.c, so that the tables can be updated if a new version of
Unicode is used. */

// Find the length of a unicode character (code point) in UTF-8 text.
int unilength(char *t);

// Read a unicode character from a UTF-8 source.
int unichar(char *t);

// The Unicode types (general categories) which partition the Unicode characters
// plus Nu (non-Unicode) and Uc (any Unicode character).
enum utype {
    Cc=0, Cf=1, Cn=2, Co=3, Cs=4, Ll=5, Lm=6, Lo=7, Lt=8, Lu=9, Mc=10, Me=11,
    Mn=12, Nd=13, Nl=14, No=15, Pc=16, Pd=17, Pe=18, Pf=19, Pi=20, Po=21, Ps=22,
    Sc=23, Sk=24, Sm=25, So=26, Zl=27, Zp=28, Zs=29,
    Nu=30, Uc=31
};
typedef enum utype utype;

// Look up the Unicode type of an ascii character (0..127).
inline static utype asciitype(int ch) {
    extern unsigned char utable[31488];
    return utable[ch];
}

// Look up the type of a Unicode 7.0.0 character (code point).
inline static utype unitype(int ch) {
    extern unsigned char uindex[4352];
    extern unsigned char utable[31488];
    return utable[(uindex[ch >> 8] << 8) + (ch & 0x7F)];
}
