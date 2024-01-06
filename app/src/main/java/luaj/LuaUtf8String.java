package luaj;

import luaj.lib.MathLib;

import java.io.PrintStream;

/**
 * Created by nirenr on 2020/1/4.
 */

public class LuaUtf8String extends LuaValue {

    /**
     * The singleton instance for string metatables that forwards to the string functions.
     * Typically, this is set to the string metatable as a side effect of loading the string
     * library, and is read-write to provide flexible behavior by default.  When used in a
     * server environment where there may be roge scripts, this should be replaced with a
     * read-only table since it is shared across all lua code in this Java VM.
     */
    public static LuaValue s_metatable;

    /**
     * The bytes for the string.  These <em><b>must not be mutated directly</b></em> because
     * the backing may be shared by multiple LuaUtf8Strings, and the hash code is
     * computed only at construction time.
     * It is exposed only for performance and legacy reasons.
     */
    public final int[] m_bytes;

    /**
     * The offset into the byte array, 0 means start at the first byte
     */
    public final int m_offset;

    /**
     * The number of bytes that comprise this string
     */
    public final int m_length;

    /**
     * The hashcode for this string.  Computed at construct time.
     */
    private int m_hashcode;

    /**
     * Size of cache of recent short strings. This is the maximum number of LuaUtf8Strings that
     * will be retained in the cache of recent short strings.  Exposed to package for testing.
     */
    static final int RECENT_STRINGS_CACHE_SIZE = 128;

    /**
     * Maximum length of a string to be considered for recent short strings caching.
     * This effectively limits the total memory that can be spent on the recent strings cache,
     * because no LuaUtf8String whose backing exceeds this length will be put into the cache.
     * Exposed to package for testing.
     */
    static final int RECENT_STRINGS_MAX_LENGTH = 32;
    private boolean isHascode;
    private String m_string;
    private LuaString m_lua_string;

    /**
     * Get a {@link LuaUtf8String} instance whose bytes match
     * the supplied Java String using the UTF8 encoding.
     *
     * @param string Java String containing characters to encode as UTF8
     * @return {@link LuaUtf8String} with UTF8 bytes corresponding to the supplied String
     */
    public static LuaUtf8String valueOfString(String string) {
        int len = string.codePointCount(0, string.length());
        int[] cs = new int[len];
        int n = 0;
        for (int i = 0; i < len; i++) {
            cs[i] = string.codePointAt(n);
            n += Character.charCount(cs[i]);
        }
        return valueUsing(cs, 0, cs.length);
    }

    public static LuaUtf8String valueOfString(LuaString string) {
        return valueOfString(string.tojstring());
    }

    /**
     * Construct a new LuaUtf8String using a copy of the bytes array supplied
     */
    private static LuaUtf8String valueFromCopy(int[] bytes, int off, int len) {
        final int[] copy = new int[len];
        System.arraycopy(bytes, off, copy, 0, len);
        return new LuaUtf8String(copy, 0, len);
    }

    /**
     * Construct a {@link LuaUtf8String} around, possibly using the the supplied
     * byte array as the backing store.
     * <p>
     * The caller must ensure that the array is not mutated after the call.
     * However, if the string is short enough the short-string cache is checked
     * for a match which may be used instead of the supplied byte array.
     * <p>
     *
     * @param bytes byte buffer
     * @return {@link LuaUtf8String} wrapping the byte buffer, or an equivalent string.
     */
    static public LuaUtf8String valueUsing(int[] bytes, int off, int len) {
        return new LuaUtf8String(bytes, off, len);
    }

    /**
     * Construct a {@link LuaUtf8String} using the supplied characters as byte values.
     * <p>
     * Only the low-order 8-bits of each character are used, the remainder is ignored.
     * <p>
     * This is most useful for constructing byte sequences that do not conform to UTF8.
     *
     * @param bytes array of char, whose values are truncated at 8-bits each and put into a byte array.
     * @return {@link LuaUtf8String} wrapping a copy of the byte buffer
     */
    public static LuaUtf8String valueOf(int[] bytes) {
        return valueOf(bytes, 0, bytes.length);
    }

