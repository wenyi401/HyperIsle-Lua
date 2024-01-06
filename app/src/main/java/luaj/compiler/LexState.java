/*******************************************************************************
 * Copyright (c) 2009 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ******************************************************************************/
package luaj.compiler;

import android.graphics.Rect;
import android.util.Pair;

import luaj.Globals;
import luaj.LocVars;
import luaj.Lua;
import luaj.LuaInteger;
import luaj.LuaString;
import luaj.LuaSyntaxError;
import luaj.LuaValue;
import luaj.Prototype;
import luaj.compiler.FuncState.BlockCnt;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;


public class LexState extends Constants {

    protected static final String RESERVED_LOCAL_VAR_FOR_CONTROL = "(for control)";
    protected static final String RESERVED_LOCAL_VAR_FOR_STATE = "(for state)";
    protected static final String RESERVED_LOCAL_VAR_FOR_GENERATOR = "(for generator)";
    protected static final String RESERVED_LOCAL_VAR_FOR_STEP = "(for step)";
    protected static final String RESERVED_LOCAL_VAR_FOR_LIMIT = "(for limit)";
    protected static final String RESERVED_LOCAL_VAR_FOR_INDEX = "(for index)";

    // keywords array
    protected static final String[] RESERVED_LOCAL_VAR_KEYWORDS = new String[]{
            RESERVED_LOCAL_VAR_FOR_CONTROL,
            RESERVED_LOCAL_VAR_FOR_GENERATOR,
            RESERVED_LOCAL_VAR_FOR_INDEX,
            RESERVED_LOCAL_VAR_FOR_LIMIT,
            RESERVED_LOCAL_VAR_FOR_STATE,
            RESERVED_LOCAL_VAR_FOR_STEP
    };
    private static final Hashtable RESERVED_LOCAL_VAR_KEYWORDS_TABLE = new Hashtable();

    static {
        for (int i = 0; i < RESERVED_LOCAL_VAR_KEYWORDS.length; i++)
            RESERVED_LOCAL_VAR_KEYWORDS_TABLE.put(RESERVED_LOCAL_VAR_KEYWORDS[i], Boolean.TRUE);
    }

    private static final int EOZ = (-1);
    private static final int MAX_INT = Integer.MAX_VALUE - 2;
    private static final int UCHAR_MAX = 255; // TODO, convert to unicode CHAR_MAX?
    private static final int LUAI_MAXCCALLS = 200;

    private static final String LUA_QS(String s) {
        return "'" + s + "'";
    }

    private static final String LUA_QL(Object o) {
        return LUA_QS(String.valueOf(o));
    }

    private static final int LUA_COMPAT_LSTR = 0; // 1 for compatibility, 2 for old behavior
    private static final boolean LUA_COMPAT_VARARG = true;

    public static boolean isReservedKeyword(String varName) {
        return RESERVED_LOCAL_VAR_KEYWORDS_TABLE.containsKey(varName);
    }

    /*
     ** Marks the end of a patch list. It is an invalid value both as an absolute
     ** address, and as a list link (would link an element to itself).
     */
    static final int NO_JUMP = (-1);

    /*
     ** grep "ORDER OPR" if you change these enums
     */
    static final int
            OPR_ADD = 0, OPR_SUB = 1, OPR_MUL = 2, OPR_MOD = 3, OPR_POW = 4,
            OPR_DIV = 5,
            OPR_IDIV = 6,
            OPR_BAND = 7, OPR_BOR = 8, OPR_BXOR = 9,
            OPR_SHL = 10, OPR_SHR = 11,
            OPR_CONCAT = 12,
            OPR_EQ = 13, OPR_LT = 14, OPR_LE = 15,
            OPR_NE = 16, OPR_GT = 17, OPR_GE = 18,
            OPR_AND = 19, OPR_OR = 20,
            OPR_NOBINOPR = 21;

    static final int
            OPR_MINUS = 0, OPR_NOT = 1, OPR_LEN = 2, OPR_NOUNOPR = 3, OPR_BNOT = 4;

    /* exp kind */
    public static final int
            VVOID = 0,    /* no value */
            VNIL = 1,
            VTRUE = 2,
            VFALSE = 3,
            VK = 4,        /* info = index of constant in `k' */
            VKNUM = 5,    /* nval = numerical value */
            VNONRELOC = 6,    /* info = result register */
            VLOCAL = 7,    /* info = local register */
            VGLOBAL = 8,    /* info = index of table, aux = index of global name in `k' */
            VENV = 9,    /* info = index of table, aux = index of global name in `k' */
            VUPVAL = 10,       /* info = index of upvalue in `upvalues' */
            VINDEXED = 11,    /* info = table register, aux = index register (or `k') */
            VJMP = 12,        /* info = instruction pc */
            VRELOCABLE = 13,    /* info = instruction pc */
            VCALL = 14,    /* info = instruction pc */
            VVARARG = 15;    /* info = instruction pc */

    /* semantics information */
    private static class SemInfo {
        LuaValue r;
        LuaString ts;
    }

    ;

    private static class Token {
        int token;
        final SemInfo seminfo = new SemInfo();

        public void set(Token other) {
            this.token = other.token;
            this.seminfo.r = other.seminfo.r;
            this.seminfo.ts = other.seminfo.ts;
        }
    }

    ;

    int current;  /* current character (charint) */
    int linenumber;  /* input line counter */
    int lastline;  /* line of last token `consumed' */
    final Token t = new Token();  /* current token */
    final Token lookahead = new Token();  /* look ahead token */
    FuncState fs;  /* `FuncState' is private to the parser */
    LuaC.CompileState L;
    InputStream z;  /* input stream */
    char[] buff;  /* buffer for tokens */
    int nbuff; /* length of buffer */
    Dyndata dyd = new Dyndata();  /* dynamic structures used by the parser */
    LuaString source;  /* current source name */
    LuaString envn;  /* environment variable name */
    byte decpoint;  /* locale decimal point */
    public Globals globals;

    /* ORDER RESERVED */
    final static String luaX_tokens[] = {
            "and", "break", "case", "catch", "continue", "default", "defer", "do", "else", "elseif",
            "end", "false", "finally", "for", "function", "goto", "if", "import",
            "in", "lambda", "local", "module", "nil", "not", "or", "repeat",
            "return", "switch", "then", "true", "try", "until", "when", "while",
            "..", "...", "==", ">=", "<=", "~=", "//", "<<", ">>",
            "::", "<eos>", "<number>", "<name>", "<string>", "<eof>",
    };

    /* terminal symbols denoted by reserved words */
    final static int TK_AND = 257;
    final static int TK_BREAK = TK_AND + 1;
    final static int TK_CASE = TK_BREAK + 1;
    final static int TK_CATCH = TK_CASE + 1;
    final static int TK_CONTINUE = TK_CATCH + 1;
    final static int TK_DEFAULT = TK_CONTINUE + 1;
    final static int TK_DEFER = TK_DEFAULT + 1;
    final static int TK_DO = TK_DEFER + 1;
    final static int TK_ELSE = TK_DO + 1;
    final static int TK_ELSEIF = TK_ELSE + 1;
    final static int TK_END = TK_ELSEIF + 1;
    final static int TK_FALSE = TK_END + 1;
    final static int TK_FINALLY = TK_FALSE + 1;
    final static int TK_FOR = TK_FINALLY + 1;
    final static int TK_FUNCTION = TK_FOR + 1;
    final static int TK_GOTO = TK_FUNCTION + 1;
    final static int TK_IF = TK_GOTO + 1;
    final static int TK_IMPORT = TK_IF + 1;
    final static int TK_IN = TK_IMPORT + 1;
    final static int TK_LAMBDA = TK_IN + 1;
    final static int TK_LOCAL = TK_LAMBDA + 1;
    final static int TK_MODULE = TK_LOCAL + 1;
    final static int TK_NIL = TK_MODULE + 1;
    final static int TK_NOT = TK_NIL + 1;
    final static int TK_OR = TK_NOT + 1;
    final static int TK_REPEAT = TK_OR + 1;
    final static int TK_RETURN = TK_REPEAT + 1;
    final static int TK_SWITCH = TK_RETURN + 1;
    final static int TK_THEN = TK_SWITCH + 1;
    final static int TK_TRUE = TK_THEN + 1;
    final static int TK_TRY = TK_TRUE + 1;
    final static int TK_UNTIL = TK_TRY + 1;
    final static int TK_WHEN = TK_UNTIL + 1;
    final static int TK_WHILE = TK_WHEN + 1;
    /* other terminal symbols */
    final static int TK_CONCAT = TK_WHILE + 1;
    final static int TK_DOTS = TK_CONCAT + 1;
    final static int TK_EQ = TK_DOTS + 1;
    final static int TK_GE = TK_EQ + 1;
    final static int TK_LE = TK_GE + 1;
    final static int TK_NE = TK_LE + 1;
    final static int TK_IDIV = TK_NE + 1;
    final static int TK_SHL = TK_IDIV + 1;
    final static int TK_SHR = TK_SHL + 1;
    final static int TK_DBCOLON = TK_SHR + 1;
    final static int TK_EOS = TK_DBCOLON + 1;
    final static int TK_NUMBER = TK_EOS + 1;
    final static int TK_NAME = TK_NUMBER + 1;
    final static int TK_STRING = TK_NAME + 1;

    final static int FIRST_RESERVED = TK_AND;
    final static int NUM_RESERVED = TK_WHILE + 1 - FIRST_RESERVED;

    final static Hashtable<LuaString, Integer> RESERVED = new Hashtable<>();

    static {
        for (int i = 0; i < NUM_RESERVED; i++) {
            LuaString ts = (LuaString) LuaValue.valueOf(luaX_tokens[i]);
            RESERVED.put(ts, new Integer(FIRST_RESERVED + i));
        }
		/*for ( int i=0; i<NUM_RESERVED; i++ ) {
			LuaString ts = (LuaString) LuaValue.valueOf( luaX_cn_tokens[i] );
			RESERVED.put(ts, new Integer(FIRST_RESERVED+i));
		}*/
    }

    private final static int[] luai_ctype_ = {
            0x00,  /* EOZ */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* 0. */
            0x00, 0x08, 0x08, 0x08, 0x08, 0x08, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* 1. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x0c, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,    /* 2. */
            0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16,    /* 3. */
            0x16, 0x16, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x04, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x05,    /* 4. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* 5. */
            0x05, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04, 0x05,
            0x04, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x05,    /* 6. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* 7. */
            0x05, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04, 0x00,

            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,


            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* c. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* d. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,


            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,


            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* f. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    };
    private final static int ALPHABIT = 0, DIGITBIT = 1, PRINTBIT = 2, SPACEBIT = 3, XDIGITBIT = 4;

    private boolean testprop(int c, int p) {
        return (luai_ctype_[(c) + 1] & (p)) != 0;
    }

    private static int MASK(int B) {
        return (1 << (B));
    }

    private boolean isalnum(int c) {
        return testprop(c, (MASK(ALPHABIT) | MASK(DIGITBIT)));
		/*return (c >= '0' && c <= '9')
			|| (c >= 'a' && c <= 'z')
			|| (c >= 'A' && c <= 'Z')
			|| (c == '_');*/
        // return Character.isLetterOrDigit(c);
    }

    private boolean isalpha(int c) {
        return testprop(c, MASK(ALPHABIT));
        //return (c >= 'a' && c <= 'z')
        //	|| (c >= 'A' && c <= 'Z');
    }

    private boolean isdigit(int c) {
        return (c >= '0' && c <= '9');
    }

    private boolean isxdigit(int c) {
        return testprop(c, MASK(XDIGITBIT));
		/*return (c >= '0' && c <= '9')
				|| (c >= 'a' && c <= 'f')
				|| (c >= 'A' && c <= 'F');*/
    }

    private boolean isspace(int c) {
        return testprop(c, MASK(SPACEBIT));
    }

