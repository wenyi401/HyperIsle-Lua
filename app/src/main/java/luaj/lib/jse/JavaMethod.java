/* ******************************************************************************
 * Copyright (c) 2011 Luaj.org. All rights reserved.
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
 ***************************************************************************** */
package luaj.lib.jse;

import luaj.Lua;
import luaj.LuaError;
import luaj.LuaFunction;
import luaj.LuaValue;
import luaj.Varargs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * LuaValue that represents a Java method.
 * <p>
 * Can be invoked via call(LuaValue...) and related methods.
 * <p>
 * This class is not used directly.
 * It is returned by calls to calls to {@link JavaInstance#get(LuaValue key)}
 * when a method is named.
 *
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
class JavaMethod extends JavaMember {

    static final Map<Method,LuaValue> methods = Collections.synchronizedMap(new HashMap<>());
    private final Class<?> returnType;

    static JavaMethod forMethod(Method m) {
        JavaMethod j = (JavaMethod) methods.get(m);
        if (j == null)
            methods.put(m, j = new JavaMethod(m));
        return j;
    }

    static LuaFunction forMethods(JavaMethod[] m) {
        return new Overload(m);
    }

    final Method method;

    private JavaMethod(Method m) {
        super(m.getParameterTypes(), m.getModifiers());
        this.method = m;
        this.returnType = m.getReturnType();
        try {
            if (!m.isAccessible())
                m.setAccessible(true);
        } catch (Exception s) {
            //nothing
        }
    }

    public LuaValue call() {
        if (Lua.LUA_JAVA_OO)
            return invokeJavaMethod(uservalue, LuaValue.NONE);
        else
            return error("method cannot be called without instance");
    }

    public LuaValue call(LuaValue arg) {
        if (Lua.LUA_JAVA_OO)
            return invokeJavaMethod(uservalue, arg);
        else
            return invokeJavaMethod(arg, LuaValue.NONE);
    }

    public LuaValue call(LuaValue arg1, LuaValue arg2) {
        if (Lua.LUA_JAVA_OO)
            return invokeJavaMethod(uservalue, LuaValue.varargsOf(arg1, arg2));
        else
            return invokeJavaMethod(arg1, arg2);
    }

    public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        if (Lua.LUA_JAVA_OO)
            return invokeJavaMethod(uservalue, LuaValue.varargsOf(arg1, arg2, arg3));
        else
            return invokeJavaMethod(arg1, LuaValue.varargsOf(arg2, arg3));
    }

    public Varargs invoke(LuaValue[] args) {
        return invoke(LuaValue.varargsOf(args));
    }

    public Varargs invoke(Varargs args) {
        if (Lua.LUA_JAVA_OO)
            return invokeJavaMethod(uservalue, args);
        else
            return invokeJavaMethod(args.arg(1), args.subargs(2));
    }

    @Override
    public LuaValue invokeJavaMethod(LuaValue obj, Varargs args) {
        if(varargs==null&&fixedargs.length!=args.narg())
            throw new IllegalArgumentException(method.toString());
        Object instance = obj.checkuserdata();
        Object[] a = convertArgs(args);
        try {
            if (returnType == Void.TYPE) {
                method.invoke(instance, a);
                return obj;
            }
            return CoerceJavaToLua.coerce(method.invoke(instance, a));
        } catch (InvocationTargetException e) {
            e.getTargetException().printStackTrace();
            throw new LuaError(method + " " + e.getTargetException());
            //throw new LuaError(e.getTargetException());
        } catch (Exception e) {
            return LuaValue.error("coercion error " + e);
        }
    }

    @Override
    public String tojstring() {
        return "JavaMethod{\n  " +
                method +
                "\n}";
    }

    /**
     * LuaValue that represents an overloaded Java method.
     * <p>
     * On invocation, will pick the best method from the list, and invoke it.
     * <p>
     * This class is not used directly.
     * It is returned by calls to calls to {@link JavaInstance#get(LuaValue key)}
     * when an overloaded method is named.
     */
    static class Overload extends LuaFunction {

        final JavaMethod[] methods;

        Overload(JavaMethod[] methods) {
            this.methods = methods;
        }

        public LuaValue call() {
            if (Lua.LUA_JAVA_OO)
                return invokeJavaMethod(uservalue, LuaValue.NONE);
            else
                return error("method cannot be called without instance");
        }

        public LuaValue call(LuaValue arg) {
            if (Lua.LUA_JAVA_OO)
                return invokeJavaMethod(uservalue, arg);
            else
                return invokeJavaMethod(arg, LuaValue.NONE);
        }

        public LuaValue call(LuaValue arg1, LuaValue arg2) {
            if (Lua.LUA_JAVA_OO)
                return invokeJavaMethod(uservalue, LuaValue.varargsOf(arg1, arg2));
            else
                return invokeJavaMethod(arg1, arg2);
        }

        public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
            if (Lua.LUA_JAVA_OO)
                return invokeJavaMethod(uservalue, LuaValue.varargsOf(arg1, arg2, arg3));
            else
                return invokeJavaMethod(arg1, LuaValue.varargsOf(arg2, arg3));
        }

        public Varargs invoke(LuaValue[] args) {
            return invoke(LuaValue.varargsOf(args));
        }

        public Varargs invoke(Varargs args) {
            if (Lua.LUA_JAVA_OO)
                return invokeJavaMethod(uservalue, args);
            else
                return invokeJavaMethod(args.arg(1), args.subargs(2));
        }

        @Override
        public LuaValue invokeJavaMethod(LuaValue instance, Varargs args) {
            JavaMethod best = null;
            int score = CoerceLuaToJava.SCORE_UNCOERCIBLE;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < methods.length; i++) {
                int s = methods[i].score(args);
                if (s < score) {
                    score = s;
                    best = methods[i];
                    if (score == 0)
                        break;
                }
            }

            // any match?
            if (best == null) {
                LuaValue.error("no coercible public method\n"+this);
            }
            // invoke it
            return best.invokeJavaMethod(instance, args);
        }

        @Override
        public String tojstring() {
            StringBuilder buf = new StringBuilder();
            buf.append("JavaMethod{\n");
            for (JavaMethod method : methods) {
                buf.append("  ").append(method.method).append("\n");
            }
            buf.append("}");
            return buf.toString();
        }

    }

    public static class JavaOOMethod extends LuaValue {
        private final JavaInstance mObj;
        private final LuaValue mMethod;

        public JavaOOMethod(JavaInstance obj, LuaValue m) {
            mObj = obj;
            mMethod = m;
        }

        public LuaValue call() {
            return mMethod.invokeJavaMethod(mObj, LuaValue.NONE);
        }

        public LuaValue call(LuaValue arg) {
            return mMethod.invokeJavaMethod(mObj, arg);
        }

        public LuaValue call(LuaValue arg1, LuaValue arg2) {
            return mMethod.invokeJavaMethod(mObj, LuaValue.varargsOf(arg1, arg2));
        }

        public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
            return mMethod.invokeJavaMethod(mObj, LuaValue.varargsOf(arg1, arg2, arg3));
        }

        public Varargs invoke(LuaValue[] args) {
            return invoke(LuaValue.varargsOf(args));
        }

        public Varargs invoke(Varargs args) {
            return mMethod.invokeJavaMethod(mObj, args);
        }

        @Override
        public int type() {
            return mMethod.type();
        }

        @Override
        public String typename() {
            return mMethod.typename();
        }

        @Override
        public String tojstring() {
            return mMethod.tojstring();
        }
    }
}
