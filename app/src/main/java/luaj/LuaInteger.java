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
package luaj;

import luaj.lib.MathLib;

/**
 * Extension of {@link LuaNumber} which can hold a Java int as its value.
 * <p>
 * These instance are not instantiated directly by clients, but indirectly
 * via the static functions {@link LuaValue#valueOf(int)} or {@link LuaValue#valueOf(double)}
 * functions.  This ensures that policies regarding pooling of instances are
 * encapsulated.
 * <p>
 * There are no API's specific to LuaInteger that are useful beyond what is already
 * exposed in {@link LuaValue}.
 *
 * @see LuaValue
 * @see LuaNumber
 * @see LuaDouble
 * @see LuaValue#valueOf(int)
 * @see LuaValue#valueOf(double)
 */
public class LuaInteger extends LuaNumber {

    private static final LuaInteger[] intValues = new LuaInteger[512];

    static {
        for (int i = 0; i < 512; i++)
            intValues[i] = new LuaInteger(i - 256);
    }

    /**
     * The value being held by this instance.
     */
    public final long v;

    ;

    // TODO consider moving this to LuaValue

    /**
     * Package protected constructor.
     *
     * @see LuaValue#valueOf(int)
     **/
    LuaInteger(long i) {
        this.v = i;
    }

    public static LuaInteger valueOf(int i) {
        return i <= 255 && i >= -256 ? intValues[i + 256] : new LuaInteger(i);
    }

    /**
     * Return a LuaNumber that represents the value provided
     *
     * @param i long value to represent.
     * @return LuaNumber that is eithe LuaInteger or LuaDouble representing l
     * @see LuaValue#valueOf(int)
     * @see LuaValue#valueOf(double)
     */
    public static LuaInteger valueOf(long i) {
        return i <= 255 && i >= -256 ? intValues[(int) (i + 256)] : new LuaInteger(i);
    }

    public static LuaInteger valueOf(double i) {
        return i <= 255 && i >= -256 ? intValues[(int) (i + 256)] : new LuaInteger((long) i);
    }

    public static int hashCode(int x) {
        return x;
    }

    public boolean isint() {
        return true;
    }

    public boolean isinttype() {
        return true;
    }

    public boolean islong() {
        return true;
    }

    public byte tobyte() {
        return (byte) v;
    }

    public char tochar() {
        return (char) v;
    }

    public double todouble() {
        return v;
    }

    public float tofloat() {
        return v;
    }

    public int toint() {
        return (int) v;
    }

    public long tolong() {
        return v;
    }

    public short toshort() {
        return (short) v;
    }

    public double optdouble(double defval) {
        return v;
    }

    public int optint(int defval) {
        return (int) v;
    }

    public LuaInteger optinteger(LuaInteger defval) {
        return this;
    }

    public long optlong(long defval) {
        return v;
    }

    public String tojstring() {
        return Long.toString(v);
    }

    public LuaString strvalue() {
        return LuaString.valueOf(Long.toString(v));
    }

    public LuaString optstring(LuaString defval) {
        return LuaString.valueOf(Long.toString(v));
    }

    public LuaValue tostring() {
        return LuaString.valueOf(Long.toString(v));
    }

    public String optjstring(String defval) {
        return Long.toString(v);
    }

    public LuaInteger checkinteger() {
        return this;
    }

    public boolean isstring() {
        return true;
    }

    public int hashCode() {
        return Long.valueOf(v).hashCode();
    }

    // unary operators
    public LuaValue neg() {
        return valueOf(-(long) v);
    }

    // object equality, used for key comparison
    public boolean equals(Object o) {
        return o instanceof LuaInteger ? ((LuaInteger) o).v == v : false;
    }

    // equality w/ metatable processing
    public LuaValue eq(LuaValue val) {
        return val.raweq(v) ? TRUE : FALSE;
    }

    public boolean eq_b(LuaValue val) {
        return val.raweq(v);
    }

    // equality w/o metatable processing
    public boolean raweq(LuaValue val) {
        return val.raweq(v);
    }

    public boolean raweq(double val) {
        return v == val;
    }

    public boolean raweq(long val) {
        return v == val;
    }

    // arithmetic operators
    public LuaValue add(LuaInteger rhs) {
        return new LuaInteger(rhs.v + v);
    }

    public LuaValue add(LuaValue rhs) {
        return rhs.add(v);
    }

    public LuaValue add(double lhs) {
        return LuaDouble.valueOf(lhs + v);
    }

    public LuaValue add(long lhs) {
        return LuaInteger.valueOf(lhs + (long) v);
    }

    public LuaValue sub(LuaValue rhs) {
        return rhs.subFrom(v);
    }

    public LuaValue sub(double rhs) {
        return LuaDouble.valueOf(v - rhs);
    }

    public LuaValue sub(long rhs) {
        return LuaDouble.valueOf(v - rhs);
    }

    public LuaValue subFrom(double lhs) {
        return LuaDouble.valueOf(lhs - v);
    }

    public LuaValue subFrom(long lhs) {
        return LuaInteger.valueOf(lhs - (long) v);
    }

    public LuaValue mul(LuaValue rhs) {
        return rhs.mul(v);
    }

    public LuaValue mul(double lhs) {
        return LuaDouble.valueOf(lhs * v);
    }

    public LuaValue mul(long lhs) {
        return LuaInteger.valueOf(lhs * (long) v);
    }

    public LuaValue pow(LuaValue rhs) {
        return rhs.powWith(v);
    }

    public LuaValue pow(double rhs) {
        return MathLib.dpow(v, rhs);
    }

    public LuaValue pow(long rhs) {
        return MathLib.dpow(v, rhs);
    }