    /**
     * Construct a {@link LuaUtf8String} using the supplied characters as byte values.
     * <p>
     * Only the low-order 8-bits of each character are used, the remainder is ignored.
     * <p>
     * This is most useful for constructing byte sequences that do not conform to UTF8.
     *
     * @param bytes array of char, whose values are truncated at 8-bits each and put into a byte array.
     * @return {@link LuaUtf8String} wrapping a copy of the byte buffer
     */
    public static LuaUtf8String valueOf(int[] bytes, int off, int len) {
        return valueUsing(bytes, off, len);
    }


    /**
     * Construct a {@link LuaUtf8String} for all the bytes in a byte array, possibly using
     * the supplied array as the backing store.
     * <p>
     * The LuaUtf8String returned will either be a new LuaUtf8String containing the byte array,
     * or be an existing LuaUtf8String used already having the same value.
     * <p>
     * The caller must not mutate the contents of the byte array after this call, as
     * it may be used elsewhere due to recent short string caching.
     *
     * @param bytes byte buffer
     * @return {@link LuaUtf8String} wrapping the byte buffer
     */
    public static LuaUtf8String valueUsing(int[] bytes) {
        return valueUsing(bytes, 0, bytes.length);
    }

    /**
     * Construct a {@link LuaUtf8String} around a byte array without copying the contents.
     * <p>
     * The array is used directly after this is called, so clients must not change contents.
     * <p>
     *
     * @param bytes  byte buffer
     * @param offset offset into the byte buffer
     * @param length length of the byte buffer
     * @return {@link LuaUtf8String} wrapping the byte buffer
     */
    private LuaUtf8String(int[] bytes, int offset, int length) {
        this.m_bytes = bytes;
        this.m_offset = offset;
        this.m_length = length;
    }

    public boolean isstring() {
        return true;
    }

    public LuaValue getmetatable() {
        return s_metatable;
    }

    public int type() {
        return LuaValue.TSTRING;
    }

    public String typename() {
        return "string";
    }

    public String tojstring() {
        if (m_string == null)
            m_string = decodeAsUtf8(m_bytes, m_offset, m_length);
        return m_string;
    }