    public LexState(LuaC.CompileState state, InputStream stream) {
        this.z = stream;
        this.buff = new char[32];
        this.L = state;
    }

    void nextChar() {
        try {
            current = z.read();
        } catch (IOException e) {
            
                e.printStackTrace();
            current = EOZ;
        }
    }

    boolean currIsNewline() {
        return current == '\n' || current == '\r';
    }

    void save_and_next() {
        save(current);
        nextChar();
    }

    void save(int c) {
        if (buff == null || nbuff + 1 > buff.length)
            buff = realloc(buff, nbuff * 2 + 1);
        buff[nbuff++] = (char) c;
    }


    String token2str(int token) {
        if (token < FIRST_RESERVED) {
            return iscntrl(token) ?
                    L.pushfstring("char(" + ((int) token) + ")") :
                    L.pushfstring(String.valueOf((char) token));
        } else {
            return luaX_tokens[token - FIRST_RESERVED];
        }
    }

    private static boolean iscntrl(int token) {
        return token < ' ';
    }

    String txtToken(int token) {
        switch (token) {
            case TK_NAME:
            case TK_STRING:
            case TK_NUMBER:
                return LuaString.valueOf(buff, 0, nbuff).tojstring();
            default:
                return token2str(token);
        }
    }

    void lexerror(String msg, int token) {
        String cid = Lua.chunkid(source.tojstring());
        if (token != 0)
            msg = L.pushfstring(linenumber + ": syntax error: " + msg + " near " + txtToken(token));
        else
            msg = L.pushfstring(cid + ":" + linenumber + ": " + msg);

        throw new LuaSyntaxError(msg);
    }

    void syntaxerror(String msg) {
        lexerror(msg, t.token);
    }

    // only called by new_localvarliteral() for var names.
    LuaString newstring(String s) {
        return L.newTString(s);
    }

    LuaString newstring(char[] chars, int offset, int len) {
        return L.newTString(LuaString.valueOf(chars, offset, len));
    }

    void inclinenumber() {
        int old = current;
        _assert(currIsNewline());
        nextChar(); /* skip '\n' or '\r' */
        if (currIsNewline() && current != old)
            nextChar(); /* skip '\n\r' or '\r\n' */
        if (++linenumber >= MAX_INT)
            syntaxerror("chunk has too many lines");
    }

    void setinput(LuaC.CompileState L, int firstByte, InputStream z, LuaString source) {
        this.decpoint = '.';
        this.L = L;
        this.lookahead.token = TK_EOS; /* no look-ahead token */
        this.z = z;
        this.fs = null;
        this.linenumber = 1;
        this.lastline = 1;
        this.source = source;
        this.envn = LuaValue.ENV;  /* environment variable name */
        this.nbuff = 0;   /* initialize buffer */
        this.current = firstByte; /* read first char */
        this.skipShebang();
    }

    private void skipShebang() {
        if (current == '#')
            while (!currIsNewline() && current != EOZ)
                nextChar();
    }



    /*
     ** =======================================================
     ** LEXICAL ANALYZER
     ** =======================================================
     */


    boolean check_next(String set) {
        if (set.indexOf(current) < 0)
            return false;
        save_and_next();
        return true;
    }

    void buffreplace(char from, char to) {
        int n = nbuff;
        char[] p = buff;
        while ((--n) >= 0)
            if (p[n] == from)
                p[n] = to;
    }

    LuaValue strx2number(String str, SemInfo seminfo) {
        char[] c = str.toCharArray();
        int s = 0;
        while (s < c.length && isspace(c[s]))
            ++s;
        // Check for negative sign
        int sgn = 1;
        if (s < c.length && c[s] == '-') {
            sgn = -1;
            ++s;
        }
        /* Check for "0x" */
        if (s + 2 >= c.length)
            return LuaValue.ZERO;
        if (c[s++] != '0')
            return LuaValue.ZERO;
        if (c[s] != 'x' && c[s] != 'X')
            return LuaValue.ZERO;
        ++s;

        // read integer part.
        long m = 0;
        int e = 0;
        while (s < c.length && isxdigit(c[s]))
            m = (m * 16) + hexvalue(c[s++]);
        if (s < c.length && c[s] == '.') {
            ++s;  // skip dot
            while (s < c.length && isxdigit(c[s])) {
                m = (m * 16) + hexvalue(c[s++]);
                e -= 4;  // Each fractional part shifts right by 2^4
            }
        }
        if (s < c.length && (c[s] == 'p' || c[s] == 'P')) {
            ++s;
            int exp1 = 0;
            boolean neg1 = false;
            if (s < c.length && c[s] == '-') {
                neg1 = true;
                ++s;
            }
            while (s < c.length && isdigit(c[s]))
                exp1 = exp1 * 10 + c[s++] - '0';
            if (neg1)
                exp1 = -exp1;
            e += exp1;
        }
        return LuaValue.valueOf(sgn * m * (1 << e));
    }

    boolean str2d(String str, SemInfo seminfo) {
        if (str.indexOf('n') >= 0 || str.indexOf('N') >= 0) {
            seminfo.r = LuaValue.ZERO;
        } else if (str.indexOf('x') >= 0 || str.indexOf('X') >= 0) {
            seminfo.r = strx2number(str, seminfo);
        } else if (str.indexOf(".") > 0) {
            seminfo.r = LuaValue.valueOf(Double.parseDouble(str.trim()));
        } else {
            long l = Long.parseLong(str.trim());
            if (l < Long.MAX_VALUE - 1 && l > Long.MIN_VALUE + 1)
                seminfo.r = LuaValue.valueOf(l);
            else
                seminfo.r = LuaValue.valueOf(Double.parseDouble(str.trim()));
        }
        return true;
    }

    void read_numeral(SemInfo seminfo) {
        String expo = "Ee";
        int first = current;
        _assert(isdigit(current));
        save_and_next();
        if (first == '0' && check_next("Xx"))
            expo = "Pp";
        while (true) {
            if (check_next(expo))
                check_next("+-");
            if (isxdigit(current) || current == '.')
                save_and_next();
            else
                break;
        }
        save('\0');
        String str = new String(buff, 0, nbuff);
        str2d(str, seminfo);
    }

    int skip_sep() {
        int count = 0;
        int s = current;
        _assert(s == '[' || s == ']');
        save_and_next();
        while (current == '=') {
            save_and_next();
            count++;
        }
        return (current == s) ? count : (-count) - 1;
    }

    void read_long_string(SemInfo seminfo, int sep) {
        int cont = 0;
        save_and_next(); /* skip 2nd `[' */
        if (currIsNewline()) /* string starts with a newline? */
            inclinenumber(); /* skip it */
        for (boolean endloop = false; !endloop; ) {
            switch (current) {
                case EOZ:
                    lexerror((seminfo != null) ? "unfinished long string"
                            : "unfinished long comment", TK_EOS);
                    break; /* to avoid warnings */
                case '[': {
                    if (skip_sep() == sep) {
                        save_and_next(); /* skip 2nd `[' */
                        cont++;
                        if (LUA_COMPAT_LSTR == 1) {
                            if (sep == 0)
                                lexerror("nesting of [[...]] is deprecated", '[');
                        }
                    }
                    break;
                }
                case ']': {
                    if (skip_sep() == sep) {
                        save_and_next(); /* skip 2nd `]' */
                        if (LUA_COMPAT_LSTR == 2) {
                            cont--;
                            if (sep == 0 && cont >= 0)
                                break;
                        }
                        endloop = true;
                    }
                    break;
                }
                case '\n':
                case '\r': {
                    save('\n');
                    inclinenumber();
                    if (seminfo == null)
                        nbuff = 0; /* avoid wasting space */
                    break;
                }
                default: {
                    if (seminfo != null)
                        save_and_next();
                    else
                        nextChar();
                }
            }
        }
        if (seminfo != null)
            seminfo.ts = newstring(buff, 2 + sep, nbuff - 2 * (2 + sep));
    }

    int skip_comment_sep() {
        int count = 1;
        nextChar();
        while (current == '*') {
            nextChar();
            count++;
        }
        return count;
    }

    void read_long_comment(int sep) {
        for (boolean endloop = false; !endloop; ) {
            switch (current) {
                case EOZ:
                    lexerror("unfinished long comment", TK_EOS);
                    break; /* to avoid warnings */
                case '*': {
                    if (skip_comment_sep() == sep) {
                        if (current == '/') {
                            endloop = true;
                            nextChar();
                        }
                    }
                    break;
                }
                case '\n':
                case '\r': {
                    save('\n');
                    inclinenumber();
                    nbuff = 0; /* avoid wasting space */
                    break;
                }
                default: {
                    nextChar();
                }
            }
        }
    }

    int hexvalue(int c) {
        return c <= '9' ? c - '0' : c <= 'F' ? c + 10 - 'A' : c + 10 - 'a';
    }

    int readhexaesc() {
        nextChar();
        int c1 = current;
        nextChar();
        int c2 = current;
        if (!isxdigit(c1) || !isxdigit(c2))
            lexerror("hexadecimal digit expected 'x" + ((char) c1) + ((char) c2), TK_STRING);
        return (hexvalue(c1) << 4) + hexvalue(c2);
    }

    int readutf8aesc() {
        int i = 0;
        int c = 0;
        nextChar();
        do {
            c = (c << 4) + hexvalue(current);
            nextChar();
        } while (++i < 8 && isxdigit(current));
        save_utf8(c);
        return (char) c;
    }

    private int save_utf8(int ch) {
        if (ch < 0x80) {
            save((char) ch);
            return 1;
        }
        if (ch <= 0x7FF) {
            save((char) (ch >> 6) | 0xC0);
            save((char) (ch | 0x80) & 0xBF);
            return 2;
        }
        if (ch <= 0xFFFF) {
            save((char) (ch >> 12) | 0xE0);
            save((char) ((ch >> 6) | 0x80) & 0xBF);
            save((char) (ch | 0x80) & 0xBF);
            return 3;
        }
        if (ch <= 0x10FFFF) {
            int[] buff = new int[8];
            int mfb = 0x3F; /* maximum that fits in first byte */
            int n = 1;
            do { /* add continuation bytes */
                buff[8 - (n++)] = 0x80 | (ch & 0x3F);
                ch >>= 6; /* remove added bits */
                mfb >>= 1; /* now there is one less bit available in first byte */
            } while (ch > mfb);  /* still needs continuation byte? */
            buff[8 - n] = (~mfb << 1) | ch;
            for (int i = 8 - n; i < 8; i++) {
                save(buff[i]);
            }
            return n;
        }
        return 0;
    }

    void read_string(int del, SemInfo seminfo) {
        save_and_next();
        while (current != del) {
            switch (current) {
                case EOZ:
                    lexerror("unfinished string", TK_EOS);
                    continue; /* to avoid warnings */
                case '\n':
                case '\r':
                    lexerror("unfinished string", TK_STRING);
                    continue; /* to avoid warnings */
                case '\\': {
                    int c;
                    nextChar(); /* do not save the `\' */
                    switch (current) {
                        case 'a': /* bell */
                            c = '\u0007';
                            break;
                        case 'b': /* backspace */
                            c = '\b';
                            break;
                        case 'f': /* form feed */
                            c = '\f';
                            break;
                        case 'n': /* newline */
                            c = '\n';
                            break;
                        case 'r': /* carriage return */
                            c = '\r';
                            break;
                        case 't': /* tab */
                            c = '\t';
                            break;
                        case 'v': /* vertical tab */
                            c = '\u000B';
                            break;
                        case 'u':
                            c = readutf8aesc();
                            //save(c);
                            continue;
                        case 'x':
                            c = readhexaesc();
                            break;
                        case '\n': /* go through */
                        case '\r':
                            save('\n');
                            inclinenumber();
                            continue;
                        case EOZ:
                            continue; /* will raise an error next loop */
                        case 'z': {  /* zap following span of spaces */
                            nextChar();  /* skip the 'z' */
                            while (isspace(current)) {
                                if (currIsNewline()) inclinenumber();
                                else nextChar();
                            }
                            continue;
                        }
                        default: {
                            if (!isdigit(current))
                                save_and_next(); /* handles \\, \", \', and \? */
                            else { /* \xxx */
                                int i = 0;
                                c = 0;
                                do {
                                    c = 10 * c + (current - '0');
                                    nextChar();
                                } while (++i < 3 && isdigit(current));
                                if (c > UCHAR_MAX)
                                    lexerror("escape sequence too large", TK_STRING);
                                save(c);
                            }
                            continue;
                        }
                    }
                    save(c);
                    nextChar();
                    continue;
                }
                default:
                    save_and_next();
            }
        }
        save_and_next(); /* skip delimiter */
        seminfo.ts = newstring(buff, 1, nbuff - 2);
        //seminfo.ts = L.newTString(LuaString.valueOf(buff, 1, nbuff-2));
    }