    public LuaValue powWith(double lhs) {
        return MathLib.dpow(lhs, v);
    }

    public LuaValue powWith(long lhs) {
        return MathLib.dpow(lhs, v);
    }

    public LuaValue div(LuaValue rhs) {
        return rhs.divInto(v);
    }

    public LuaValue div(double rhs) {
        return LuaDouble.ddiv(v, rhs);
    }

    public LuaValue div(long rhs) {
        return LuaDouble.ddiv(v, rhs);
    }

    public LuaValue divInto(double lhs) {
        return LuaDouble.ddiv(lhs, v);
    }

    public LuaValue mod(LuaValue rhs) {
        return rhs.modFrom(v);
    }

    public LuaValue mod(double rhs) {
        return LuaDouble.dmod(v, rhs);
    }

    public LuaValue mod(long rhs) {
        return LuaDouble.dmod(v, rhs);
    }

    public LuaValue modFrom(double lhs) {
        return LuaDouble.dmod(lhs, v);
    }

    public LuaValue idiv(LuaValue rhs) {
        return rhs.idiv(v);
    }

    public LuaValue idiv(long lhs) {
        return LuaInteger.valueOf(lhs / v);
    }

    public LuaValue band(LuaValue rhs) {
        return rhs.band(v);
    }

    public LuaValue band(long lhs) {
        return LuaInteger.valueOf(lhs & v);
    }

    public LuaValue bor(LuaValue rhs) {
        return rhs.bor(v);
    }

    public LuaValue bor(long lhs) {
        return LuaInteger.valueOf(lhs | v);
    }

    public LuaValue bxor(LuaValue rhs) {
        return rhs.bxor(v);
    }

    public LuaValue bxor(long lhs) {
        return LuaInteger.valueOf(lhs ^ v);
    }

    public LuaValue shl(LuaValue rhs) {
        return rhs.shl(v);
    }

    public LuaValue shl(long lhs) {
        return v < 0 ? shr(lhs) : v > 63 ? ZERO : LuaInteger.valueOf(lhs << v);
    }

    public LuaValue shr(LuaValue rhs) {
        return rhs.shr(v);
    }

    public LuaValue shr(long lhs) {
        return v < 0 ? shl(lhs) : v > 63 ? ZERO : LuaInteger.valueOf(lhs >> v);
    }

    public LuaValue bnot() {
        return LuaInteger.valueOf(~v);
    }

    // relational operators
    public LuaValue lt(LuaValue rhs) {
        return rhs instanceof LuaNumber ? (rhs.gt_b(v) ? TRUE : FALSE) : super.lt(rhs);
    }

    public LuaValue lt(double rhs) {
        return v < rhs ? TRUE : FALSE;
    }

    public LuaValue lt(long rhs) {
        return v < rhs ? TRUE : FALSE;
    }

    public boolean lt_b(LuaValue rhs) {
        return rhs instanceof LuaNumber ? rhs.gt_b(v) : super.lt_b(rhs);
    }

    public boolean lt_b(long rhs) {
        return v < rhs;
    }

    public boolean lt_b(double rhs) {
        return v < rhs;
    }

    public LuaValue lteq(LuaValue rhs) {
        return rhs instanceof LuaNumber ? (rhs.gteq_b(v) ? TRUE : FALSE) : super.lteq(rhs);
    }

    public LuaValue lteq(double rhs) {
        return v <= rhs ? TRUE : FALSE;
    }

    public LuaValue lteq(long rhs) {
        return v <= rhs ? TRUE : FALSE;
    }

    public boolean lteq_b(LuaValue rhs) {
        return rhs instanceof LuaNumber ? rhs.gteq_b(v) : super.lteq_b(rhs);
    }

    public boolean lteq_b(long rhs) {
        return v <= rhs;
    }

    public boolean lteq_b(double rhs) {
        return v <= rhs;
    }

    public LuaValue gt(LuaValue rhs) {
        return rhs instanceof LuaNumber ? (rhs.lt_b(v) ? TRUE : FALSE) : super.gt(rhs);
    }

    public LuaValue gt(double rhs) {
        return v > rhs ? TRUE : FALSE;
    }

    public LuaValue gt(long rhs) {
        return v > rhs ? TRUE : FALSE;
    }

    public boolean gt_b(LuaValue rhs) {
        return rhs instanceof LuaNumber ? rhs.lt_b(v) : super.gt_b(rhs);
    }

    public boolean gt_b(long rhs) {
        return v > rhs;
    }

    public boolean gt_b(double rhs) {
        return v > rhs;
    }

    public LuaValue gteq(LuaValue rhs) {
        return rhs instanceof LuaNumber ? (rhs.lteq_b(v) ? TRUE : FALSE) : super.gteq(rhs);
    }

    public LuaValue gteq(double rhs) {
        return v >= rhs ? TRUE : FALSE;
    }

    public LuaValue gteq(long rhs) {
        return v >= rhs ? TRUE : FALSE;
    }

    public boolean gteq_b(LuaValue rhs) {
        return rhs instanceof LuaNumber ? rhs.lteq_b(v) : super.gteq_b(rhs);
    }

    public boolean gteq_b(long rhs) {
        return v >= rhs;
    }

    public boolean gteq_b(double rhs) {
        return v >= rhs;
    }

    // string comparison
    public int strcmp(LuaString rhs) {
        typerror("attempt to compare number with string");
        return 0;
    }

    public int checkint() {
        return (int) v;
    }

    public long checklong() {
        return v;
    }

    public double checkdouble() {
        return v;
    }

    public String checkjstring() {
        return String.valueOf(v);
    }

    public LuaString checkstring() {
        return valueOf(String.valueOf(v));
    }

}
