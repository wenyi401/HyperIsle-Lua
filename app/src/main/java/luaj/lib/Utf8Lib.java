package luaj.lib;

import luaj.Buffer;
import luaj.LuaString;
import luaj.LuaTable;
import luaj.LuaUtf8String;
import luaj.LuaValue;
import luaj.Varargs;

import java.util.Arrays;

/**
 * Created by nirenr on 2020/1/4.
 */

public class Utf8Lib extends TwoArgFunction {

    /**
     * Construct a StringLib, which can be initialized by calling it with a
     * modname string, and a global environment table as arguments using
     * {@link #call(LuaValue, LuaValue)}.
     */
    public Utf8Lib() {
    }

    /**
     * Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * Creates a metatable that uses __INDEX to fall back on itself to support string
     * method operations.
     * If the shared strings metatable instance is null, will set the metatable as
     * the global shared metatable for strings.
     * <p>
     * All tables and metatables are read-write by default so if this will be used in
     * a server environment, sandboxing should be used.  In particular, the
     * {@link LuaString#s_metatable} table should probably be made read-only.
     *
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env     the environment to load into, typically a Globals instance.
     */
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable string = new LuaTable();
        string.set("byte", new _byte());
        string.set("char", new _char());
        string.set("find", new find());
        string.set("gmatch", new gmatch());
        string.set("gfind", new gfind());
        string.set("gsub", new gsub());
        string.set("len", new len());
        string.set("lower", new lower());
        string.set("match", new match());
        string.set("rep", new rep());
        string.set("reverse", new reverse());
        string.set("sub", new sub());
        string.set("upper", new upper());