    @Override
    public LuaValue get(int key) {
        return LuaValue.valueOf(m_bytes[m_offset + posrelat(key, m_length) - 1]);
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.isnumber())
            return get(key.toint());
        return super.get(key);
    }

    static int posrelat(int pos, int len) {
        return (pos >= 0) ? pos : len + pos + 1;
    }

    // unary operators
    public LuaValue neg() {
        double d = scannumber();
        return Double.isNaN(d) ? super.neg() : valueOf(-d);
    }

    // basic binary arithmetic
    public LuaValue add(LuaValue rhs) {
        double d = scannumber();
        return Double.isNaN(d) ? arithmt(ADD, rhs) : rhs.add(d);
    }

    public LuaValue add(double rhs) {
        return valueOf(checkarith() + rhs);
    }

    public LuaValue add(int rhs) {
        return valueOf(checkarith() + rhs);
    }

    public LuaValue sub(LuaValue rhs) {
        double d = scannumber();
        return Double.isNaN(d) ? arithmt(SUB, rhs) : rhs.subFrom(d);
    }

    public LuaValue sub(double rhs) {
        return valueOf(checkarith() - rhs);
    }

    public LuaValue sub(int rhs) {
        return valueOf(checkarith() - rhs);
    }

    public LuaValue subFrom(double lhs) {
        return valueOf(lhs - checkarith());
    }

    public LuaValue mul(LuaValue rhs) {
        double d = scannumber();
        return Double.isNaN(d) ? arithmt(MUL, rhs) : rhs.mul(d);
    }

    public LuaValue mul(double rhs) {
        return valueOf(checkarith() * rhs);
    }

    public LuaValue mul(int rhs) {
        return valueOf(checkarith() * rhs);
    }

    public LuaValue pow(LuaValue rhs) {
        double d = scannumber();
        return Double.isNaN(d) ? arithmt(POW, rhs) : rhs.powWith(d);
    }

    public LuaValue pow(double rhs) {
        return MathLib.dpow(checkarith(), rhs);
    }

    public LuaValue pow(int rhs) {
        return MathLib.dpow(checkarith(), rhs);
    }

    public LuaValue powWith(double lhs) {
        return MathLib.dpow(lhs, checkarith());
    }

    public LuaValue powWith(int lhs) {
        return MathLib.dpow(lhs, checkarith());
    }

    public LuaValue div(LuaValue rhs) {
        double d = scannumber();
        return Double.isNaN(d) ? arithmt(DIV, rhs) : rhs.divInto(d);
    }

    public LuaValue div(double rhs) {
        return LuaDouble.ddiv(checkarith(), rhs);
    }

    public LuaValue div(int rhs) {
        return LuaDouble.ddiv(checkarith(), rhs);
    }

    public LuaValue divInto(double lhs) {
        return LuaDouble.ddiv(lhs, checkarith());
    }

    public LuaValue mod(LuaValue rhs) {
        double d = scannumber();
        return Double.isNaN(d) ? arithmt(MOD, rhs) : rhs.modFrom(d);
    }

    public LuaValue mod(double rhs) {
        return LuaDouble.dmod(checkarith(), rhs);
    }

    public LuaValue mod(int rhs) {
        return LuaDouble.dmod(checkarith(), rhs);
    }

    public LuaValue modFrom(double lhs) {
        return LuaDouble.dmod(lhs, checkarith());
    }

    // relational operators, these only work with other strings
    public LuaValue lt(LuaValue rhs) {
        return rhs.isstring() ? (rhs.strcmp(this) > 0 ? LuaValue.TRUE : FALSE) : super.lt(rhs);
    }

    public boolean lt_b(LuaValue rhs) {
        return rhs.isstring() ? rhs.strcmp(this) > 0 : super.lt_b(rhs);
    }

    public boolean lt_b(int rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    public boolean lt_b(double rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    public LuaValue lteq(LuaValue rhs) {
        return rhs.isstring() ? (rhs.strcmp(this) >= 0 ? LuaValue.TRUE : FALSE) : super.lteq(rhs);
    }

    public boolean lteq_b(LuaValue rhs) {
        return rhs.isstring() ? rhs.strcmp(this) >= 0 : super.lteq_b(rhs);
    }

    public boolean lteq_b(int rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    public boolean lteq_b(double rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    public LuaValue gt(LuaValue rhs) {
        return rhs.isstring() ? (rhs.strcmp(this) < 0 ? LuaValue.TRUE : FALSE) : super.gt(rhs);
    }

    public boolean gt_b(LuaValue rhs) {
        return rhs.isstring() ? rhs.strcmp(this) < 0 : super.gt_b(rhs);
    }

    public boolean gt_b(int rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    public boolean gt_b(double rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    public LuaValue gteq(LuaValue rhs) {
        return rhs.isstring() ? (rhs.strcmp(this) <= 0 ? LuaValue.TRUE : FALSE) : super.gteq(rhs);
    }

    public boolean gteq_b(LuaValue rhs) {
        return rhs.isstring() ? rhs.strcmp(this) <= 0 : super.gteq_b(rhs);
    }

    public boolean gteq_b(int rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    public boolean gteq_b(double rhs) {
        typerror("attempt to compare string with number");
        return false;
    }

    // concatenation
    public LuaValue concat(LuaValue rhs) {
        return rhs.concatTo(this);
    }

    public Buffer concat(Buffer rhs) {
        return rhs.concatTo(this);
    }

    public LuaValue concatTo(LuaNumber lhs) {
        return concatTo(lhs.strvalue());
    }

    public LuaValue concatTo(LuaUtf8String lhs) {
        int[] b = new int[lhs.m_length + this.m_length];
        System.arraycopy(lhs.m_bytes, lhs.m_offset, b, 0, lhs.m_length);
        System.arraycopy(this.m_bytes, this.m_offset, b, lhs.m_length, this.m_length);
        return valueUsing(b, 0, b.length);
    }

    // string comparison
    public int strcmp(LuaValue lhs) {
        return -lhs.strcmp(this);
    }

    public int strcmp(LuaUtf8String rhs) {
        for (int i = 0, j = 0; i < m_length && j < rhs.m_length; ++i, ++j) {
            if (m_bytes[m_offset + i] != rhs.m_bytes[rhs.m_offset + j]) {
                return ((int) m_bytes[m_offset + i]) - ((int) rhs.m_bytes[rhs.m_offset + j]);
            }
        }
        return m_length - rhs.m_length;
    }

    /**
     * Check for number in arithmetic, or throw aritherror
     */
    private double checkarith() {
        double d = scannumber();
        if (Double.isNaN(d))
            aritherror();
        return d;
    }

    public int checkint() {
        return (int) (long) checkdouble();
    }

    public LuaInteger checkinteger() {
        return valueOf(checkint());
    }

    public long checklong() {
        return (long) checkdouble();
    }

    public double checkdouble() {
        double d = scannumber();
        if (Double.isNaN(d))
            argerror("number");
        return d;
    }

    public LuaNumber checknumber() {
        return valueOf(checkdouble());
    }

    public LuaNumber checknumber(String msg) {
        double d = scannumber();
        if (Double.isNaN(d))
            error(msg);
        return valueOf(d);
    }

    public boolean isnumber() {
        double d = scannumber();
        return !Double.isNaN(d);
    }

    public boolean isint() {
        double d = scannumber();
        if (Double.isNaN(d))
            return false;
        int i = (int) d;
        return i == d;
    }

    public boolean islong() {
        double d = scannumber();
        if (Double.isNaN(d))
            return false;
        long l = (long) d;
        return l == d;
    }

    public byte tobyte() {
        return (byte) toint();
    }

    public char tochar() {
        return (char) toint();
    }

    public double todouble() {
        double d = scannumber();
        return Double.isNaN(d) ? 0 : d;
    }

    public float tofloat() {
        return (float) todouble();
    }

    public int toint() {
        return (int) tolong();
    }

    public long tolong() {
        return (long) todouble();
    }

    public short toshort() {
        return (short) toint();
    }

    public double optdouble(double defval) {
        return checkdouble();
    }

    public int optint(int defval) {
        return checkint();
    }

    public LuaInteger optinteger(LuaInteger defval) {
        return checkinteger();
    }

    public long optlong(long defval) {
        return checklong();
    }

    public LuaNumber optnumber(LuaNumber defval) {
        return checknumber();
    }

    public LuaString optstring(LuaString defval) {
        return strvalue();
    }

    public LuaValue tostring() {
        return strvalue();
    }

    public String optjstring(String defval) {
        return tojstring();
    }

    public LuaString strvalue() {
        if (m_lua_string == null)
            m_lua_string = toLuaString(m_bytes, m_offset, m_length);
        return m_lua_string;
    }

    public static LuaString toLuaString(int[] bytes, int off, int len) {
        String s = new String(bytes, off, len);
        return LuaString.valueOf(s);
    }

    /**
     * Take a substring using Java zero-based indexes for begin and end or range.
     *
     * @param beginIndex The zero-based index of the first character to include.
     * @param endIndex   The zero-based index of position after the last character.
     * @return LuaUtf8String which is a substring whose first character is at offset
     * beginIndex and extending for (endIndex - beginIndex ) characters.
     */
    public LuaUtf8String substring(int beginIndex, int endIndex) {
        final int off = m_offset + beginIndex;
        final int len = endIndex - beginIndex;
        return len >= m_length / 2 ?
                valueUsing(m_bytes, off, len) :
                valueOf(m_bytes, off, len);
    }

    public int hashCode() {
        if (isHascode)
            return m_hashcode;
        m_hashcode = tostring().hashCode();
        isHascode = true;
        return m_hashcode;
    }

    /**
     * Compute the hash code of a sequence of bytes within a byte array using
     * lua's rules for string hashes.  For long strings, not all bytes are hashed.
     *
     * @param bytes  byte array containing the bytes.
     * @param offset offset into the hash for the first byte.
     * @param length number of bytes starting with offset that are part of the string.
     * @return hash for the string defined by bytes, offset, and length.
     */
    public static int hashCode(int[] bytes, int offset, int length) {
        int h = length;  /* seed */
        int step = (length >> 5) + 1;  /* if string is too long, don't hash all its chars */
        for (int l1 = length; l1 >= step; l1 -= step)  /* compute hash */
            h = h ^ ((h << 5) + (h >> 2) + (((int) bytes[offset + l1 - 1]) & 0x0FF));
        return h;
    }

    // object comparison, used in key comparison
    public boolean equals(Object o) {
        if (o instanceof LuaUtf8String) {
            return raweq((LuaUtf8String) o);
        }
        return false;
    }

    // equality w/ metatable processing
    public LuaValue eq(LuaValue val) {
        return val.raweq(this) ? TRUE : FALSE;
    }

    public boolean eq_b(LuaValue val) {
        return val.raweq(this);
    }

    // equality w/o metatable processing
    public boolean raweq(LuaValue val) {
        return (val instanceof LuaUtf8String)
                ? raweq((LuaUtf8String) val)
                : val.raweq(this);
    }

    public boolean raweq(LuaString s) {
        return strvalue().raweq(s);
    }

    public boolean raweq(LuaUtf8String s) {
        if (this == s)
            return true;
        if (s.m_length != m_length)
            return false;
        if (s.m_bytes == m_bytes && s.m_offset == m_offset)
            return true;
        if (s.hashCode() != hashCode())
            return false;
        for (int i = 0; i < m_length; i++)
            if (s.m_bytes[s.m_offset + i] != m_bytes[m_offset + i])
                return false;
        return true;
    }

    public static boolean equals(LuaUtf8String a, int i, LuaUtf8String b, int j, int n) {
        return equals(a.m_bytes, a.m_offset + i, b.m_bytes, b.m_offset + j, n);
    }

    /**
     * Return true if the bytes in the supplied range match this LuaUtf8Strings bytes.
     */
    private boolean byteseq(int[] bytes, int off, int len) {
        return (m_length == len && equals(m_bytes, m_offset, bytes, off, len));
    }

    public static boolean equals(int[] a, int i, int[] b, int j, int n) {
        if (a.length < i + n || b.length < j + n)
            return false;
        while (--n >= 0)
            if (a[i++] != b[j++])
                return false;
        return true;
    }


    public LuaValue len() {
        return LuaInteger.valueOf(m_length);
    }

    public int length() {
        return m_length;
    }

    public int rawlen() {
        return m_length;
    }

    public int luaByte(int index) {
        return m_bytes[m_offset + index];
    }

    public int charAt(int index) {
        if (index < 0 || index >= m_length)
            throw new IndexOutOfBoundsException();
        return luaByte(index);
    }

    public String checkjstring() {
        return tojstring();
    }

    public LuaString checkstring() {
        return strvalue();
    }


    /**
     * Copy the bytes of the string into the given byte array.
     *
     * @param strOffset   offset from which to copy
     * @param bytes       destination byte array
     * @param arrayOffset offset in destination
     * @param len         number of bytes to copy
     */
    public void copyInto(int strOffset, int[] bytes, int arrayOffset, int len) {
        System.arraycopy(m_bytes, m_offset + strOffset, bytes, arrayOffset, len);
    }

    /**
     * Java version of strpbrk - find index of any byte that in an accept string.
     *
     * @param accept {@link LuaUtf8String} containing characters to look for.
     * @return index of first match in the {@code accept} string, or -1 if not found.
     */
    public int indexOfAny(LuaUtf8String accept) {
        final int ilimit = m_offset + m_length;
        final int jlimit = accept.m_offset + accept.m_length;
        for (int i = m_offset; i < ilimit; ++i) {
            for (int j = accept.m_offset; j < jlimit; ++j) {
                if (m_bytes[i] == accept.m_bytes[j]) {
                    return i - m_offset;
                }
            }
        }
        return -1;
    }

    /**
     * Find the index of a byte starting at a point in this string
     *
     * @param b     the byte to look for
     * @param start the first index in the string
     * @return index of first match found, or -1 if not found.
     */
    public int indexOf(byte b, int start) {
        for (int i = start; i < m_length; ++i) {
            if (m_bytes[m_offset + i] == b)
                return i;
        }
        return -1;
    }

    /**
     * Find the index of a string starting at a point in this string
     *
     * @param s     the string to search for
     * @param start the first index in the string
     * @return index of first match found, or -1 if not found.
     */
    public int indexOf(LuaUtf8String s, int start) {
        final int slen = s.length();
        final int limit = m_length - slen;
        for (int i = start; i <= limit; ++i) {
            if (equals(m_bytes, m_offset + i, s.m_bytes, s.m_offset, slen))
                return i;
        }
        return -1;
    }

    /**
     * Find the last index of a string in this string
     *
     * @param s the string to search for
     * @return index of last match found, or -1 if not found.
     */
    public int lastIndexOf(LuaUtf8String s) {
        final int slen = s.length();
        final int limit = m_length - slen;
        for (int i = limit; i >= 0; --i) {
            if (equals(m_bytes, m_offset + i, s.m_bytes, s.m_offset, slen))
                return i;
        }
        return -1;
    }


    /**
     * Convert to Java String interpreting as utf8 characters.
     *
     * @param bytes  byte array in UTF8 encoding to convert
     * @param offset starting index in byte array
     * @param length number of bytes to convert
     * @return Java String corresponding to the value of bytes interpreted using UTF8
     * @see #lengthAsUtf8(int[])
     * @see #encodeToUtf8(int[], int, int[], int)
     * @see #isValidUtf8()
     */
    public static String decodeAsUtf8(int[] bytes, int offset, int length) {
        return new String(bytes, offset, length);
    }

    /**
     * Count the number of bytes required to encode the string as UTF-8.
     *
     * @param chars Array of unicode characters to be encoded as UTF-8
     * @return count of bytes needed to encode using UTF-8
     * @see #encodeToUtf8(int[], int, int[], int)
     * @see #decodeAsUtf8(int[], int, int)
     * @see #isValidUtf8()
     */
    public static int lengthAsUtf8(int[] chars) {
        int i, b;
        int c;
        for (i = b = chars.length; --i >= 0; )
            if ((c = chars[i]) >= 0x80)
                b += (c >= 0x800) ? 2 : 1;
        return b;
    }


    /**
     * Check that a byte sequence is valid UTF-8
     *
     * @return true if it is valid UTF-8, otherwise false
     * @see #lengthAsUtf8(int[])
     * @see #encodeToUtf8(int[], int, int[], int)
     * @see #decodeAsUtf8(int[], int, int)
     */
    public boolean isValidUtf8() {
        for (int i = m_offset, j = m_offset + m_length; i < j; ) {
            int c = m_bytes[i++];
            if (c >= 0) continue;
            if (((c & 0xE0) == 0xC0)
                    && i < j
                    && (m_bytes[i++] & 0xC0) == 0x80) continue;
            if (((c & 0xF0) == 0xE0)
                    && i + 1 < j
                    && (m_bytes[i++] & 0xC0) == 0x80
                    && (m_bytes[i++] & 0xC0) == 0x80) continue;
            return false;
        }
        return true;
    }

    // --------------------- number conversion -----------------------

    /**
     * convert to a number using baee 10 or base 16 if it starts with '0x',
     * or NIL if it can't be converted
     *
     * @return IntValue, DoubleValue, or NIL depending on the content of the string.
     * @see LuaValue#tonumber()
     */
    public LuaValue tonumber() {
        double d = scannumber();
        return Double.isNaN(d) ? NIL : valueOf(d);
    }

    /**
     * convert to a number using a supplied base, or NIL if it can't be converted
     *
     * @param base the base to use, such as 10
     * @return IntValue, DoubleValue, or NIL depending on the content of the string.
     * @see LuaValue#tonumber()
     */
    public LuaValue tonumber(int base) {
        double d = scannumber(base);
        return Double.isNaN(d) ? NIL : valueOf(d);
    }

    /**
     * Convert to a number in base 10, or base 16 if the string starts with '0x',
     * or return Double.NaN if it cannot be converted to a number.
     *
     * @return double value if conversion is valid, or Double.NaN if not
     */
    public double scannumber() {
        int i = m_offset, j = m_offset + m_length;
        while (i < j && m_bytes[i] == ' ') ++i;
        while (i < j && m_bytes[j - 1] == ' ') --j;
        if (i >= j)
            return Double.NaN;
        if (m_bytes[i] == '0' && i + 1 < j && (m_bytes[i + 1] == 'x' || m_bytes[i + 1] == 'X'))
            return scanlong(16, i + 2, j);
        double l = scanlong(10, i, j);
        return Double.isNaN(l) ? scandouble(i, j) : l;
    }

    /**
     * Convert to a number in a base, or return Double.NaN if not a number.
     *
     * @param base the base to use between 2 and 36
     * @return double value if conversion is valid, or Double.NaN if not
     */
    public double scannumber(int base) {
        if (base < 2 || base > 36)
            return Double.NaN;
        int i = m_offset, j = m_offset + m_length;
        while (i < j && m_bytes[i] == ' ') ++i;
        while (i < j && m_bytes[j - 1] == ' ') --j;
        if (i >= j)
            return Double.NaN;
        return scanlong(base, i, j);
    }

    /**
     * Scan and convert a long value, or return Double.NaN if not found.
     *
     * @param base  the base to use, such as 10
     * @param start the index to start searching from
     * @param end   the first index beyond the search range
     * @return double value if conversion is valid,
     * or Double.NaN if not
     */
    private double scanlong(int base, int start, int end) {
        long x = 0;
        boolean neg = (m_bytes[start] == '-');
        for (int i = (neg ? start + 1 : start); i < end; i++) {
            int digit = m_bytes[i] - (base <= 10 || (m_bytes[i] >= '0' && m_bytes[i] <= '9') ? '0' :
                    m_bytes[i] >= 'A' && m_bytes[i] <= 'Z' ? ('A' - 10) : ('a' - 10));
            if (digit < 0 || digit >= base)
                return Double.NaN;
            x = x * base + digit;
            if (x < 0)
                return Double.NaN; // overflow
        }
        return neg ? -x : x;
    }

    /**
     * Scan and convert a double value, or return Double.NaN if not a double.
     *
     * @param start the index to start searching from
     * @param end   the first index beyond the search range
     * @return double value if conversion is valid,
     * or Double.NaN if not
     */
    private double scandouble(int start, int end) {
        if (end > start + 64) end = start + 64;
        for (int i = start; i < end; i++) {
            switch (m_bytes[i]) {
                case '-':
                case '+':
                case '.':
                case 'e':
                case 'E':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
                default:
                    return Double.NaN;
            }
        }
        int[] c = new int[end - start];
        for (int i = start; i < end; i++)
            c[i - start] = m_bytes[i];
        try {
            return Double.parseDouble(new String(c, start, end));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * Print the bytes of the LuaUtf8String to a PrintStream as if it were
     * an ASCII string, quoting and escaping control characters.
     *
     * @param ps PrintStream to print to.
     */
    public void printToStream(PrintStream ps) {
        for (int i = 0, n = m_length; i < n; i++) {
            int c = m_bytes[m_offset + i];
            ps.print((char) c);
        }
    }
}