    int llex(SemInfo seminfo) {
        nbuff = 0;
        while (true) {
            switch (current) {
                case '\n':
                case '\r': {
                    inclinenumber();
                    continue;
                }
                case '-': {
                    nextChar();
                    if (current != '-')
                        return '-';
                    /* else is a comment */
                    nextChar();
                    if (current == '[') {
                        int sep = skip_sep();
                        nbuff = 0; /* `skip_sep' may dirty the buffer */
                        if (sep >= 0) {
                            read_long_string(null, sep); /* long comment */
                            nbuff = 0;
                            continue;
                        }
                    }
                    /* else short comment */
                    while (!currIsNewline() && current != EOZ)
                        nextChar();
                    continue;
                }
                case '[': {
                    int sep = skip_sep();
                    if (sep >= 0) {
                        read_long_string(seminfo, sep);
                        return TK_STRING;
                    } else if (sep == -1)
                        return '[';
                    else
                        lexerror("invalid long string delimiter", TK_STRING);
                }
                case '=': {
                    nextChar();
                    if (current != '=')
                        return '=';
                    else {
                        nextChar();
                        return TK_EQ;
                    }
                }
                case '<': {
                    nextChar();
                    if (current == '<') {
                        nextChar();
                        return TK_SHL;
                    }
                    if (current != '=')
                        return '<';
                    else {
                        nextChar();
                        return TK_LE;
                    }
                }
                case '>': {
                    nextChar();
                    if (current == '>') {
                        nextChar();
                        return TK_SHR;
                    }
                    if (current != '=')
                        return '>';
                    else {
                        nextChar();
                        return TK_GE;
                    }
                }
                case '/': {
                    nextChar();
                    if (current == '*') {
                        /* else is a comment */
                        int sep = skip_comment_sep();
                        nbuff = 0; /* `skip_sep' may dirty the buffer */
                        read_long_comment(sep); /* long comment */
                        nbuff = 0;
                        continue;
                    } else if (current != '/')
                        return '/';
                    else {
                        nextChar();
                        return TK_IDIV;
                    }
                }
                case '~': {
                    nextChar();
                    if (current != '=')
                        return '~';
                    else {
                        nextChar();
                        return TK_NE;
                    }
                }
                case '!': {
                    nextChar();
                    if (current != '=')
                        return TK_NOT;
                    else {
                        nextChar();
                        return TK_NE;
                    }
                }
                case '&': {
                    nextChar();
                    if (current != '&')
                        return '&';
                    else {
                        nextChar();
                        return TK_AND;
                    }
                }
                case '|': {
                    nextChar();
                    if (current != '|')
                        return '|';
                    else {
                        nextChar();
                        return TK_OR;
                    }
                }
                case '$':
                    nextChar();
                    return TK_LOCAL;
                case '@':
                    nextChar();
                    return TK_DBCOLON;
                case ':': {
                    nextChar();
                    if (current != ':')
                        return ':';
                    else {
                        nextChar();
                        return TK_DBCOLON;
                    }
                }
                case '"':
                case '\'': {
                    read_string(current, seminfo);
                    return TK_STRING;
                }
                case '.': {
                    save_and_next();
                    if (check_next(".")) {
                        if (check_next("."))
                            return TK_DOTS; /* ... */
                        else
                            return TK_CONCAT; /* .. */
                    } else if (!isdigit(current))
                        return '.';
                    else {
                        read_numeral(seminfo);
                        return TK_NUMBER;
                    }
                }
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9': {
                    read_numeral(seminfo);
                    return TK_NUMBER;
                }
                case EOZ: {
                    return TK_EOS;
                }
                default: {
                    if (isspace(current)) {
                        _assert(!currIsNewline());
                        nextChar();
                        continue;
                    } else if (isdigit(current)) {
                        read_numeral(seminfo);
                        return TK_NUMBER;
                    } else if (isalpha(current) || current == '_') {
                        /* identifier or reserved word */
                        LuaString ts;
                        do {
                            save_and_next();
                        } while (isalnum(current));
                        ts = newstring(buff, 0, nbuff);
                        if (RESERVED.containsKey(ts)) {
                            seminfo.ts = ts;
                            return ((Integer) RESERVED.get(ts)).intValue();
                        } else {
                            seminfo.ts = ts;
                            return TK_NAME;
                        }
                    } else {
                        int c = current;
                        nextChar();
                        return c; /* single-char tokens (+ - / ...) */
                    }
                }
            }
        }
    }

    void next() {
        lastline = linenumber;
        if (lookahead.token != TK_EOS) { /* is there a look-ahead token? */
            t.set(lookahead); /* use this one */
            lookahead.token = TK_EOS; /* and discharge it */
        } else {
            t.token = llex(t.seminfo); /* read next token */
        }
    }

    void lookahead() {
        _assert(lookahead.token == TK_EOS);
        lookahead.token = llex(lookahead.seminfo);
    }

    // =============================================================
    // from lcode.h
    // =============================================================


    // =============================================================
    // from lparser.c
    // =============================================================

    static final boolean vkisvar(final int k) {
        return (VLOCAL <= (k) && (k) <= VINDEXED);
    }

    static final boolean vkisinreg(final int k) {
        return ((k) == VNONRELOC || (k) == VLOCAL);
    }

    static class expdesc {
        int k; // expkind, from enumerated list, above

        static class U { // originally a union
            short ind_idx; // index (R/K)
            short ind_t; // table(register or upvalue)
            short ind_vt; // whether 't' is register (VLOCAL) or (UPVALUE)
            private LuaValue _nval;
            int info;

            public void setNval(LuaValue r) {
                _nval = r;
            }

            public LuaValue nval() {
                return (_nval == null ? LuaInteger.valueOf(info) : _nval);
            }
        }

        ;
        final U u = new U();
        final IntPtr t = new IntPtr(); /* patch list of `exit when true' */
        final IntPtr f = new IntPtr(); /* patch list of `exit when false' */

        void init(int k, int i) {
            this.f.i = NO_JUMP;
            this.t.i = NO_JUMP;
            this.k = k;
            this.u.info = i;
        }

        boolean hasjumps() {
            return (t.i != f.i);
        }

        boolean isnumeral() {
            return (k == VKNUM && t.i == NO_JUMP && f.i == NO_JUMP);
        }

        public void setvalue(expdesc other) {
            this.f.i = other.f.i;
            this.k = other.k;
            this.t.i = other.t.i;
            this.u._nval = other.u._nval;
            this.u.ind_idx = other.u.ind_idx;
            this.u.ind_t = other.u.ind_t;
            this.u.ind_vt = other.u.ind_vt;
            this.u.info = other.u.info;
        }

        protected expdesc clone() {
            expdesc c = new expdesc();
            c.setvalue(this);
            return c;
        }

        @Override
        public String toString() {
            return "expdesc{k=" + k + "\nU{info=" + u.info +
                    "\nind_idx=" + u.ind_idx +
                    "\nind_vt=" + u.ind_vt +
                    "\nind_t=" + u.ind_t + "}\n}";
        }
    }


    /* description of active local variable */
    static class Vardesc {
        final short idx;  /* variable index in stack */

        Vardesc(int idx) {
            this.idx = (short) idx;
        }
    }

    ;


    /* description of pending goto statements and label statements */
    static class Labeldesc {
        LuaString name;  /* label identifier */
        int pc;  /* position in code */
        int line;  /* line where it appeared */
        short nactvar;  /* local level where it appears in current block */

        public Labeldesc(LuaString name, int pc, int line, short nactvar) {
            this.name = name;
            this.pc = pc;
            this.line = line;
            this.nactvar = nactvar;
        }
    }

    ;


    /* dynamic structures used by the parser */
    static class Dyndata {
        Vardesc[] actvar;  /* list of active local variables */
        int n_actvar = 0;
        Labeldesc[] gt;  /* list of pending gotos */
        int n_gt = 0;
        Labeldesc[] label;   /* list of active labels */
        int n_label = 0;
    }

    ;


    boolean hasmultret(int k) {
        return ((k) == VCALL || (k) == VVARARG);
    }

	/*----------------------------------------------------------------------
	name		args	description
	------------------------------------------------------------------------*/

    void anchor_token() {
        /* last token from outer function must be EOS */
        _assert(fs != null || t.token == TK_EOS);
        if (t.token == TK_NAME || t.token == TK_STRING) {
            LuaString ts = t.seminfo.ts;
            // TODO: is this necessary?
            L.cachedLuaString(t.seminfo.ts);
        }
    }

    /* semantic error */
    void semerror(String msg) {
        t.token = 0;  /* remove 'near to' from final message */
        syntaxerror(msg);
    }

    void error_expected(int token) {
        syntaxerror(L.pushfstring(LUA_QS(token2str(token)) + " expected"));
    }

    boolean testnext(int c) {
        if (t.token == c) {
            next();
            return true;
        } else
            return false;
    }

    boolean testtoken(int c) {
        if (t.token == c) {
            return true;
        } else
            return false;
    }

    void check(int c) {
        if (t.token != c)
            error_expected(c);
    }

    void checknext(int c) {
        check(c);
        next();
    }

    void check_condition(boolean c, String msg) {
        if (!(c))
            syntaxerror(msg);
    }


    void check_match(int what, int who, int where) {
        if (!testnext(what)) {
            if (where == linenumber) {
                error_expected(what);
            } else {
                syntaxerror(L.pushfstring(LUA_QS(token2str(what))
                        + " expected " + "(to close " + LUA_QS(token2str(who))
                        + " at line " + where + ")"));
            }
        }
    }

    LuaString str_checkname() {
        LuaString ts;
        check(TK_NAME);
        ts = t.seminfo.ts;
        next();
        return ts;
    }

    LuaString str_check() {
        LuaString ts;
        check(TK_STRING);
        ts = t.seminfo.ts;
        next();
        return ts;
    }

    void codestring(expdesc e, LuaString s) {
        e.init(VK, fs.stringK(s));
    }

    void checkname(expdesc e) {
        codestring(e, str_checkname2());
    }

    LuaString str_checkname2() {
        LuaString ts;
        if (t.token != TK_NAME && (t.token > TK_WHILE || t.token < FIRST_RESERVED))
            error_expected(TK_NAME);
        //check(TK_NAME);
        ts = t.seminfo.ts;
        next();
        return ts;
    }

    LuaString checkstring(expdesc e) {
        LuaString str = str_check();
        codestring(e, str);
        return str;
    }

    int registerlocalvar(LuaString varname) {
        FuncState fs = this.fs;
        Prototype f = fs.f;
        if (f.locvars == null || fs.nlocvars + 1 > f.locvars.length)
            f.locvars = realloc(f.locvars, fs.nlocvars * 2 + 1);
        LocVars var = new LocVars(varname, 0, 0);
        f.locvars[fs.nlocvars] = var;
        return fs.nlocvars++;
    }

    void new_localvar(LuaString name) {
        int reg = registerlocalvar(name);
        fs.checklimit(dyd.n_actvar + 1, FuncState.LUAI_MAXVARS, "local variables");
        if (dyd.actvar == null || dyd.n_actvar + 1 > dyd.actvar.length)
            dyd.actvar = realloc(dyd.actvar, Math.max(1, dyd.n_actvar * 2));
        dyd.actvar[dyd.n_actvar++] = new Vardesc(reg);
    }

    void new_localvarliteral(String v) {
        LuaString ts = newstring(v);
        new_localvar(ts);
    }

    void adjustlocalvars(int nvars) {
        FuncState fs = this.fs;
        fs.nactvar = (short) (fs.nactvar + nvars);
        for (; nvars > 0; nvars--) {
            LocVars var = fs.getlocvar(fs.nactvar - nvars);
            var.startpc = fs.pc;
        }
    }

    void removevars(int tolevel) {
        FuncState fs = this.fs;
        while (fs.nactvar > tolevel)
            fs.getlocvar(--fs.nactvar).endpc = fs.pc;
    }

    private ArrayList<String> mPackage = new ArrayList<>();
    private HashMap<String, LuaString> mClass = new HashMap<>();

    public void addPackage(String pkgname) {
        mPackage.add(pkgname);
    }

    void singlevar(expdesc var) {
        LuaString varname = this.str_checkname();
        FuncState fs = this.fs;
        int vartype = FuncState.singlevaraux(fs, varname, var, 1);

        if (vartype != VLOCAL && Lua.LUA_FUNC_ENV) {
            if (varname.eq_b(this.envn)) {
                var.init(VENV, NO_REG);
                return;
            }
        }

        if (vartype == VVOID) { /* global name? */
            if (checkClass(var, varname)) {
                return;
            }
            expdesc key = new expdesc();
            vartype = FuncState.singlevaraux(fs, this.envn, var, 1);  /* get environment variable */
            if (Lua.LUA_FUNC_ENV && vartype != VLOCAL) {
                var.init(VGLOBAL, fs.stringK(varname));
                var.u.info = fs.stringK(varname); /* info points to global name */
            } else {
                _assert(var.k == VLOCAL || var.k == VUPVAL);
                this.codestring(key, varname);  /* key is variable name */
                fs.indexed(var, key);  /* env[varname] */
            }
        }
    }

    private boolean checkClass(expdesc var, LuaString varname) {
        String name = varname.tojstring();
        if (mClass.containsKey(name)) {
            LuaString cls = mClass.get(name);
            if (cls == null)
                return false;
            var.init(VRELOCABLE, fs.codeABx(Lua.OP_LOADC, 0, fs.stringK(cls)));
            //var.init(Lua.OP_GETUPVAL, fs.newupvalue(varname, var));
            /*this.new_localvar(varname);
            this.adjust_assign(1, 1, var);
            this.adjustlocalvars(1);*/

            return true;
        }
        for (String s : mPackage) {
            try {
                globals.luajavaLib.bindClassForName(s + name);
                LuaString cls = LuaString.valueOf(s + name);
                var.init(VRELOCABLE, fs.codeABx(Lua.OP_LOADC, 0, fs.stringK(cls)));
                //var.init(Lua.OP_GETUPVAL, fs.newupvalue(varname, var));
                /*this.new_localvar(varname);
                this.adjust_assign(1, 1, var);
                this.adjustlocalvars(1);*/
                mClass.put(name, cls);
                return true;
            } catch (Exception e) {
            }
        }
        mClass.put(name, null);
        return false;
    }

    void adjust_assign(int nvars, int nexps, expdesc e) {
        FuncState fs = this.fs;
        int extra = nvars - nexps;
        if (hasmultret(e.k)) {
            /* includes call itself */
            extra++;
            if (extra < 0)
                extra = 0;
            /* last exp. provides the difference */
            fs.setreturns(e, extra);
            if (extra > 1)
                fs.reserveregs(extra - 1);
        } else {
            /* close last expression */
            if (e.k != VVOID)
                fs.exp2nextreg(e);
            if (extra > 0) {
                int reg = fs.freereg;
                fs.reserveregs(extra);
                fs.nil(reg, extra);
            }
        }
    }

    void enterlevel() {
        if (++L.nCcalls > LUAI_MAXCCALLS)
            lexerror("chunk has too many syntax levels", 0);
    }

    void leavelevel() {
        L.nCcalls--;
    }

    void closegoto(int g, Labeldesc label) {
        FuncState fs = this.fs;
        Labeldesc[] gl = this.dyd.gt;
        Labeldesc gt = gl[g];
        _assert(gt.name.eq_b(label.name));
        if (gt.nactvar < label.nactvar) {
            LuaString vname = fs.getlocvar(gt.nactvar).varname;
            String msg = L.pushfstring("<goto " + gt.name + "> at line "
                    + gt.line + " jumps into the scope of local '"
                    + vname.tojstring() + "'");
            semerror(msg);
        }
        fs.patchlist(gt.pc, label.pc);
        /* remove goto from pending list */
        System.arraycopy(gl, g + 1, gl, g, this.dyd.n_gt - g - 1);
        gl[--this.dyd.n_gt] = null;
    }

    /*
     ** try to close a goto with existing labels; this solves backward jumps
     */
    boolean findlabel(int g) {
        int i;
        BlockCnt bl = fs.bl;
        Dyndata dyd = this.dyd;
        Labeldesc gt = dyd.gt[g];
        /* check labels in current block for a match */
        for (i = bl.firstlabel; i < dyd.n_label; i++) {
            Labeldesc lb = dyd.label[i];
            if (lb.name.eq_b(gt.name)) {  /* correct label? */
                if (gt.nactvar > lb.nactvar &&
                        (bl.upval || dyd.n_label > bl.firstlabel))
                    fs.patchclose(gt.pc, lb.nactvar);
                closegoto(g, lb);  /* close it */
                return true;
            }
        }
        return false;  /* label not found; cannot close goto */
    }

    /* Caller must grow() the vector before calling this. */
    int newlabelentry(Labeldesc[] l, int index, LuaString name, int line, int pc) {
        l[index] = new Labeldesc(name, pc, line, fs.nactvar);
        return index;
    }

    /*
     ** check whether new label 'lb' matches any pending gotos in current
     ** block; solves forward jumps
     */
    void findgotos(Labeldesc lb) {
        Labeldesc[] gl = dyd.gt;
        int i = fs.bl.firstgoto;
        while (i < dyd.n_gt) {
            if (gl[i].name.eq_b(lb.name))
                closegoto(i, lb);
            else
                i++;
        }
    }


    /*
     ** create a label named "break" to resolve break statements
     */
    void breaklabel() {
        LuaString n = LuaString.valueOf("break");
        int l = newlabelentry(dyd.label = grow(dyd.label, dyd.n_label + 1), dyd.n_label++, n, 0, fs.pc);
        findgotos(dyd.label[l]);
    }

    void continuelabel() {
        LuaString n = LuaString.valueOf("continue");
        int l = newlabelentry(dyd.label = grow(dyd.label, dyd.n_label + 1), dyd.n_label++, n, 0, fs.pc);
        findgotos(dyd.label[l]);
    }

    /*
     ** generates an error for an undefined 'goto'; choose appropriate
     ** message when label name is a reserved word (which can only be 'break')
     */
    void undefgoto(Labeldesc gt) {
        String msg = L.pushfstring(isReservedKeyword(gt.name.tojstring())
                ? "<" + gt.name + "> at line " + gt.line + " not inside a loop"
                : "no visible label '" + gt.name + "' for <goto> at line " + gt.line);
        semerror(msg);
    }

    Prototype addprototype() {
        Prototype clp;
        Prototype f = fs.f;  /* prototype of current function */
        if (f.p == null || fs.np >= f.p.length) {
            f.p = realloc(f.p, Math.max(1, fs.np * 2));
        }
        f.p[fs.np++] = clp = new Prototype();
        return clp;
    }

    void codeclosure(expdesc v) {
        FuncState fs = this.fs.prev;
        v.init(VRELOCABLE, fs.codeABx(OP_CLOSURE, 0, fs.np - 1));
        fs.exp2nextreg(v);  /* fix it at stack top (for GC) */
    }

    void open_func(FuncState fs, BlockCnt bl) {
        fs.prev = this.fs;  /* linked list of funcstates */
        fs.ls = this;
        this.fs = fs;
        fs.pc = 0;
        fs.lasttarget = -1;
        fs.jpc = new IntPtr(NO_JUMP);
        fs.freereg = 0;
        fs.nk = 0;
        fs.np = 0;
        fs.nups = 0;
        fs.nlocvars = 0;
        fs.nactvar = 0;
        fs.firstlocal = dyd.n_actvar;
        fs.bl = null;
        fs.f.source = this.source;
        fs.f.maxstacksize = 2;  /* registers 0/1 are always valid */
        fs.enterblock(bl, false);
    }

    void close_func() {
        FuncState fs = this.fs;
        Prototype f = fs.f;
        fs.ret(0, 0); /* final return */
        fs.leaveblock();
        f.code = realloc(f.code, fs.pc);
        f.lineinfo = realloc(f.lineinfo, fs.pc);
        f.k = realloc(f.k, fs.nk);
        f.p = realloc(f.p, fs.np);
        f.locvars = realloc(f.locvars, fs.nlocvars);
        f.upvalues = realloc(f.upvalues, fs.nups);
        _assert(fs.bl == null);
        this.fs = fs.prev;
        // last token read was anchored in defunct function; must reanchor it
        // ls.anchor_token();
    }

    /*============================================================*/
    /* GRAMMAR RULES */
    /*============================================================*/

    void fieldsel(expdesc v) {
        /* fieldsel -> ['.' | ':'] NAME */
        FuncState fs = this.fs;
        expdesc key = new expdesc();
        fs.exp2anyregup(v);
        this.next(); /* skip the dot or colon */
        this.checkname(key);
        fs.indexed(v, key);
    }

    void yindex(expdesc v) {
        /* index -> '[' expr ']' */
        this.next(); /* skip the '[' */
        this.expr(v);
        this.fs.exp2val(v);
        this.checknext(']');
    }


    /*
     ** {======================================================================
     ** Rules for Constructors
     ** =======================================================================
     */


    static class ConsControl {
        expdesc v = new expdesc(); /* last list item read */
        expdesc t; /* table descriptor */
        int nh; /* total number of `record' elements */
        int na; /* total number of array elements */
        int tostore; /* number of array elements pending to be stored */
    }

    ;


    void recfield(ConsControl cc) {
        /* recfield -> (NAME | `['exp1`]') = exp1 */
        FuncState fs = this.fs;
        int reg = this.fs.freereg;
        expdesc key = new expdesc();
        expdesc val = new expdesc();
        int rkkey;
        boolean isstr = false;
        boolean isfunc = false;
        if (this.t.token == TK_FUNCTION) {
            isfunc = true;
            fs.checklimit(cc.nh, MAX_INT, "items in a constructor");
            next();
            this.checkname(key);
        } else if (this.t.token == TK_STRING) {
            isstr = true;
            fs.checklimit(cc.nh, MAX_INT, "items in a constructor");
            this.checkstring(key);
        } else if (this.t.token == TK_NAME) {
            fs.checklimit(cc.nh, MAX_INT, "items in a constructor");
            this.checkname(key);
        } else if (this.t.token == TK_NUMBER) {
            isstr = true;
            fs.checklimit(cc.nh, MAX_INT, "items in a constructor");
            key.init(VKNUM, 0);
            key.u.setNval(this.t.seminfo.r);
            next();
        } else {
            /* this.t.token == '[' */
            this.yindex(key);
        }
        cc.nh++;
        if (!isfunc && (!isstr || !testnext(':')))
            this.checknext('=');
        rkkey = fs.exp2RK(key);
        if (isfunc)
            this.body(val, false, this.linenumber);
        else
            this.expr(val);
        fs.codeABC(Lua.OP_SETTABLE, cc.t.u.info, rkkey, fs.exp2RK(val));
        fs.freereg = (short) reg; /* free registers */
    }

    void listfield(ConsControl cc) {
        this.expr(cc.v);
        fs.checklimit(cc.na, MAX_INT, "items in a constructor");
        cc.na++;
        cc.tostore++;
    }


    void constructor(expdesc t) {
        /* constructor -> ?? */
        FuncState fs = this.fs;
        int line = this.linenumber;
        int pc = fs.codeABC(Lua.OP_NEWTABLE, 0, 0, 0);
        ConsControl cc = new ConsControl();
        cc.na = cc.nh = cc.tostore = 0;
        cc.t = t;
        t.init(VRELOCABLE, pc);
        cc.v.init(VVOID, 0); /* no value (yet) */
        fs.exp2nextreg(t); /* fix it at stack top (for gc) */
        this.checknext('{');
        do {
            _assert(cc.v.k == VVOID || cc.tostore > 0);
            if (this.t.token == '}')
                break;
            fs.closelistfield(cc);
            switch (this.t.token) {
                case TK_IMPORT:
                    importstat(cc);
                    break;
                case TK_STRING:
                    this.lookahead();
                    if (this.lookahead.token != '=' && this.lookahead.token != ':') /* expression? */
                        this.listfield(cc);
                    else
                        this.recfield(cc);
                    break;

                case TK_FUNCTION:
                    this.lookahead();
                    if (this.lookahead.token != TK_NAME) /* expression? */
                        this.listfield(cc);
                    else
                        this.recfield(cc);
                    break;

                case TK_NUMBER:
                case TK_NAME: { /* may be listfields or recfields */
                    this.lookahead();
                    if (this.lookahead.token != '=') /* expression? */
                        this.listfield(cc);
                    else
                        this.recfield(cc);
                    break;
                }
                case '[': { /* constructor_item -> recfield */
                    this.recfield(cc);
                    break;
                }
                default: { /* constructor_part -> listfield */
                    this.listfield(cc);
                    break;
                }
            }
        } while (this.testnext(',') || this.testnext(';'));
        this.check_match('}', '{', line);
        fs.lastlistfield(cc);
        InstructionPtr i = new InstructionPtr(fs.f.code, pc);
        SETARG_B(i, luaO_int2fb(cc.na)); /* set initial array size */
        SETARG_C(i, luaO_int2fb(cc.nh));  /* set initial table size */
    }

    void constructorList(expdesc t) {
        /* constructor -> ?? */
        FuncState fs = this.fs;
        int line = this.linenumber;
        int pc = fs.codeABx(Lua.OP_NEWLIST, 0, 0);
        ConsControl cc = new ConsControl();
        cc.na = cc.nh = cc.tostore = 0;
        cc.t = t;
        t.init(VRELOCABLE, pc);
        cc.v.init(VVOID, 0); /* no value (yet) */
        fs.exp2nextreg(t); /* fix it at stack top (for gc) */
        this.checknext('[');
        do {
            _assert(cc.v.k == VVOID || cc.tostore > 0);
            if (this.t.token == ']')
                break;
            fs.closelistfield(cc);
            /* constructor_part -> listfield */
            this.listfield(cc);
        } while (this.testnext(',') || this.testnext(';'));
        this.check_match(']', '[', line);
        fs.lastlistfield(cc);
        InstructionPtr i = new InstructionPtr(fs.f.code, pc);
        SETARG_B(i, luaO_int2fb(cc.na)); /* set initial array size */
    }

    /*
     ** converts an integer to a "floating point byte", represented as
     ** (eeeeexxx), where the real value is (1xxx) * 2^(eeeee - 1) if
     ** eeeee != 0 and (xxx) otherwise.
     */
    static int luaO_int2fb(int x) {
        int e = 0;  /* expoent */
        while (x >= 16) {
            x = (x + 1) >> 1;
            e++;
        }
        if (x < 8) return x;
        else return ((e + 1) << 3) | (((int) x) - 8);
    }


    /* }====================================================================== */

    void parlist() {
        /* parlist -> [ param { `,' param } ] */
        FuncState fs = this.fs;
        Prototype f = fs.f;
        int nparams = 0;
        f.is_vararg = 0;
        if (this.t.token != ')') {  /* is `parlist' not empty? */
            do {
                switch (this.t.token) {
                    case TK_NAME: {  /* param . NAME */
                        this.new_localvar(this.str_checkname());
                        ++nparams;
                        break;
                    }
                    case TK_DOTS: {  /* param . `...' */
                        this.next();
                        f.is_vararg = 1;
                        break;
                    }
                    default:
                        this.syntaxerror("<name> or " + LUA_QL("...") + " expected");
                }
            } while ((f.is_vararg == 0) && this.testnext(','));
        }
        this.adjustlocalvars(nparams);
        f.numparams = fs.nactvar;
        fs.reserveregs(fs.nactvar);  /* reserve register for parameters */
    }

    void body(expdesc e, boolean needself, int line) {
        /* body -> `(' parlist `)' chunk END */
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        if (t.seminfo.ts != null)
            new_fs.f.name = t.seminfo.ts.tojstring();
        open_func(new_fs, bl);
        this.checknext('(');
        if (needself) {
            new_localvarliteral("self");
            adjustlocalvars(1);
        }
        this.parlist();
        this.checknext(')');
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('{');
        if (Lua.LUA_LOCAL_ENV) {
            this.new_localvar(this.envn);
            expdesc env = new expdesc();
            FuncState.singlevaraux(fs, this.envn, env, 1);
            this.adjust_assign(1, 1, env);
            this.adjustlocalvars(1);
        }
        this.statlist();
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();

        if (left)
            this.check_match('}', TK_FUNCTION, line);
        else
            this.check_match(TK_END, TK_FUNCTION, line);
    }

    void lambdabody(expdesc e, int line) {
        /* body -> `(' parlist `)' chunk END */
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        open_func(new_fs, bl);
        boolean left = this.testnext('(');
        this.parlist();
        if (left)
            this.checknext(')');

        if (Lua.LUA_LOCAL_ENV) {
            this.new_localvar(this.envn);
            expdesc env = new expdesc();
            FuncState.singlevaraux(fs, this.envn, env, 1);
            this.adjust_assign(1, 1, env);
            this.adjustlocalvars(1);
        }
        this.checknext(':');
        retstat();
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
    }

    void deferbody(expdesc e, int line) {
        /* body -> `(' parlist `)' chunk END */
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        open_func(new_fs, bl);
        new_fs.f.name = "defer";
        boolean hasarg = this.testnext('(');
        if (hasarg) {
            this.parlist();
            this.checknext(')');
        }
        this.statement();
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
    }

    void whenbody(expdesc e, int line) {
        /* body -> `(' parlist `)' chunk END */
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        open_func(new_fs, bl);
        new_fs.f.name = "when";
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('(');
        expdesc control = new expdesc();
        expr(control); /* read control */
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }

        IntPtr escapelist = new IntPtr(NO_JUMP);  /* exit list for finished parts */

        while (t.token == TK_CASE) {
            test_case_block(escapelist, control.clone());  /* CASE cond THEN block */
        }
        if (testnext(TK_DEFAULT))
            block();  /* `default' part */

        fs.patchtohere(escapelist.i);  /* patch escape list to 'switch' end */

        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();

        if (left)
            this.check_match('}', TK_WHEN, line);
        else
            this.check_match(TK_END, TK_WHEN, line);
    }

    boolean trybody(expdesc e, int token, int line) {
        /* body -> `(' parlist `)' chunk END */
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        open_func(new_fs, bl);
        if (token == TK_CATCH) {
            boolean hasarg = this.testnext('(');
            if (hasarg) {
                this.parlist();
                this.checknext(')');
            }
        }
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('{');
        this.statlist();
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
        if (left)
            check_match('}', token, line);
        return left;
    }

    int explist(expdesc v) {
        /* explist1 -> expr { `,' expr } */
        int n = 1; /* at least one expression */
        this.expr(v);
        while (this.testnext(',')) {
            fs.exp2nextreg(v);
            this.expr(v);
            n++;
        }
        return n;
    }


    void funcargs(expdesc f, int line) {
        FuncState fs = this.fs;
        expdesc args = new expdesc();
        int base, nparams;
        switch (this.t.token) {
            case '(': { /* funcargs -> `(' [ explist1 ] `)' */
                this.next();
                if (this.t.token == ')') /* arg list is empty? */
                    args.k = VVOID;
                else {
                    this.explist(args);
                    fs.setmultret(args);
                }
                this.check_match(')', '(', line);
                break;
            }
            case '{': { /* funcargs -> constructor */
                this.constructor(args);
                break;
            }
            case TK_STRING: { /* funcargs -> STRING */
                this.codestring(args, this.t.seminfo.ts);
                this.next(); /* must use `seminfo' before `next' */
                break;
            }
            default: {
                this.syntaxerror("function arguments expected");
                return;
            }
        }
        _assert(f.k == VNONRELOC);
        base = f.u.info; /* base register for call */
        if (hasmultret(args.k))
            nparams = Lua.LUA_MULTRET; /* open call */
        else {
            if (args.k != VVOID)
                fs.exp2nextreg(args); /* close last argument */
            nparams = fs.freereg - (base + 1);
        }
        f.init(VCALL, fs.codeABC(Lua.OP_CALL, base, nparams + 1, 2));
        fs.fixline(line);
        fs.freereg = (short) (base + 1);  /* call remove function and arguments and leaves
         * (unless changed) one result */
    }


    /*
     ** {======================================================================
     ** Expression parsing
     ** =======================================================================
     */

    void primaryexp(expdesc v) {
        /* primaryexp -> NAME | '(' expr ')' */
        switch (t.token) {
            case '(': {
                int line = linenumber;
                this.next();
                this.expr(v);
                this.check_match(')', '(', line);
                fs.dischargevars(v);
                return;
            }
            case TK_NAME: {
                singlevar(v);
                return;
            }
            default: {
                this.syntaxerror("unexpected symbol " + t.token);
                return;
            }
        }
    }


    void suffixedexp(expdesc v) {
		/* suffixedexp ->
       	primaryexp { '.' NAME | '[' exp ']' | ':' NAME funcargs | funcargs } */
        int line = linenumber;
        primaryexp(v);
        for (; ; ) {
            switch (t.token) {
                case '.': { /* fieldsel */
                    this.fieldsel(v);
                    break;
                }
                case '[': { /* `[' exp1 `]' */
                    expdesc key = new expdesc();
                    fs.exp2anyregup(v);
                    this.yindex(key);
                    fs.indexed(v, key);
                    break;
                }
                case ':': { /* `:' NAME funcargs */
                    expdesc key = new expdesc();
                    this.next();
                    this.checkname(key);
                    fs.self(v, key);
                    this.funcargs(v, line);
                    break;
                }
                case '(':
                case TK_STRING:
                case '{': { /* funcargs */
                    fs.exp2nextreg(v);
                    this.funcargs(v, line);
                    break;
                }
                default:
                    return;
            }
        }
    }


    void simpleexp(expdesc v) {
        /*
         * simpleexp -> NUMBER | STRING | NIL | true | false | ... | constructor |
         * FUNCTION body | primaryexp
         */
        switch (this.t.token) {
            case TK_NUMBER: {
                v.init(VKNUM, 0);
                v.u.setNval(this.t.seminfo.r);
                break;
            }
            case TK_STRING: {
                this.codestring(v, this.t.seminfo.ts);
                break;
            }
            case TK_NIL: {
                v.init(VNIL, 0);
                break;
            }
            case TK_TRUE: {
                v.init(VTRUE, 0);
                break;
            }
            case TK_FALSE: {
                v.init(VFALSE, 0);
                break;
            }
            case TK_DOTS: { /* vararg */
                FuncState fs = this.fs;
                this.check_condition(fs.f.is_vararg != 0, "cannot use " + LUA_QL("...")
                        + " outside a vararg function");
                v.init(VVARARG, fs.codeABC(Lua.OP_VARARG, 0, 1, 0));
                break;
            }
            case '{': { /* constructor */
                this.constructor(v);
                return;
            }
            case '[': { /* constructor */
                this.constructorList(v);
                return;
            }
            case TK_FUNCTION: {
                this.next();
                t.seminfo.ts = null;
                this.body(v, false, this.linenumber);
                return;
            }
            case TK_LAMBDA: {
                this.next();
                this.lambdabody(v, this.linenumber);
                return;
            }
            case TK_WHEN: {
                this.whenstat(v);
                //v.init(VCALL, whenstat()); /* stat -> funcstat */
                return;
            }
            case TK_IMPORT: {
                this.importstat(v);
                return;
            }
            default: {
                this.suffixedexp(v);
                return;
            }
        }
        this.next();
    }


    int getunopr(int op) {
        switch (op) {
            case TK_NOT:
                return OPR_NOT;
            case '-':
                return OPR_MINUS;
            case '#':
                return OPR_LEN;
            case '~':
                return OPR_BNOT;
            default:
                return OPR_NOUNOPR;
        }
    }


    int getbinopr(int op) {
        switch (op) {
            case '+':
                return OPR_ADD;
            case '-':
                return OPR_SUB;
            case '*':
                return OPR_MUL;
            case '/':
                return OPR_DIV;
            case '%':
                return OPR_MOD;
            case '^':
                return OPR_POW;
            case TK_CONCAT:
                return OPR_CONCAT;
            case TK_NE:
                return OPR_NE;
            case TK_EQ:
                return OPR_EQ;
            case '<':
                return OPR_LT;
            case TK_LE:
                return OPR_LE;
            case '>':
                return OPR_GT;
            case TK_GE:
                return OPR_GE;
            case TK_AND:
                return OPR_AND;
            case TK_OR:
                return OPR_OR;
            case TK_IDIV:
                return OPR_IDIV;
            case '&':
                return OPR_BAND;
            case '|':
                return OPR_BOR;
            case '~':
                return OPR_BXOR;
            case TK_SHL:
                return OPR_SHL;
            case TK_SHR:
                return OPR_SHR;
            default:
                return OPR_NOBINOPR;
        }
    }

    static class Priority {
        final byte left; /* left priority for each binary operator */

        final byte right; /* right priority */

        public Priority(int i, int j) {
            left = (byte) i;
            right = (byte) j;
        }
    }

    ;

    static Priority[] priority = {  /* ORDER OPR */
            new Priority(10, 10), new Priority(10, 10),           /* '+' '-' */
            new Priority(11, 11), new Priority(11, 11),           /* '*' '%' */
            new Priority(14, 13),                  /* '^' (right associative) */
            new Priority(11, 11), new Priority(11, 11),           /* '/' '//' */
            new Priority(6, 6), new Priority(4, 4), new Priority(5, 5),   /* '&' '|' '~' */
            new Priority(7, 7), new Priority(7, 7),           /* '<<' '>>' */
            new Priority(9, 8),                   /* '..' (right associative) */
            new Priority(3, 3), new Priority(3, 3), new Priority(3, 3),   /* ==, <, <= */
            new Priority(3, 3), new Priority(3, 3), new Priority(3, 3),   /* ~=, >, >= */
            new Priority(2, 2), new Priority(1, 1),            /* and, or */
            new Priority(1, 1)
    };

    static final int UNARY_PRIORITY = 12;  /* priority for unary operators */


    /*
     ** subexpr -> (simpleexp | unop subexpr) { binop subexpr }
     ** where `binop' is any binary operator with a priority higher than `limit'
     */
    int subexpr(expdesc v, int limit) {
        int op;
        int uop;
        this.enterlevel();
        uop = getunopr(this.t.token);
        if (uop != OPR_NOUNOPR) {
            int line = linenumber;
            this.next();
            this.subexpr(v, UNARY_PRIORITY);
            fs.prefix(uop, v, line);
        } else
            this.simpleexp(v);
        /* expand while operators have priorities higher than `limit' */
        op = getbinopr(this.t.token);
        while (op != OPR_NOBINOPR && priority[op].left > limit) {
            expdesc v2 = new expdesc();
            int line = linenumber;
            this.next();
            fs.infix(op, v);
            /* read sub-expression with higher priority */
            int nextop = this.subexpr(v2, priority[op].right);
            fs.posfix(op, v, v2, line);
            op = nextop;
        }
        this.leavelevel();
        return op; /* return first untreated operator */
    }

    void expr(expdesc v) {
        this.subexpr(v, 0);
    }

    /* }==================================================================== */



    /*
     ** {======================================================================
     ** Rules for Statements
     ** =======================================================================
     */

    boolean block_follow(boolean withuntil) {
        switch (t.token) {
            case TK_ELSE:
            case TK_ELSEIF:
            case TK_END:
            case TK_EOS:
            case TK_CASE:
            case TK_DEFAULT:
            case TK_CATCH:
            case TK_FINALLY:
            case '}':
                return true;
            case TK_UNTIL:
                return withuntil;
            default:
                return false;
        }
    }


    void block() {
        /* block -> chunk */
        FuncState fs = this.fs;
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, false);
        this.statlist();
        fs.leaveblock();
    }


    /*
     ** structure to chain all variables in the left-hand side of an
     ** assignment
     */
    static class LHS_assign {
        LHS_assign prev;
        /* variable (global, local, upvalue, or indexed) */
        expdesc v = new expdesc();
    }

    ;


    /*
     ** check whether, in an assignment to a local variable, the local variable
     ** is needed in a previous assignment (to a table). If so, save original
     ** local value in a safe place and use this safe copy in the previous
     ** assignment.
     */
    void check_conflict(LHS_assign lh, expdesc v) {
        FuncState fs = this.fs;
        short extra = (short) fs.freereg;  /* eventual position to save local variable */
        boolean conflict = false;
        for (; lh != null; lh = lh.prev) {
            if (lh.v.k == VINDEXED) {
                /* table is the upvalue/local being assigned now? */
                if (lh.v.u.ind_vt == v.k && lh.v.u.ind_t == v.u.info) {
                    conflict = true;
                    lh.v.u.ind_vt = VLOCAL;
                    lh.v.u.ind_t = extra;  /* previous assignment will use safe copy */
                }
                /* index is the local being assigned? (index cannot be upvalue) */
                if (v.k == VLOCAL && lh.v.u.ind_idx == v.u.info) {
                    conflict = true;
                    lh.v.u.ind_idx = extra;  /* previous assignment will use safe copy */
                }
            }
        }
        if (conflict) {
            /* copy upvalue/local value to a temporary (in position 'extra') */
            int op = (v.k == VLOCAL) ? Lua.OP_MOVE : Lua.OP_GETUPVAL;
            fs.codeABC(op, extra, v.u.info, 0);
            fs.reserveregs(1);
        }
    }

    void assignment(LHS_assign lh, int nvars) {
        expdesc e = new expdesc();
        this.check_condition(VLOCAL <= lh.v.k && lh.v.k <= VINDEXED,
                "syntax error");
        if (this.testnext(',')) {  /* assignment -> `,' primaryexp assignment */
            LHS_assign nv = new LHS_assign();
            nv.prev = lh;
            this.suffixedexp(nv.v);
            if (nv.v.k != VINDEXED)
                this.check_conflict(lh, nv.v);
            this.assignment(nv, nvars + 1);
        } else {  /* assignment . `=' explist1 */
            int nexps;
            this.checknext('=');
            nexps = this.explist(e);
            if (nexps != nvars) {
                this.adjust_assign(nvars, nexps, e);
                if (nexps > nvars)
                    this.fs.freereg -= nexps - nvars;  /* remove extra values */
            } else {
                fs.setoneret(e);  /* close last expression */
                fs.storevar(lh.v, e);
                return;  /* avoid default */
            }
        }
        e.init(VNONRELOC, this.fs.freereg - 1);  /* default assignment */
        fs.storevar(lh.v, e);
    }


    int cond() {
        /* cond -> exp */
        expdesc v = new expdesc();
        /* read condition */
        this.expr(v);
        /* `falses' are all equal here */
        if (v.k == VNIL)
            v.k = VFALSE;
        fs.goiftrue(v);
        return v.f.i;
    }

    void gotostat(int pc) {
        int line = linenumber;
        LuaString label;
        int g;
        if (testnext(TK_GOTO))
            label = str_checkname();
        else if (testnext(TK_CONTINUE))
            label = LuaString.valueOf("continue");
        else {
            next();  /* skip break */
            label = LuaString.valueOf("break");
        }
        g = newlabelentry(dyd.gt = grow(dyd.gt, dyd.n_gt + 1), dyd.n_gt++, label, line, pc);
        findlabel(g);  /* close it if label already defined */
    }


    /* skip no-op statements */
    void skipnoopstat() {
        while (t.token == ';' || t.token == TK_DBCOLON)
            statement();
    }


    void labelstat(LuaString label, int line) {
        /* label -> '::' NAME '::' */
        int l;  /* index of new label being created */
        fs.checkrepeated(dyd.label, dyd.n_label, label);  /* check for repeated labels */
        testnext(TK_DBCOLON);  /* skip double colon */
        /* create new entry for this label */
        l = newlabelentry(dyd.label = grow(dyd.label, dyd.n_label + 1), dyd.n_label++, label, line, fs.getlabel());
        skipnoopstat();  /* skip other no-op statements */
        if (block_follow(false)) {  /* label is last no-op statement in the block? */
            /* assume that locals are already out of scope */
            dyd.label[l].nactvar = fs.bl.nactvar;
        }
        findgotos(dyd.label[l]);
    }

    void whilestat(int line) {
        /* whilestat -> WHILE cond DO block END */
        FuncState fs = this.fs;
        int whileinit;
        int condexit;
        BlockCnt bl = new BlockCnt();
        this.next();  /* skip WHILE */
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('(');
        whileinit = fs.getlabel();
        condexit = this.cond();
        fs.enterblock(bl, true);
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        this.block();
        continuelabel();
        fs.patchlist(fs.jump(), whileinit);

        if (left)
            this.check_match('}', TK_WHILE, line);
        else
            this.check_match(TK_END, TK_WHILE, line);
        fs.leaveblock();
        fs.patchtohere(condexit);  /* false conditions finish the loop */
    }

    void repeatstat(int line) {
        /* repeatstat -> REPEAT block UNTIL cond */
        int condexit;
        FuncState fs = this.fs;
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('{');
        int repeat_init = fs.getlabel();
        BlockCnt bl1 = new BlockCnt();
        BlockCnt bl2 = new BlockCnt();
        fs.enterblock(bl1, true); /* loop block */
        fs.enterblock(bl2, false); /* scope block */
        this.next(); /* skip REPEAT */
        this.statlist();
        continuelabel();

        if (left) {
            this.check_match('}', TK_REPEAT, line);
        }
        this.check_match(TK_UNTIL, TK_REPEAT, line);
        condexit = this.cond(); /* read condition (inside scope block) */
        if (bl2.upval) { /* upvalues? */
            fs.patchclose(condexit, bl2.nactvar);
        }
        fs.leaveblock(); /* finish scope */
        fs.patchlist(condexit, repeat_init); /* close the loop */
        fs.leaveblock(); /* finish loop */
    }


    int exp1() {
        expdesc e = new expdesc();
        int k;
        this.expr(e);
        k = e.k;
        fs.exp2nextreg(e);
        return k;
    }


    boolean forbody(int base, int line, int nvars, boolean isnum, boolean left) {
        /* forbody -> DO block */
        BlockCnt bl = new BlockCnt();
        FuncState fs = this.fs;
        int prep, endfor;
        this.adjustlocalvars(3); /* control variables */
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        prep = isnum ? fs.codeAsBx(Lua.OP_FORPREP, base, NO_JUMP) : fs.jump();
        fs.enterblock(bl, false); /* scope for declared variables */
        this.adjustlocalvars(nvars);
        fs.reserveregs(nvars);
        this.block();
        continuelabel();
        fs.leaveblock(); /* end of scope for declared variables */
        fs.patchtohere(prep);
        if (isnum)  /* numeric for? */
            endfor = fs.codeAsBx(Lua.OP_FORLOOP, base, NO_JUMP);
        else {  /* generic for */
            fs.codeABC(Lua.OP_TFORCALL, base, 0, nvars);
            fs.fixline(line);
            endfor = fs.codeAsBx(Lua.OP_TFORLOOP, base + 2, NO_JUMP);
        }
        fs.patchlist(endfor, prep + 1);
        fs.fixline(line);
        return left;
    }

    boolean foreachbody(int base, int line, int nvars, boolean left) {
        /* forbody -> DO block */
        BlockCnt bl = new BlockCnt();
        FuncState fs = this.fs;
        int prep, endfor;
        this.adjustlocalvars(3); /* control variables */
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        prep = fs.jump();
        fs.enterblock(bl, false); /* scope for declared variables */
        this.adjustlocalvars(nvars);
        fs.reserveregs(nvars);
        this.block();
        continuelabel();
        fs.leaveblock(); /* end of scope for declared variables */
        fs.patchtohere(prep);
        fs.codeABC(Lua.OP_TFOREACH, base, 0, nvars);
        fs.fixline(line);
        endfor = fs.codeAsBx(Lua.OP_TFORLOOP, base + 2, NO_JUMP);
        fs.patchlist(endfor, prep + 1);
        fs.fixline(line);
        return left;
    }

    boolean fornum(LuaString varname, int line, boolean left) {
        /* fornum -> NAME = exp1,exp1[,exp1] forbody */
        FuncState fs = this.fs;
        int base = fs.freereg;
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_INDEX);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_LIMIT);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STEP);
        this.new_localvar(varname);
        this.checknext('=');
        this.exp1(); /* initial value */
        this.checknext(',');
        this.exp1(); /* limit */
        if (this.testnext(','))
            this.exp1(); /* optional step */
        else { /* default step = 1 */
            fs.codeABx(Lua.OP_LOADK, fs.freereg, fs.numberK(LuaInteger.valueOf(1)));
            fs.reserveregs(1);
        }
        return this.forbody(base, line, 1, true, left);
    }


    boolean forlist(LuaString indexname, boolean left) {
        /* forlist -> NAME {,NAME} IN explist1 forbody */
        FuncState fs = this.fs;
        expdesc e = new expdesc();
        int nvars = 4;   /* gen, state, control, plus at least one declared var */
        int line;
        int base = fs.freereg;
        /* create control variables */
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_GENERATOR);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STATE);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_CONTROL);
        /* create declared variables */
        this.new_localvar(indexname);
        while (this.testnext(',')) {
            this.new_localvar(this.str_checkname());
            ++nvars;
        }
        boolean foreach = this.testnext(':');
        this.testnext(TK_IN);
        line = this.linenumber;
        this.adjust_assign(3, this.explist(e), e);
        fs.checkstack(3); /* extra space to call generator */
        if (foreach)
            return this.foreachbody(base, line, nvars - 3, left);
        else
            return this.forbody(base, line, nvars - 3, false, left);
    }


    void forstat(int line) {
        /* forstat -> FOR (fornum | forlist) END */
        FuncState fs = this.fs;
        LuaString varname;
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, true); /* scope for loop and control variables */
        this.next(); /* skip `for' */
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('(');
        varname = this.str_checkname(); /* first variable name */
        switch (this.t.token) {
            case '=':
                left = this.fornum(varname, line, left);
                break;
            case ',':
            case TK_IN:
                left = this.forlist(varname, left);
                break;
            default:
                left = this.forlist(varname, left);
                //this.syntaxerror(LUA_QL("=") + " or " + LUA_QL("in") + " expected");
        }

        if (left)
            this.check_match('}', TK_FOR, line);
        else
            this.check_match(TK_END, TK_FOR, line);
        fs.leaveblock(); /* loop scope (`break' jumps to this point) */
    }


    boolean test_then_block(IntPtr escapelist) {
        /* test_then_block -> [IF | ELSEIF] cond THEN block */
        expdesc v = new expdesc();
        BlockCnt bl = new BlockCnt();
        int jf = 0;  /* instruction to skip 'then' code (if condition is false) */
        int token = t.token;
        int line = this.linenumber;
        this.next(); /* skip IF or ELSEIF */
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('(');
        expr(v);  /* read expression */
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_THEN);
        }
        if (t.token == TK_GOTO || t.token == TK_BREAK || t.token == TK_CONTINUE) {
            fs.goiffalse(v); /* will jump to label if condition is true */
            fs.enterblock(bl, false); /* must enter block before 'goto' */
            gotostat(v.t.i); /* handle goto/break */
            skipnoopstat(); /* skip other no-op statements */
            if (block_follow(false)) { /* 'goto' is the entire block? */
                fs.leaveblock();
                return left; /* and that is it */
            } else
                syntaxerror("unreachable statement");
            /* must skip over 'then' part if condition is false */
            //jf = fs.jump();
        } else { /* regular case (not goto/break) */
            fs.goiftrue(v); /* skip over block if condition is false */
            fs.enterblock(bl, false);
            jf = v.f.i;
        }
        statlist(); /* `then' part */
        fs.leaveblock();
        if (left)
            this.check_match('}', token, line);
        if (t.token == TK_ELSE || t.token == TK_ELSEIF)
            fs.concat(escapelist, fs.jump()); /* must jump over it */
        fs.patchtohere(jf);
        return left;
    }


    void ifstat(int line) {
        boolean left = false;
        IntPtr escapelist = new IntPtr(NO_JUMP);  /* exit list for finished parts */
        left = test_then_block(escapelist);  /* IF cond THEN block */
        while (t.token == TK_ELSEIF)
            left = test_then_block(escapelist);  /* ELSEIF cond THEN block */
        if (testnext(TK_ELSE)) {
            left = Lua.LUA_BLOCK_CURLY && this.testnext('{');
            block();  /* `else' part */
            if (left)
                check_match('}', TK_IF, line);
        }
        if (!left)
            check_match(TK_END, TK_IF, line);
        fs.patchtohere(escapelist.i);  /* patch escape list to 'if' end */
    }

    void test_case_block(IntPtr escapelist, expdesc control) {
        /* test_case_block -> CASE value THEN block */
        expdesc v = new expdesc();
        BlockCnt bl = new BlockCnt();
        int jf = 0;  /* instruction to skip 'then' code (if condition is false) */
        this.next(); /* skip CASE */
        expdesc gcontrol = control.clone();
        enterlevel();
        fs.infix(OPR_EQ, control);
        expr(v);  /* read condition */
        fs.posfix(OPR_EQ, control, v, linenumber);
        while (testnext(',')) {
            expdesc c = gcontrol.clone();
            fs.infix(OPR_EQ, c);
            expr(v);  /* read condition */
            fs.posfix(OPR_EQ, c, v, linenumber);
            fs.infix(OPR_OR, control);
            fs.posfix(OPR_OR, control, c, linenumber);
        }
        leavelevel();
        this.testnext(TK_THEN);

        if (t.token == TK_GOTO || t.token == TK_BREAK || t.token == TK_CONTINUE) {
            fs.goiffalse(control); /* will jump to label if condition is true */
            fs.enterblock(bl, false); /* must enter block before 'goto' */
            gotostat(control.t.i); /* handle goto/break */
            skipnoopstat(); /* skip other no-op statements */
            if (block_follow(false)) { /* 'goto' is the entire block? */
                fs.leaveblock();
                return; /* and that is it */
            } else
                syntaxerror("unreachable statement");
            /* must skip over 'case' part if condition is false */
            //jf = fs.jump();
        } else { /* regular case (not goto/break) */
            fs.goiftrue(control); /* skip over block if condition is false */
            fs.enterblock(bl, false);
            jf = control.f.i;
        }
        statlist(); /* `case' part */
        fs.leaveblock();
        if (t.token == TK_CASE || t.token == TK_DEFAULT)
            fs.concat(escapelist, fs.jump()); /* must jump over it */
        fs.patchtohere(jf);
    }

    void switchstat(int line) {
        IntPtr escapelist = new IntPtr(NO_JUMP);  /* exit list for finished parts */
        expdesc control = new expdesc();
        this.next(); /* skip SWITCH */
        boolean left = Lua.LUA_BLOCK_CURLY && this.testnext('(');
        expr(control); /* read control */
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        while (t.token == TK_CASE) {
            test_case_block(escapelist, control.clone());  /* CASE cond THEN block */
        }
        if (testnext(TK_DEFAULT))
            block();  /* `default' part */

        if (left)
            check_match('}', TK_SWITCH, line);
        else
            check_match(TK_END, TK_SWITCH, line);
        fs.patchtohere(escapelist.i);  /* patch escape list to 'switch' end */

    }

    void localfunc() {
        expdesc b = new expdesc();
        FuncState fs = this.fs;
        this.new_localvar(this.str_checkname());
        this.adjustlocalvars(1);
        this.body(b, false, this.linenumber);
        /* debug information will only see the variable after this point! */
        fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
    }

    void importstat() {
        this.next();
        FuncState fs = this.fs;
        boolean left = this.testnext('(');
        do {
            String varname = null;
            String pkgname = null;
            if (t.token == TK_NAME) {
                varname = this.str_checkname().tojstring();
            }
            LuaString classname = this.str_check();
            if (varname == null) {
                varname = classname.tojstring();
                int idx = varname.lastIndexOf("$");
                if (idx < 0)
                    idx = varname.lastIndexOf(".");
                if (idx > 0) {
                    pkgname = varname.substring(0, idx + 1);
                    varname = varname.substring(idx + 1);
                }

            }
            if (varname.equals("*")) {
                addPackage(pkgname);
                mClass.clear();
                continue;
            }
            this.new_localvarliteral(varname);
            this.adjustlocalvars(1);
            fs.codeABx(Lua.OP_IMPORT, fs.nactvar - 1, fs.stringK(classname));
            fs.reserveregs(1);
        } while (this.testnext(','));
        if (left)
            this.checknext(')');
    }

    void importstat(expdesc e) {
        this.next();
        FuncState fs = this.fs;
        LuaString classname = this.str_check();
        String varname = classname.tojstring();
        int idx = varname.lastIndexOf("$");
        if (idx < 0)
            idx = varname.lastIndexOf(".");
        if (idx > 0) {
            String pkgname = varname.substring(0, idx);
            varname = varname.substring(idx + 1);
            if (varname.equals("*")) {
                e.init(VRELOCABLE, fs.codeABx(Lua.OP_LOADP, e.u.info, fs.stringK(LuaValue.valueOf(pkgname))));
                return;
            }
        }
        e.init(VRELOCABLE, fs.codeABx(Lua.OP_IMPORT, e.u.info, fs.stringK(classname)));
    }

    void importstat(ConsControl cc) {
        this.next();
        FuncState fs = this.fs;
        LuaString classname = this.str_check();
        String varname = classname.tojstring();
        int idx = varname.lastIndexOf("$");
        if (idx < 0)
            idx = varname.lastIndexOf(".");
        if (idx > 0) {
            String pkgname = varname.substring(0, idx + 1);
            varname = varname.substring(idx + 1);
            if (varname.equals("*")) {
                addPackage(pkgname);
                mClass.clear();
                return;
            }
        }
        cc.v.init(VRELOCABLE, fs.codeABx(Lua.OP_IMPORT, cc.v.u.info, fs.stringK(classname)));
        fs.checklimit(cc.na, MAX_INT, "items in a constructor");
        cc.na++;
        cc.tostore++;
    }

    void modulestat() {
        this.next();
        FuncState fs = this.fs;
        boolean left = this.testnext('(');
        LuaString classname = this.str_check();
        this.new_localvar(this.envn);
        this.adjustlocalvars(1);
        fs.codeABx(Lua.OP_MODULE, fs.nactvar - 1, fs.stringK(classname));
        fs.reserveregs(1);
        if (left)
            this.checknext(')');
    }

    /*void modulebody(expdesc e, int line) {
        / * body -> `(' parlist `)' chunk END * /
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        new_fs.f.name="module";
        open_func(new_fs, bl);
        FuncState fs = new_fs;
        boolean left = this.testnext('(');
        LuaString classname = this.str_check();
        this.new_localvar(this.envn);
        this.adjustlocalvars(1);
        fs.codeABx(Lua.OP_MODULE, fs.nactvar - 1, fs.stringK(classname));
        fs.reserveregs(1);
        if (left)
            this.checknext(')');
        left = Lua.LUA_BLOCK_CURLY && this.testnext('{');
        this.statlist();
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();

        if (left)
            this.check_match('}', TK_FUNCTION, line);
    }

    void modulestat() {
        this.next();
        FuncState fs = this.fs;
        expdesc b = new expdesc();
        //this.new_localvarliteral("(when)");
        //this.adjustlocalvars(1);
        this.modulebody(b, this.linenumber);
        / * debug information will only see the variable after this point! * /
        //fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        //return fs.codeABC(Lua.OP_CALL, fs.nactvar - 1, 0, 2);
        fs.codeABC(Lua.OP_CALL, b.u.info, 0, 2);

    }*/


    void deferstat() {
        this.next();
        FuncState fs = this.fs;
        expdesc b = new expdesc();
        //this.new_localvarliteral("(defer)");
        //this.adjustlocalvars(1);
        this.deferbody(b, this.linenumber);
        /* debug information will only see the variable after this point! */
        fs.codeABC(Lua.OP_DEFER, b.u.info, 0, 0);
        //fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
    }

    int whenstat() {
        this.next();
        FuncState fs = this.fs;
        expdesc b = new expdesc();
        //this.new_localvarliteral("(when)");
        //this.adjustlocalvars(1);
        this.whenbody(b, this.linenumber);
        /* debug information will only see the variable after this point! */
        //fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        //return fs.codeABC(Lua.OP_CALL, fs.nactvar - 1, 0, 2);
        return fs.codeABC(Lua.OP_CALL, b.u.info, 0, 2);
    }

    void whenstat(expdesc b) {
        this.next();
        FuncState fs = this.fs;
        //this.new_localvarliteral("(when)");
        //this.adjustlocalvars(1);
        this.whenbody(b, this.linenumber);
        /* debug information will only see the variable after this point! */
        //fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        b.init(VCALL, fs.codeABC(Lua.OP_CALL, b.u.info, 0, 2));
    }

    void trystat() {
        int ra = 0, rb = 0, rc = 0;
        boolean left = false;
        int line = this.linenumber;
        this.next();
        expdesc b = new expdesc();
        FuncState fs = this.fs;
        //ra = fs.freereg;
        //this.new_localvarliteral("(try)");
        //this.adjustlocalvars(1);
        left = this.trybody(b, TK_TRY, this.linenumber);
        /* debug information will only see the variable after this point! */
        //fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        ra = b.u.info;
        if (this.testnext(TK_CATCH)) {
			/*rb = fs.freereg;
			this.new_localvarliteral("(catch)");
			this.adjustlocalvars(1);*/
            left = this.trybody(b, TK_CATCH, this.linenumber);
            /* debug information will only see the variable after this point! */
            //fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
            rb = b.u.info;
        }

        if (this.testnext(TK_FINALLY)) {
            left = this.trybody(b, TK_FINALLY, this.linenumber);
            /* debug information will only see the variable after this point! */
            //fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
            rc = b.u.info;
        }

        if (!left)
            check_match(TK_END, TK_TRY, line);

        fs.codeABC(Lua.OP_TCALL, ra, rb, rc);
    }

    void loadlist(int n) {
        LocVars[] ls = fs.f.locvars;
        expdesc var = new expdesc();
        int nl = fs.nlocvars;
        LuaString l = ls[nl - n].varname;
        FuncState.singlevaraux(fs, l, var, 1);
        for (int i = nl - n + 1; i < nl; i++) {
            l = ls[i].varname;
            fs.exp2nextreg(var);
            FuncState.singlevaraux(fs, l, var, 1);
        }
        this.adjust_assign(n, n, var);
        this.adjustlocalvars(n);
    }

    void localstat() {
        /* stat -> LOCAL NAME {`,' NAME} [`=' explist1] */
        int nvars = 0;
        int nexps;
        expdesc e = new expdesc();
        boolean def = testnext('=') || testnext(':');
        do {
            this.new_localvar(this.str_checkname());
            ++nvars;
        } while (this.testnext(','));

        if (def) {
            loadlist(nvars);
            return;
        }

        if (nvars == 1 && testtoken('(')) {
            expdesc b = new expdesc();
            this.adjustlocalvars(1);
            this.body(b, false, this.linenumber);
            /* debug information will only see the variable after this point! */
            fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
            return;
        }

        if (this.testnext('='))
            nexps = this.explist(e);
        else {
            e.k = VVOID;
            nexps = 0;
        }
        this.adjust_assign(nvars, nexps, e);
        this.adjustlocalvars(nvars);
    }


    boolean funcname(expdesc v) {
        /* funcname -> NAME {field} [`:' NAME] */
        boolean ismethod = false;
        this.singlevar(v);
        while (this.t.token == '.')
            this.fieldsel(v);
        if (this.t.token == ':') {
            ismethod = true;
            this.fieldsel(v);
        }
        return ismethod;
    }


    void funcstat(int line) {
        /* funcstat -> FUNCTION funcname body */
        boolean needself;
        expdesc v = new expdesc();
        expdesc b = new expdesc();
        this.next(); /* skip FUNCTION */
        needself = this.funcname(v);
        this.body(b, needself, line);
        fs.storevar(v, b);
        fs.fixline(line); /* definition `happens' in the first line */
    }


    void exprstat() {
        /* stat -> func | assignment */
        FuncState fs = this.fs;
        LHS_assign v = new LHS_assign();
        this.suffixedexp(v.v);
        if (t.token == '=' || t.token == ',') { /* stat -> assignment ? */
            v.prev = null;
            assignment(v, 1);
        } else {  /* stat -> func */
            check_condition(v.v.k == VCALL, "syntax error");
            if (v.v.k == VCALL)
                SETARG_C(fs.getcodePtr(v.v), 1);  /* call statement uses no results */
        }
    }

    void retstat() {
        /* stat -> RETURN explist */
        FuncState fs = this.fs;
        expdesc e = new expdesc();
        int first, nret; /* registers with returned values */
        if (block_follow(true) || this.t.token == ';')
            first = nret = 0; /* return no values */
        else {
            nret = this.explist(e); /* optional return values */
            if (hasmultret(e.k)) {
                fs.setmultret(e);
                if (e.k == VCALL && nret == 1) { /* tail call? */
                    SET_OPCODE(fs.getcodePtr(e), Lua.OP_TAILCALL);
                    _assert(Lua.GETARG_A(fs.getcode(e)) == fs.nactvar);
                }
                first = fs.nactvar;
                nret = Lua.LUA_MULTRET; /* return all values */
            } else {
                if (nret == 1) /* only one single value? */
                    first = fs.exp2anyreg(e);
                else {
                    fs.exp2nextreg(e); /* values must go to the `stack' */
                    first = fs.nactvar; /* return all `active' values */
                    _assert(nret == fs.freereg - first);
                }
            }
        }
        fs.ret(first, nret);
        testnext(';');  /* skip optional semicolon */
    }

    void statement() {
        int line = this.linenumber; /* may be needed for error messages */
        enterlevel();
        switch (this.t.token) {
            case ';': { /* stat -> ';' (empty statement) */
                next(); /* skip ';' */
                break;
            }
            case TK_IF: { /* stat -> ifstat */
                this.ifstat(line);
                break;
            }
            case TK_WHILE: { /* stat -> whilestat */
                this.whilestat(line);
                break;
            }
            case TK_SWITCH: { /* stat -> switchstat */
                this.switchstat(line);
                break;
            }
            case TK_DO: { /* stat -> DO block END */
                this.next(); /* skip DO */
                this.block();
                this.check_match(TK_END, TK_DO, line);

                break;
            }
            case TK_FOR: { /* stat -> forstat */
                this.forstat(line);
                break;
            }
            case TK_REPEAT: { /* stat -> repeatstat */
                this.repeatstat(line);
                break;
            }
            case TK_FUNCTION: {
                this.funcstat(line); /* stat -> funcstat */
                break;
            }
            case TK_IMPORT: {
                this.importstat(); /* stat -> deferstat */
                break;
            }
            case TK_MODULE: {
                this.modulestat(); /* stat -> deferstat */
                break;
            }
            case TK_DEFER: {
                this.deferstat(); /* stat -> deferstat */
                break;
            }
            case TK_WHEN: {
                this.whenstat(); /* stat -> whenstat */
                break;
            }
            case TK_TRY: {
                this.trystat(); /* stat -> trystat */
                break;
            }
            case TK_LOCAL: { /* stat -> localstat */
                this.next(); /* skip LOCAL */
                if (this.testnext(TK_FUNCTION)) /* local function? */
                    this.localfunc();
                else
                    this.localstat();
                break;
            }
            case TK_DBCOLON: { /* stat -> label */
                next(); /* skip double colon */
                labelstat(str_checkname(), line);
                break;
            }
            case TK_RETURN: { /* stat -> retstat */
                next();  /* skip RETURN */
                this.retstat();
                break;
            }
            case TK_CONTINUE:
            case TK_BREAK: {  /* stat -> breakstat */
                gotostat(fs.jump());
                if (!block_follow(true))
                    syntaxerror("unreachable statement");
                break;
            }
            case TK_GOTO: { /* stat -> breakstat */
                this.gotostat(fs.jump());
                break;
            }
            default: {
                this.exprstat();
                break;
            }
        }
        //_assert(fs.f.maxstacksize >= fs.freereg
        //		&& fs.freereg >= fs.nactvar);
        if (!(fs.f.maxstacksize >= fs.freereg
                && fs.freereg >= fs.nactvar))
            syntaxerror("statement");
        fs.freereg = fs.nactvar; /* free registers */
        leavelevel();
    }

    void statlist() {
        /* statlist -> { stat [`;'] } */
        while (!block_follow(true)) {
            if (t.token == TK_RETURN) {
                statement();
                return; /* 'return' must be last statement */
            }
            statement();
        }
    }

    /*
     ** compiles the main function, which is a regular vararg function with an
     ** upvalue named LUA_FUNC_ENV
     */
    public static ArrayList<Pair> tokens = new ArrayList<>();
    public static ArrayList<Rect> lines = new ArrayList<>();

    public void mainfunc(FuncState funcstate) {
        BlockCnt bl = new BlockCnt();
        open_func(funcstate, bl);
        fs.f.is_vararg = 1;  /* main function is always vararg */
        expdesc v = new expdesc();
        v.init(VLOCAL, 0);  /* create and... */
        fs.newupvalue(envn, v);  /* ...set environment upvalue */
        if (Lua.LUA_LOCAL_ENV) {
            this.new_localvar(this.envn);
            expdesc env = new expdesc();
            FuncState.singlevaraux(fs, this.envn, env, 1);
            this.adjust_assign(1, 1, env);
            this.adjustlocalvars(1);
        }
        this.new_localvarliteral("_G");
        expdesc g = new expdesc();
        FuncState.singlevaraux(fs, LuaValue.valueOf("_G"), g, 1);
        this.adjust_assign(1, 1, g);
        this.adjustlocalvars(1);
        next();  /* read first token */
        if (testtoken('{') || testtoken(TK_STRING))
            retstat();
        else
            statlist();  /* parse main body */
        check(TK_EOS);
        close_func();
    }

    /* }====================================================================== */

}