        env.set("utf8", string);
        if (!env.get("package").isnil()) env.get("package").get("loaded").set("utf8", string);
        if (LuaUtf8String.s_metatable == null) {
            LuaUtf8String.s_metatable = LuaValue.tableOf(new LuaValue[]{INDEX, string});
        }
        return string;
    }

    /**
     * string.byte (s [, i [, j]])
     * <p>
     * Returns the internal numerical codes of the
     * characters s[i], s[i+1], ..., s[j]. The default value for i is 1; the
     * default value for j is i.
     * <p>
     * Note that numerical codes are not necessarily portable across platforms.
     *
     * @param args the calling args
     */
    @SuppressWarnings("JavadocReference")
    static final class _byte extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            LuaUtf8String s = LuaUtf8String.valueOfString(args.checkstring(1));
            int l = s.m_length;
            int posi = posrelat(args.optint(2, 1), l);
            int pose = posrelat(args.optint(3, posi), l);
            int n, i;
            if (posi <= 0) posi = 1;
            if (pose > l) pose = l;
            if (posi > pose) return NONE;  /* empty interval; return no values */
            n = (int) (pose - posi + 1);
            if (posi + n <= pose)  /* overflow? */
                error("string slice too long");
            LuaValue[] v = new LuaValue[n];
            for (i = 0; i < n; i++)
                v[i] = valueOf(s.luaByte(posi + i - 1));
            return varargsOf(v);
        }
    }

    /**
     * string.char (...)
     * <p>
     * Receives zero or more integers. Returns a string with length equal
     * to the number of arguments, in which each character has the internal
     * numerical code equal to its corresponding argument.
     * <p>
     * Note that numerical codes are not necessarily portable across platforms.
     *
     * @param args the calling VM
     */
    @SuppressWarnings("JavadocReference")
    static final class _char extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int n = args.narg();
            int[] bytes = new int[n];
            for (int i = 0, a = 1; i < n; i++, a++) {
                int c = args.checkint(a);
                if (c < 0 || c >= Character.MAX_CODE_POINT)
                    argerror(a, "invalid value for string.char [0; 0x10ffff]: " + c);
                bytes[i] = c;
            }
            return LuaUtf8String.valueOf(bytes).tostring();
        }
    }


    /**
     * string.find (s, pattern [, init [, plain]])
     * <p>
     * Looks for the first match of pattern in the string s.
     * If it finds a match, then find returns the indices of s
     * where this occurrence starts and ends; otherwise, it returns nil.
     * A third, optional numerical argument init specifies where to start the search;
     * its default value is 1 and may be negative. A value of true as a fourth,
     * optional argument plain turns off the pattern matching facilities,
     * so the function does a plain "find substring" operation,
     * with no characters in pattern being considered "magic".
     * Note that if plain is given, then init must be given as well.
     * <p>
     * If the pattern has captures, then in a successful match the captured values
     * are also returned, after the two indices.
     */
    static final class find extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return str_find_aux(args, true);
        }
    }

    /**
     * string.gmatch (s, pattern)
     * <p>
     * Returns an iterator function that, each time it is called, returns the next captures
     * from pattern over string s. If pattern specifies no captures, then the
     * whole match is produced in each call.
     * <p>
     * As an example, the following loop
     * s = "hello world from Lua"
     * for w in string.gmatch(s, "%a+") do
     * print(w)
     * end
     * <p>
     * will iterate over all the words from string s, printing one per line.
     * The next example collects all pairs key=value from the given string into a table:
     * t = {}
     * s = "from=world, to=Lua"
     * for k, v in string.gmatch(s, "(%w+)=(%w+)") do
     * t[k] = v
     * end
     * <p>
     * For this function, a '^' at the start of a pattern does not work as an anchor,
     * as this would prevent the iteration.
     */
    static final class gmatch extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            LuaString src = args.checkstring(1);
            LuaString pat = args.checkstring(2);
            return new GMatchAux(args, src, pat, false);
        }
    }

    static final class gfind extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            LuaString src = args.checkstring(1);
            LuaString pat = args.checkstring(2);
            return new GMatchAux(args, src, pat, true);
        }
    }

    static class GMatchAux extends VarArgFunction {
        private final int srclen;
        private final MatchState ms;
        private final boolean find;
        private int soffset;
        private int lastmatch;

        public GMatchAux(Varargs args, LuaString src, LuaString pat, boolean find) {
            this.srclen = src.length();
            this.ms = new MatchState(args, LuaUtf8String.valueOfString(src), LuaUtf8String.valueOfString(pat));
            this.soffset = 0;
            this.lastmatch = -1;
            this.find = find;
        }

        public Varargs invoke(Varargs args) {
            for (; soffset <= srclen; soffset++) {
                ms.reset();
                int res = ms.match(soffset, 0);
                if (res >= 0 && res != lastmatch) {
                    int soff = soffset;
                    lastmatch = soffset = res;
                    if (find)
                        return varargsOf(valueOf(soff + 1), valueOf(res), ms.push_captures(false, soff, res));
                    else
                        return ms.push_captures(true, soff, res);
                }
            }
            return NIL;
        }
    }


    /**
     * string.gsub (s, pattern, repl [, n])
     * Returns a copy of s in which all (or the first n, if given) occurrences of the
     * pattern have been replaced by a replacement string specified by repl, which
     * may be a string, a table, or a function. gsub also returns, as its second value,
     * the total number of matches that occurred.
     * <p>
     * If repl is a string, then its value is used for replacement.
     * The character % works as an escape character: any sequence in repl of the form %n,
     * with n between 1 and 9, stands for the value of the n-th captured substring (see below).
     * The sequence %0 stands for the whole match. The sequence %% stands for a single %.
     * <p>
     * If repl is a table, then the table is queried for every match, using the first capture
     * as the key; if the pattern specifies no captures, then the whole match is used as the key.
     * <p>
     * If repl is a function, then this function is called every time a match occurs,
     * with all captured substrings passed as arguments, in order; if the pattern specifies
     * no captures, then the whole match is passed as a sole argument.
     * <p>
     * If the value returned by the table query or by the function call is a string or a number,
     * then it is used as the replacement string; otherwise, if it is false or nil,
     * then there is no replacement (that is, the original match is kept in the string).
     * <p>
     * Here are some examples:
     * x = string.gsub("hello world", "(%w+)", "%1 %1")
     * --> x="hello hello world world"
     * <p>
     * x = string.gsub("hello world", "%w+", "%0 %0", 1)
     * --> x="hello hello world"
     * <p>
     * x = string.gsub("hello world from Lua", "(%w+)%s*(%w+)", "%2 %1")
     * --> x="world hello Lua from"
     * <p>
     * x = string.gsub("home = $HOME, user = $USER", "%$(%w+)", os.getenv)
     * --> x="home = /home/roberto, user = roberto"
     * <p>
     * x = string.gsub("4+5 = $return 4+5$", "%$(.-)%$", function (s)
     * return loadstring(s)()
     * end)
     * --> x="4+5 = 9"
     * <p>
     * local t = {name="lua", version="5.1"}
     * x = string.gsub("$name-$version.tar.gz", "%$(%w+)", t)
     * --> x="lua-5.1.tar.gz"
     */
    static final class gsub extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            LuaUtf8String src = LuaUtf8String.valueOfString(args.checkstring(1));
            final int srclen = src.length();
            LuaUtf8String p = LuaUtf8String.valueOfString(args.checkstring(2));
            int lastmatch = -1; /* end of last match */
            LuaValue repl = args.arg(3);
            int max_s = args.optint(4, srclen + 1);
            final boolean anchor = p.length() > 0 && p.charAt(0) == '^';

            Buffer lbuf = new Buffer(srclen);
            MatchState ms = new MatchState(args, src, p);

            int soffset = 0;
            int n = 0;
            while (n < max_s) {
                ms.reset();
                int res = ms.match(soffset, anchor ? 1 : 0);
                if (res != -1 && res != lastmatch) {  /* match? */
                    n++;
                    ms.add_value(lbuf, soffset, res, repl);  /* add replacement to buffer */
                    soffset = lastmatch = res;
                } else if (soffset < srclen) /* otherwise, skip one character */
                    lbuf.append((char) src.luaByte(soffset++));
                else break;   /* end of subject */
                if (anchor) break;
            }
            lbuf.append(src.substring(soffset, srclen));
            return varargsOf(lbuf.tostring(), valueOf(n));
        }
    }

    /**
     * string.len (s)
     * <p>
     * Receives a string and returns its length. The empty string "" has length 0.
     * Embedded zeros are counted, so "a\000bc\000" has length 5.
     */
    static final class len extends OneArgFunction {
        public LuaValue call(LuaValue arg) {
            return LuaUtf8String.valueOfString(arg.checkstring()).len();
        }
    }

    /**
     * string.lower (s)
     * <p>
     * Receives a string and returns a copy of this string with all uppercase letters
     * changed to lowercase. All other characters are left unchanged.
     * The definition of what an uppercase letter is depends on the current locale.
     */
    static final class lower extends OneArgFunction {
        public LuaValue call(LuaValue arg) {
            return valueOf(arg.checkjstring().toLowerCase());
        }
    }

    /**
     * string.match (s, pattern [, init])
     * <p>
     * Looks for the first match of pattern in the string s. If it finds one,
     * then match returns the captures from the pattern; otherwise it returns
     * nil. If pattern specifies no captures, then the whole match is returned.
     * A third, optional numerical argument init specifies where to start the
     * search; its default value is 1 and may be negative.
     */
    static final class match extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return str_find_aux(args, false);
        }
    }

    /**
     * string.rep (s, n)
     * <p>
     * Returns a string that is the concatenation of n copies of the string s.
     */
    static final class rep extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            LuaString s = args.checkstring(1);
            int n = args.checkint(2);
            final byte[] bytes = new byte[s.length() * n];
            int len = s.length();
            for (int offset = 0; offset < bytes.length; offset += len) {
                s.copyInto(0, bytes, offset, len);
            }
            return LuaString.valueUsing(bytes);
        }
    }

    /**
     * string.reverse (s)
     * <p>
     * Returns a string that is the string s reversed.
     */
    static final class reverse extends OneArgFunction {
        public LuaValue call(LuaValue arg) {
            LuaUtf8String s = LuaUtf8String.valueOfString(arg.checkstring());
            int n = s.length();
            int[] b = new int[n];
            for (int i = 0, j = n - 1; i < n; i++, j--)
                b[j] = s.luaByte(i);
            return LuaUtf8String.valueOf(b).tostring();
        }
    }

    /**
     * string.sub (s, i [, j])
     * <p>
     * Returns the substring of s that starts at i and continues until j;
     * i and j may be negative. If j is absent, then it is assumed to be equal to -1
     * (which is the same as the string length). In particular, the call
     * string.sub(s,1,j)
     * returns a prefix of s with length j, and
     * string.sub(s, -i)
     * returns a suffix of s with length i.
     */
    static final class sub extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            final LuaUtf8String s = LuaUtf8String.valueOfString(args.checkstring(1));
            final int l = s.length();

            int start = posrelat(args.checkint(2), l);
            int end = posrelat(args.optint(3, -1), l);

            if (start < 1)
                start = 1;
            if (end > l)
                end = l;

            if (start <= end) {
                return s.substring(start - 1, end).tostring();
            } else {
                return EMPTYSTRING;
            }
        }
    }

    /**
     * string.upper (s)
     * <p>
     * Receives a string and returns a copy of this string with all lowercase letters
     * changed to uppercase. All other characters are left unchanged.
     * The definition of what a lowercase letter is depends on the current locale.
     */
    static final class upper extends OneArgFunction {
        public LuaValue call(LuaValue arg) {
            return valueOf(arg.checkjstring().toUpperCase());
        }
    }

    /**
     * This utility method implements both string.find and string.match.
     */
    static Varargs str_find_aux(Varargs args, boolean find) {
        LuaUtf8String s = LuaUtf8String.valueOfString(args.checkstring(1));
        LuaUtf8String pat = LuaUtf8String.valueOfString(args.checkstring(2));
        int init = args.optint(3, 1);

        if (init > 0) {
            init = Math.min(init - 1, s.length());
        } else if (init < 0) {
            init = Math.max(0, s.length() + init);
        }

        boolean fastMatch = find && (args.arg(4).toboolean() || pat.indexOfAny(SPECIALS) == -1);

        if (fastMatch) {
            int result = s.indexOf(pat, init);
            if (result != -1) {
                return varargsOf(valueOf(result + 1), valueOf(result + pat.length()));
            }
        } else {
            MatchState ms = new MatchState(args, s, pat);

            boolean anchor = false;
            int poff = 0;
            if (pat.length() > 0 && pat.luaByte(0) == '^') {
                anchor = true;
                poff = 1;
            }

            int soff = init;
            do {
                int res;
                ms.reset();
                if ((res = ms.match(soff, poff)) != -1) {
                    if (find) {
                        return varargsOf(valueOf(soff + 1), valueOf(res), ms.push_captures(false, soff, res));
                    } else {
                        return ms.push_captures(true, soff, res);
                    }
                }
            } while (soff++ < s.length() && !anchor);
        }
        return NIL;
    }

    static int posrelat(int pos, int len) {
        return (pos >= 0) ? pos : len + pos + 1;
    }

    // Pattern matching implementation

    private static final int L_ESC = '%';
    private static final LuaUtf8String SPECIALS = LuaUtf8String.valueOfString("^$*+?.([%-");
    private static final int MAX_CAPTURES = 32;

    private static final int MAXCCALLS = 200;

    private static final int CAP_UNFINISHED = -1;
    private static final int CAP_POSITION = -2;

    private static final byte MASK_ALPHA = 0x01;
    private static final byte MASK_LOWERCASE = 0x02;
    private static final byte MASK_UPPERCASE = 0x04;
    private static final byte MASK_DIGIT = 0x08;
    private static final byte MASK_PUNCT = 0x10;
    private static final byte MASK_SPACE = 0x20;
    private static final byte MASK_CONTROL = 0x40;
    private static final byte MASK_HEXDIGIT = (byte) 0x80;

    static final byte[] CHAR_TABLE;
    static final int[] EMOJI_TABLE = new int[]{8211, 8212, 8224, 8225, 8252, 8265, 8315, 8364, 8377, 8381, 8471, 8482, 8505, 8592, 8593, 8594, 8595, 8596, 8597, 8598, 8599, 8600, 8601, 8617, 8618, 8645, 8646, 8710, 8711, 8712, 8730, 8734, 8745, 8746, 8801, 8834, 8986, 8987, 9000, 9167, 9193, 9194, 9195, 9196, 9197, 9198, 9199, 9200, 9201, 9202, 9203, 9208, 9209, 9210, 9410, 9642, 9643, 9650, 9654, 9660, 9664, 9674, 9675, 9679, 9711, 9723, 9724, 9725, 9726, 9728, 9729, 9730, 9731, 9732, 9742, 9745, 9748, 9749, 9752, 9757, 9760, 9762, 9763, 9766, 9770, 9774, 9775, 9784, 9785, 9786, 9792, 9794, 9800, 9801, 9802, 9803, 9804, 9805, 9806, 9807, 9808, 9809, 9810, 9811, 9823, 9824, 9827, 9829, 9830, 9832, 9851, 9854, 9855, 9874, 9875, 9876, 9877, 9878, 9879, 9881, 9883, 9884, 9888, 9889, 9895, 9898, 9899, 9904, 9905, 9917, 9918, 9924, 9925, 9928, 9934, 9935, 9937, 9939, 9940, 9961, 9962, 9968, 9969, 9970, 9971, 9972, 9973, 9975, 9976, 9977, 9978, 9981, 9986, 9989, 9992, 9993, 9994, 9995, 9996, 9997, 9999, 10002, 10004, 10006, 10013, 10017, 10024, 10035, 10036, 10052, 10055, 10060, 10062, 10067, 10068, 10069, 10071, 10083, 10084, 10133, 10134, 10135, 10145, 10160, 10175, 10548, 10549, 11013, 11014, 11015, 11035, 11036, 11088, 11093, 12336, 12349, 12951, 12953, 126980, 127183, 127344, 127345, 127358, 127359, 127374, 127377, 127378, 127379, 127380, 127381, 127382, 127383, 127384, 127385, 127386, 127489, 127490, 127514, 127535, 127538, 127539, 127540, 127541, 127542, 127543, 127544, 127545, 127546, 127568, 127569, 127744, 127745, 127746, 127747, 127748, 127749, 127750, 127751, 127752, 127753, 127754, 127755, 127756, 127757, 127758, 127759, 127760, 127761, 127762, 127763, 127764, 127765, 127766, 127767, 127768, 127769, 127770, 127771, 127772, 127773, 127774, 127775, 127776, 127777, 127780, 127781, 127782, 127783, 127784, 127785, 127786, 127787, 127788, 127789, 127790, 127791, 127792, 127793, 127794, 127795, 127796, 127797, 127798, 127799, 127800, 127801, 127802, 127803, 127804, 127805, 127806, 127807, 127808, 127809, 127810, 127811, 127812, 127813, 127814, 127815, 127816, 127817, 127818, 127819, 127820, 127821, 127822, 127823, 127824, 127825, 127826, 127827, 127828, 127829, 127830, 127831, 127832, 127833, 127834, 127835, 127836, 127837, 127838, 127839, 127840, 127841, 127842, 127843, 127844, 127845, 127846, 127847, 127848, 127849, 127850, 127851, 127852, 127853, 127854, 127855, 127856, 127857, 127858, 127859, 127860, 127861, 127862, 127863, 127864, 127865, 127866, 127867, 127868, 127869, 127870, 127871, 127872, 127873, 127874, 127875, 127876, 127877, 127878, 127879, 127880, 127881, 127882, 127883, 127884, 127885, 127886, 127887, 127888, 127889, 127890, 127891, 127894, 127895, 127897, 127898, 127899, 127902, 127903, 127904, 127905, 127906, 127907, 127908, 127909, 127910, 127911, 127912, 127913, 127914, 127915, 127916, 127917, 127918, 127919, 127920, 127921, 127922, 127923, 127924, 127925, 127926, 127927, 127928, 127929, 127930, 127931, 127932, 127933, 127934, 127935, 127936, 127937, 127938, 127939, 127940, 127941, 127942, 127943, 127944, 127945, 127946, 127947, 127948, 127949, 127950, 127951, 127952, 127953, 127954, 127955, 127956, 127957, 127958, 127959, 127960, 127961, 127962, 127963, 127964, 127965, 127966, 127967, 127968, 127969, 127970, 127971, 127972, 127973, 127974, 127975, 127976, 127977, 127978, 127979, 127980, 127981, 127982, 127983, 127984, 127987, 127988, 127989, 127991, 127992, 127993, 127994, 127995, 127996, 127997, 127998, 127999, 128000, 128001, 128002, 128003, 128004, 128005, 128006, 128007, 128008, 128009, 128010, 128011, 128012, 128013, 128014, 128015, 128016, 128017, 128018, 128019, 128020, 128021, 128022, 128023, 128024, 128025, 128026, 128027, 128028, 128029, 128030, 128031, 128032, 128033, 128034, 128035, 128036, 128037, 128038, 128039, 128040, 128041, 128042, 128043, 128044, 128045, 128046, 128047, 128048, 128049, 128050, 128051, 128052, 128053, 128054, 128055, 128056, 128057, 128058, 128059, 128060, 128061, 128062, 128063, 128064, 128065, 128066, 128067, 128068, 128069, 128070, 128071, 128072, 128073, 128074, 128075, 128076, 128077, 128078, 128079, 128080, 128081, 128082, 128083, 128084, 128085, 128086, 128087, 128088, 128089, 128090, 128091, 128092, 128093, 128094, 128095, 128096, 128097, 128098, 128099, 128100, 128101, 128102, 128103, 128104, 128105, 128106, 128107, 128108, 128109, 128110, 128111, 128112, 128113, 128114, 128115, 128116, 128117, 128118, 128119, 128120, 128121, 128122, 128123, 128124, 128125, 128126, 128127, 128128, 128129, 128130, 128131, 128132, 128133, 128134, 128135, 128136, 128137, 128138, 128139, 128140, 128141, 128142, 128143, 128144, 128145, 128146, 128147, 128148, 128149, 128150, 128151, 128152, 128153, 128154, 128155, 128156, 128157, 128158, 128159, 128160, 128161, 128162, 128163, 128164, 128165, 128166, 128167, 128168, 128169, 128170, 128171, 128172, 128173, 128174, 128175, 128176, 128177, 128178, 128179, 128180, 128181, 128182, 128183, 128184, 128185, 128186, 128187, 128188, 128189, 128190, 128191, 128192, 128193, 128194, 128195, 128196, 128197, 128198, 128199, 128200, 128201, 128202, 128203, 128204, 128205, 128206, 128207, 128208, 128209, 128210, 128211, 128212, 128213, 128214, 128215, 128216, 128217, 128218, 128219, 128220, 128221, 128222, 128223, 128224, 128225, 128226, 128227, 128228, 128229, 128230, 128231, 128232, 128233, 128234, 128235, 128236, 128237, 128238, 128239, 128240, 128241, 128242, 128243, 128244, 128245, 128246, 128247, 128248, 128249, 128250, 128251, 128252, 128253, 128255, 128256, 128257, 128258, 128259, 128260, 128261, 128262, 128263, 128264, 128265, 128266, 128267, 128268, 128269, 128270, 128271, 128272, 128273, 128274, 128275, 128276, 128277, 128278, 128279, 128280, 128281, 128282, 128283, 128284, 128285, 128286, 128287, 128288, 128289, 128290, 128291, 128292, 128293, 128294, 128295, 128296, 128297, 128298, 128299, 128300, 128301, 128302, 128303, 128304, 128305, 128306, 128307, 128308, 128309, 128310, 128311, 128312, 128313, 128314, 128315, 128316, 128317, 128329, 128330, 128331, 128332, 128333, 128334, 128336, 128337, 128338, 128339, 128340, 128341, 128342, 128343, 128344, 128345, 128346, 128347, 128348, 128349, 128350, 128351, 128352, 128353, 128354, 128355, 128356, 128357, 128358, 128359, 128367, 128368, 128371, 128372, 128373, 128374, 128375, 128376, 128377, 128378, 128391, 128394, 128395, 128396, 128397, 128400, 128405, 128406, 128420, 128421, 128424, 128433, 128434, 128444, 128450, 128451, 128452, 128465, 128466, 128467, 128476, 128477, 128478, 128481, 128483, 128488, 128495, 128499, 128506, 128507, 128508, 128509, 128510, 128511, 128512, 128513, 128514, 128515, 128516, 128517, 128518, 128519, 128520, 128521, 128522, 128523, 128524, 128525, 128526, 128527, 128528, 128529, 128530, 128531, 128532, 128533, 128534, 128535, 128536, 128537, 128538, 128539, 128540, 128541, 128542, 128543, 128544, 128545, 128546, 128547, 128548, 128549, 128550, 128551, 128552, 128553, 128554, 128555, 128556, 128557, 128558, 128559, 128560, 128561, 128562, 128563, 128564, 128565, 128566, 128567, 128568, 128569, 128570, 128571, 128572, 128573, 128574, 128575, 128576, 128577, 128578, 128579, 128580, 128581, 128582, 128583, 128584, 128585, 128586, 128587, 128588, 128589, 128590, 128591, 128640, 128641, 128642, 128643, 128644, 128645, 128646, 128647, 128648, 128649, 128650, 128651, 128652, 128653, 128654, 128655, 128656, 128657, 128658, 128659, 128660, 128661, 128662, 128663, 128664, 128665, 128666, 128667, 128668, 128669, 128670, 128671, 128672, 128673, 128674, 128675, 128676, 128677, 128678, 128679, 128680, 128681, 128682, 128683, 128684, 128685, 128686, 128687, 128688, 128689, 128690, 128691, 128692, 128693, 128694, 128695, 128696, 128697, 128698, 128699, 128700, 128701, 128702, 128703, 128704, 128705, 128706, 128707, 128708, 128709, 128715, 128716, 128717, 128718, 128719, 128720, 128721, 128722, 128725, 128726, 128727, 128736, 128737, 128738, 128739, 128740, 128741, 128745, 128747, 128748, 128752, 128755, 128756, 128757, 128758, 128759, 128760, 128761, 128762, 128763, 128764, 128992, 128993, 128994, 128995, 128996, 128997, 128998, 128999, 129000, 129001, 129002, 129003, 129292, 129293, 129294, 129295, 129296, 129297, 129298, 129299, 129300, 129301, 129302, 129303, 129304, 129305, 129306, 129307, 129308, 129309, 129310, 129311, 129312, 129313, 129314, 129315, 129316, 129317, 129318, 129319, 129320, 129321, 129322, 129323, 129324, 129325, 129326, 129327, 129328, 129329, 129330, 129331, 129332, 129333, 129334, 129335, 129336, 129337, 129338, 129340, 129341, 129342, 129343, 129344, 129345, 129346, 129347, 129348, 129349, 129351, 129352, 129353, 129354, 129355, 129356, 129357, 129358, 129359, 129360, 129361, 129362, 129363, 129364, 129365, 129366, 129367, 129368, 129369, 129370, 129371, 129372, 129373, 129374, 129375, 129376, 129377, 129378, 129379, 129380, 129381, 129382, 129383, 129384, 129385, 129386, 129387, 129388, 129389, 129390, 129391, 129392, 129393, 129394, 129395, 129396, 129397, 129398, 129399, 129400, 129402, 129403, 129404, 129405, 129406, 129407, 129408, 129409, 129410, 129411, 129412, 129413, 129414, 129415, 129416, 129417, 129418, 129419, 129420, 129421, 129422, 129423, 129424, 129425, 129426, 129427, 129428, 129429, 129430, 129431, 129432, 129433, 129434, 129435, 129436, 129437, 129438, 129439, 129440, 129441, 129442, 129443, 129444, 129445, 129446, 129447, 129448, 129449, 129450, 129451, 129452, 129453, 129454, 129455, 129456, 129457, 129458, 129459, 129460, 129461, 129462, 129463, 129464, 129465, 129466, 129467, 129468, 129469, 129470, 129471, 129472, 129473, 129474, 129475, 129476, 129477, 129478, 129479, 129480, 129481, 129482, 129483, 129485, 129486, 129487, 129488, 129489, 129490, 129491, 129492, 129493, 129494, 129495, 129496, 129497, 129498, 129499, 129500, 129501, 129502, 129503, 129504, 129505, 129506, 129507, 129508, 129509, 129510, 129511, 129512, 129513, 129514, 129515, 129516, 129517, 129518, 129519, 129520, 129521, 129522, 129523, 129524, 129525, 129526, 129527, 129528, 129529, 129530, 129531, 129532, 129533, 129534, 129535, 129648, 129649, 129650, 129651, 129652, 129656, 129657, 129658, 129664, 129665, 129666, 129667, 129668, 129669, 129670, 129680, 129681, 129682, 129683, 129684, 129685, 129686, 129687, 129688, 129689, 129690, 129691, 129692, 129693, 129694, 129695, 129696, 129697, 129699, 129700, 129701, 129702, 129703, 129704, 129712, 129713, 129714, 129715, 129716, 129717, 129718, 129728, 129729, 129730, 129744, 129745, 129746, 129747, 129748, 129749, 129750};

    static {
        CHAR_TABLE = new byte[256];
        for (int i = 0; i < 128; ++i) {
            final char c = (char) i;
            CHAR_TABLE[i] = (byte) ((Character.isDigit(c) ? MASK_DIGIT : 0) |
                    (Character.isLowerCase(c) ? MASK_LOWERCASE : 0) |
                    (Character.isUpperCase(c) ? MASK_UPPERCASE : 0) |
                    ((c < ' ' || c == 0x7F) ? MASK_CONTROL : 0));
            if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9')) {
                CHAR_TABLE[i] |= MASK_HEXDIGIT;
            }
            if ((c >= '!' && c <= '/') || (c >= ':' && c <= '@') || (c >= '[' && c <= '`') || (c >= '{' && c <= '~')) {
                CHAR_TABLE[i] |= MASK_PUNCT;
            }
            if ((CHAR_TABLE[i] & (MASK_LOWERCASE | MASK_UPPERCASE)) != 0) {
                CHAR_TABLE[i] |= MASK_ALPHA;
            }
        }

        CHAR_TABLE[' '] = MASK_SPACE;
        CHAR_TABLE['\r'] |= MASK_SPACE;
        CHAR_TABLE['\n'] |= MASK_SPACE;
        CHAR_TABLE['\t'] |= MASK_SPACE;
        CHAR_TABLE[0x0B /* '\v' */] |= MASK_SPACE;
        CHAR_TABLE['\f'] |= MASK_SPACE;
    }

    ;

    static class MatchState {
        int matchdepth;  /* control for recursive depth (to avoid C stack overflow) */
        final LuaUtf8String s;
        final LuaUtf8String p;
        final Varargs args;
        int level;
        int[] cinit;
        int[] clen;

        MatchState(Varargs args, LuaUtf8String s, LuaUtf8String pattern) {
            this.s = s;
            this.p = pattern;
            this.args = args;
            this.level = 0;
            this.cinit = new int[MAX_CAPTURES];
            this.clen = new int[MAX_CAPTURES];
            this.matchdepth = MAXCCALLS;
        }

        void reset() {
            level = 0;
            this.matchdepth = MAXCCALLS;
        }

        private void add_s(Buffer lbuf, LuaString ns, int soff, int e) {
            LuaString news = ns;
            int l = news.length();
            for (int i = 0; i < l; ++i) {
                byte b = (byte) news.luaByte(i);
                if (b != L_ESC) {
                    lbuf.append((byte) b);
                } else {
                    ++i; // skip ESC
                    b = (byte) (i < l ? news.luaByte(i) : 0);
                    if (!Character.isDigit((char) b)) {
                        if (b != L_ESC) error("invalid use of '" + (char) L_ESC +
                                "' in replacement string: after '" + (char) L_ESC +
                                "' must be '0'-'9' or '" + (char) L_ESC +
                                "', but found " + (i < l ? "symbol '" + (char) b + "' with code " + b +
                                " at pos " + (i + 1) :
                                "end of string"));
                        lbuf.append(b);
                    } else if (b == '0') {
                        lbuf.append(s.substring(soff, e));
                    } else {
                        lbuf.append(push_onecapture(b - '1', soff, e).strvalue());
                    }
                }
            }
        }

        public void add_value(Buffer lbuf, int soffset, int end, LuaValue repl) {
            switch (repl.type()) {
                case LuaValue.TSTRING:
                case LuaValue.TNUMBER:
                    add_s(lbuf, repl.strvalue(), soffset, end);
                    return;

                case LuaValue.TFUNCTION:
                    repl = repl.invoke(push_captures(true, soffset, end)).arg1();
                    break;

                case LuaValue.TTABLE:
                    // Need to call push_onecapture here for the error checking
                    repl = repl.get(push_onecapture(0, soffset, end));
                    break;

                default:
                    error("bad argument: string/function/table expected");
                    return;
            }
            if (!repl.toboolean()) {
                repl = s.substring(soffset, end);
            } else if (!repl.isstring()) {
                error("invalid replacement value (a " + repl.typename() + ")");
            }
            lbuf.append(repl.strvalue());
        }

        Varargs push_captures(boolean wholeMatch, int soff, int end) {
            int nlevels = (this.level == 0 && wholeMatch) ? 1 : this.level;
            switch (nlevels) {
                case 0:
                    return NONE;
                case 1:
                    return push_onecapture(0, soff, end);
            }
            LuaValue[] v = new LuaValue[nlevels];
            for (int i = 0; i < nlevels; ++i)
                v[i] = push_onecapture(i, soff, end);
            return varargsOf(v);
        }

        private LuaValue push_onecapture(int i, int soff, int end) {
            if (i >= this.level) {
                if (i == 0) {
                    return s.substring(soff, end);
                } else {
                    return error("invalid capture index %" + (i + 1));
                }
            } else {
                int l = clen[i];
                if (l == CAP_UNFINISHED) {
                    return error("unfinished capture");
                }
                if (l == CAP_POSITION) {
                    return valueOf(cinit[i] + 1);
                } else {
                    int begin = cinit[i];
                    return s.substring(begin, begin + l);
                }
            }
        }

        private int check_capture(int l) {
            l -= '1';
            if (l < 0 || l >= level || this.clen[l] == CAP_UNFINISHED) {
                error("invalid capture index %" + (l + 1));
            }
            return l;
        }

        private int capture_to_close() {
            int level = this.level;
            for (level--; level >= 0; level--)
                if (clen[level] == CAP_UNFINISHED)
                    return level;
            error("invalid pattern capture");
            return 0;
        }

        int classend(int poffset) {
            switch (p.luaByte(poffset++)) {
                case L_ESC:
                    if (poffset == p.length()) {
                        error("malformed pattern (ends with '%')");
                    }
                    return poffset + 1;

                case '[':
                    if (poffset != p.length() && p.luaByte(poffset) == '^') poffset++;
                    do {
                        if (poffset == p.length()) {
                            error("malformed pattern (missing ']')");
                        }
                        if (p.luaByte(poffset++) == L_ESC && poffset < p.length())
                            poffset++; /* skip escapes (e.g. '%]') */
                    } while (poffset == p.length() || p.luaByte(poffset) != ']');
                    return poffset + 1;
                default:
                    return poffset;
            }
        }

        static boolean match_class(int c, int cl) {
            final char lcl = Character.toLowerCase((char) cl);
            int cdata = c > 255 ? c : CHAR_TABLE[c];

            boolean res;
            switch (lcl) {
                case 'a':
                    res = (cdata & MASK_ALPHA) != 0;
                    break;
                case 'd':
                    res = (cdata & MASK_DIGIT) != 0;
                    break;
                case 'l':
                    res = (cdata & MASK_LOWERCASE) != 0;
                    break;
                case 'u':
                    res = (cdata & MASK_UPPERCASE) != 0;
                    break;
                case 'c':
                    res = (cdata & MASK_CONTROL) != 0;
                    break;
                case 'p':
                    res = (cdata & MASK_PUNCT) != 0;
                    break;
                case 's':
                    res = (cdata & MASK_SPACE) != 0;
                    break;
                case 'g':
                    res = (cdata & (MASK_ALPHA | MASK_DIGIT | MASK_PUNCT)) != 0;
                    break;
                case 'w':
                    res = (cdata & (MASK_ALPHA | MASK_DIGIT)) != 0;
                    break;
                case 'x':
                    res = (cdata & MASK_HEXDIGIT) != 0;
                    break;
                case 'z':
                    res = (c == 0);
                    break;  /* deprecated option */
                case 'h':
                    res = (c >= 0x4E00 && c <= 0x9FA5)//基本汉字 20902字 4E00-9FA5
                            || (c >= 0x9FA6 && c <= 0x9FEF)//基本汉字补充 74字 9FA6-9FEF
                            || (c >= 0x3400 && c <= 0x4DB5)//扩展A 6582字 3400-4DB5
                            || (c >= 0x20000 && c <= 0x2A6D6)//扩展B 42711字 20000-
                            || (c >= 0x2A700 && c <= 0x2B734)//扩展C 4149字 2A700-2B734
                            || (c >= 0x2B740 && c <= 0x2B81D)//扩展D 222字 2B740-2B81D
                            || (c >= 0x2B820 && c <= 0x2CEA1)//扩展E 5762字 2B820-2CEA1
                            || (c >= 0x2CEB0 && c <= 0x2EBE0)//扩展F 7473字 2CEB0-2EBE0
                            || (c >= 0x30000 && c <= 0x3134A)//扩展G 4939字 30000-3134A
                            || (c >= 0x2F00 && c <= 0x2FD5)//康熙部首 214字 2F00-2FD5
                            || (c >= 0x2E80 && c <= 0x2EF3)//部首扩展 115字 2E80-2EF3
                            || (c >= 0xF900 && c <= 0xFAD9)//兼容汉字 477字 F900-FAD9
                            || (c >= 0x2F800 && c <= 0x2FA1D)//兼容扩展 542字 2F800-2FA1D
                            || (c >= 0xE400 && c <= 0xE5E8)//部件扩展 452字 E400-E5E8
                            || (c >= 0x31C0 && c <= 0x31E3)//汉字笔画 36字 31C0-31E3
                            || (c >= 0x2FF0 && c <= 0x2FFB)//汉字结构 12字 2FF0-2FFB
                            || (c >= 0x3105 && c <= 0x312F)//汉语注音 43字 3105-312F
                            || (c >= 0x31A0 && c <= 0x31BA)//注音扩展 22字 31A0-31BA
                            || (c == 0x3007)//〇 1字 3007
                    ;
                    break;
                case 'e':
                    return Arrays.binarySearch(EMOJI_TABLE, c) > -1;
                default:
                    return cl == c;
            }
            return (lcl == cl) ? res : !res;
        }

        boolean matchbracketclass(int c, int poff, int ec) {
            boolean sig = true;
            if (p.luaByte(poff + 1) == '^') {
                sig = false;
                poff++;
            }
            while (++poff < ec) {
                if (p.luaByte(poff) == L_ESC) {
                    poff++;
                    if (match_class(c, p.luaByte(poff)))
                        return sig;
                } else if ((p.luaByte(poff + 1) == '-') && (poff + 2 < ec)) {
                    poff += 2;
                    if (p.luaByte(poff - 2) <= c && c <= p.luaByte(poff))
                        return sig;
                } else if (p.luaByte(poff) == c) return sig;
            }
            return !sig;
        }

        boolean singlematch(int c, int poff, int ep) {
            switch (p.luaByte(poff)) {
                case '.':
                    return true;
                case L_ESC:
                    return match_class(c, p.luaByte(poff + 1));
                case '[':
                    return matchbracketclass(c, poff, ep - 1);
                default:
                    return p.luaByte(poff) == c;
            }
        }

        /**
         * Perform pattern matching. If there is a match, returns offset into s
         * where match ends, otherwise returns -1.
         */
        int match(int soffset, int poffset) {
            if (matchdepth-- == 0) error("pattern too complex");
            try {
                while (true) {
                    // Check if we are at the end of the pattern -
                    // equivalent to the '\0' case in the C version, but our pattern
                    // string is not NUL-terminated.
                    if (poffset == p.length())
                        return soffset;
                    switch (p.luaByte(poffset)) {
                        case '(':
                            if (++poffset < p.length() && p.luaByte(poffset) == ')')
                                return start_capture(soffset, poffset + 1, CAP_POSITION);
                            else
                                return start_capture(soffset, poffset, CAP_UNFINISHED);
                        case ')':
                            return end_capture(soffset, poffset + 1);
                        case L_ESC:
                            if (poffset + 1 == p.length())
                                error("malformed pattern (ends with '%')");
                            switch (p.luaByte(poffset + 1)) {
                                case 'b':
                                    soffset = matchbalance(soffset, poffset + 2);
                                    if (soffset == -1) return -1;
                                    poffset += 4;
                                    continue;
                                case 'f': {
                                    poffset += 2;
                                    if (poffset == p.length() || p.luaByte(poffset) != '[') {
                                        error("missing '[' after '%f' in pattern");
                                    }
                                    int ep = classend(poffset);
                                    int previous = (soffset == 0) ? '\0' : s.luaByte(soffset - 1);
                                    int next = (soffset == s.length()) ? '\0' : s.luaByte(soffset);
                                    if (matchbracketclass(previous, poffset, ep - 1) ||
                                            !matchbracketclass(next, poffset, ep - 1))
                                        return -1;
                                    poffset = ep;
                                    continue;
                                }
                                default: {
                                    int c = p.luaByte(poffset + 1);
                                    if (Character.isDigit((char) c)) {
                                        soffset = match_capture(soffset, c);
                                        if (soffset == -1)
                                            return -1;
                                        return match(soffset, poffset + 2);
                                    }
                                }
                            }
                        case '$':
                            if (poffset + 1 == p.length())
                                return (soffset == s.length()) ? soffset : -1;
                    }
                    int ep = classend(poffset);
                    boolean m = soffset < s.length() && singlematch(s.luaByte(soffset), poffset, ep);
                    int pc = (ep < p.length()) ? p.luaByte(ep) : '\0';

                    switch (pc) {
                        case '?':
                            int res;
                            if (m && ((res = match(soffset + 1, ep + 1)) != -1))
                                return res;
                            poffset = ep + 1;
                            continue;
                        case '*':
                            return max_expand(soffset, poffset, ep);
                        case '+':
                            return (m ? max_expand(soffset + 1, poffset, ep) : -1);
                        case '-':
                            return min_expand(soffset, poffset, ep);
                        default:
                            if (!m)
                                return -1;
                            soffset++;
                            poffset = ep;
                            continue;
                    }
                }
            } finally {
                matchdepth++;
            }
        }

        int max_expand(int soff, int poff, int ep) {
            int i = 0;
            while (soff + i < s.length() &&
                    singlematch(s.luaByte(soff + i), poff, ep))
                i++;
            while (i >= 0) {
                int res = match(soff + i, ep + 1);
                if (res != -1)
                    return res;
                i--;
            }
            return -1;
        }

        int min_expand(int soff, int poff, int ep) {
            for (; ; ) {
                int res = match(soff, ep + 1);
                if (res != -1)
                    return res;
                else if (soff < s.length() && singlematch(s.luaByte(soff), poff, ep))
                    soff++;
                else return -1;
            }
        }

        int start_capture(int soff, int poff, int what) {
            int res;
            int level = this.level;
            if (level >= MAX_CAPTURES) {
                error("too many captures");
            }
            cinit[level] = soff;
            clen[level] = what;
            this.level = level + 1;
            if ((res = match(soff, poff)) == -1)
                this.level--;
            return res;
        }

        int end_capture(int soff, int poff) {
            int l = capture_to_close();
            int res;
            clen[l] = soff - cinit[l];
            if ((res = match(soff, poff)) == -1)
                clen[l] = CAP_UNFINISHED;
            return res;
        }

        int match_capture(int soff, int l) {
            l = check_capture(l);
            int len = clen[l];
            if ((s.length() - soff) >= len &&
                    LuaUtf8String.equals(s, cinit[l], s, soff, len))
                return soff + len;
            else
                return -1;
        }

        int matchbalance(int soff, int poff) {
            final int plen = p.length();
            if (poff == plen || poff + 1 == plen) {
                error("malformed pattern (missing arguments to '%b')");
            }
            final int slen = s.length();
            if (soff >= slen)
                return -1;
            final int b = p.luaByte(poff);
            if (s.luaByte(soff) != b)
                return -1;
            final int e = p.luaByte(poff + 1);
            int cont = 1;
            while (++soff < slen) {
                if (s.luaByte(soff) == e) {
                    if (--cont == 0) return soff + 1;
                } else if (s.luaByte(soff) == b) cont++;
            }
            return -1;
        }
    }
}
