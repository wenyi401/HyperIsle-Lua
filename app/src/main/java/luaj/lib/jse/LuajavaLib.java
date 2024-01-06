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
package luaj.lib.jse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import luaj.Globals;
import luaj.LuaEnhancer;
import luaj.LuaError;
import luaj.LuaTable;
import luaj.LuaUserdata;
import luaj.LuaValue;
import luaj.Varargs;
import luaj.compiler.LuaC;
import luaj.lib.LibFunction;
import luaj.lib.OneArgFunction;
import luaj.lib.VarArgFunction;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Subclass of {@link LibFunction} which implements the features of the luajava package.
 * <p>
 * Luajava is an approach to mixing lua and java using simple functions that bind
 * java classes and methods to lua dynamically.  The API is documented on the
 * <a href="http://www.keplerproject.org/luajava/">luajava</a> documentation pages.
 * <p>
 * <p>
 * Typically, this library is included as part of a call to
 * {@link luaj.lib.jse.JsePlatform#standardGlobals()}
 * <pre> {@code
 * Globals globals = JsePlatform.standardGlobals();
 * System.out.println( globals.get("luajava").get("bindClass").call( LuaValue.valueOf("java.lang.System") ).invokeMethod("currentTimeMillis") );
 * } </pre>
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link Globals#load} using code such as:
 * <pre> {@code
 * Globals globals = new Globals();
 * globals.load(new JseBaseLib());
 * globals.load(new PackageLib());
 * globals.load(new LuajavaLib());
 * globals.load(
 *      "sys = luajava.bindClass('java.lang.System')\n"+
 *      "print ( sys:currentTimeMillis() )\n", "main.lua" ).call();
 * } </pre>
 * <p>
 * <p>
 * The {@code luajava} library is available
 * on all JSE platforms via the call to {@link luaj.lib.jse.JsePlatform#standardGlobals()}
 * and the luajava api's are simply invoked from lua.
 * Because it makes extensive use of Java's reflection API, it is not available
 * on JME, but can be used in Android applications.
 * <p>
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 *
 * @see LibFunction
 * @see luaj.lib.jse.JsePlatform
 * @see LuaC
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 * @see <a href="http://www.keplerproject.org/luajava/manual.html#luareference">http://www.keplerproject.org/luajava/manual.html#luareference</a>
 */
@SuppressWarnings("rawtypes")
public class LuajavaLib extends VarArgFunction {

    static final int INIT = 0;
    static final int BINDCLASS = 1;
    static final int NEWINSTANCE = 2;
    static final int NEW = 3;
    static final int CREATEPROXY = 4;
    static final int LOADLIB = 5;
    static final int ASTABLE = 6;
    static final int INSTANCEOF = 7;

    static final String[] NAMES = {
            "bindClass",
            "newInstance",
            "new",
            "createProxy",
            "loadLib",
            "astable",
            "instanceof",
    };

    static final int METHOD_MODIFIERS_VARARGS = Modifier.TRANSIENT;//0x80;

    public LuajavaLib() {
    }

    public Varargs invoke(Varargs args) {
        try {
            switch (opcode) {
                case INIT: {
                    // LuaValue modname = args.arg1();
                    LuaValue env = args.arg(2);
                    Globals globals = env.checkglobals();
                    globals.luajavaLib = this;

                    LuaTable t = new LuaTable();
                    bind(t, this.getClass(), NAMES, BINDCLASS);
                    env.set("luajava", t);
                    env.get("package").get("loaded").set("luajava", t);
                    env.set("byte", JavaClass.forClass(Byte.TYPE));
                    env.set("char", JavaClass.forClass(Character.TYPE));
                    env.set("short", JavaClass.forClass(Short.TYPE));
                    env.set("int", JavaClass.forClass(Integer.TYPE));
                    env.set("long", JavaClass.forClass(Long.TYPE));
                    env.set("float", JavaClass.forClass(Float.TYPE));
                    env.set("double", JavaClass.forClass(Double.TYPE));
                    env.set("import", new VarArgFunction() {
                        public Varargs invoke(Varargs args) {
                            try {
                                String cls = args.checkjstring(1);
                                String n = cls.replaceFirst(".*?[$\\.]([^$\\.]*)$", "$1");
                                Class clazz = classForName(cls);
                                JavaClass ls = JavaClass.forClass(clazz);
                                env.set(n, ls);
                                return ls;
                            } catch (ClassNotFoundException e) {
                                
                                    e.printStackTrace();
                                throw new LuaError(e);
                            }
                        }
                    });
                    return t;
                }
                case BINDCLASS: {
                    final Class clazz = classForName(args.checkjstring(1));
                    return JavaClass.forClass(clazz);
                }
                case NEWINSTANCE:
                case NEW: {
                    // get constructor
                    final LuaValue c = args.checkvalue(1);
                    final Class clazz = (opcode == NEWINSTANCE ? classForName(c.tojstring()) : (Class) c.checkuserdata(Class.class));
                    final Varargs consargs = args.subargs(2);
                    return JavaClass.forClass(clazz).getConstructor().invoke(consargs);
                }

                case CREATEPROXY: {
                    final int niface = args.narg() - 1;
                    if (niface <= 0)
                        throw new LuaError("no interfaces");
                    final LuaValue lobj = args.checktable(niface + 1);

                    // get the interfaces
                    final Class[] ifaces = new Class[niface];
                    for (int i = 0; i < niface; i++)
                        ifaces[i] = classForName(args.checkjstring(i + 1));

                    // create the invocation handler
                    InvocationHandler handler = new ProxyInvocationHandler(lobj);

                    // create the proxy object
                    Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), ifaces, handler);

                    // return the proxy
                    return LuaValue.userdataOf(proxy);
                }
                case LOADLIB: {
                    // get constructor
                    String classname = args.checkjstring(1);
                    String methodname = args.checkjstring(2);
                    Class<?> clazz = classForName(classname);
                    Method method = clazz.getMethod(methodname);
                    Object result = method.invoke(clazz);
                    if (result instanceof LuaValue) {
                        return (LuaValue) result;
                    } else {
                        return NIL;
                    }
                }
                case ASTABLE:
                    if (args.istable(1))
                        return args.checktable(1);
                    return asTable(args.checkuserdata(1), args.optboolean(2, false));
                case INSTANCEOF:
                    Class cls = args.arg(2).touserdata(Class.class);
                    return LuaValue.valueOf(cls.isInstance(args.checkuserdata(1)));
                default:
                    throw new LuaError("not yet supported: " + this);
            }
        } catch (LuaError e) {
            throw e;
        } catch (InvocationTargetException ite) {
            throw new LuaError(ite.getTargetException());
        } catch (Exception e) {
            throw new LuaError(e);
        }
    }

    public static LuaValue asTable(Object obj, boolean dep) {
        if (dep) {
            return asTable(obj);
        }
        LuaTable tab = new LuaTable();
        if (obj.getClass().isArray()) {
            int n = Array.getLength(obj);
            for (int i = 0; i <= n - 1; i++) {
                tab.set(i + 1, CoerceJavaToLua.coerce(Array.get(obj, i)));
            }
        } else if (obj instanceof Collection) {
            Collection list = (Collection) obj;
            int i = 1;
            for (Object v : list) {
                tab.set(i++, CoerceJavaToLua.coerce(v));
            }
        } else if (obj instanceof Map) {
            Map map = (Map) obj;
            for (Object o : map.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                tab.set(CoerceJavaToLua.coerce(entry.getKey()), CoerceJavaToLua.coerce(entry.getValue()));
            }
        } else if (obj instanceof JSONObject) {
            JSONObject map = (JSONObject) obj;
            Iterator<String> keys = map.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try {
                    tab.set(k, CoerceJavaToLua.coerce(map.get(k)));
                } catch (JSONException e) {
                    
                        e.printStackTrace();
                }
            }
        } else if (obj instanceof JSONArray) {
            JSONArray map = (JSONArray) obj;
            int len = map.length();
            for (int i = 0; i < len; i++) {
                try {
                    tab.set(i, CoerceJavaToLua.coerce(map.get(i)));
                } catch (JSONException e) {
                    
                        e.printStackTrace();
                }
            }
        } else {
            return CoerceJavaToLua.coerce(obj);
        }
        return tab;
    }

    public static LuaValue asTable(Object obj) {
        LuaTable tab = new LuaTable();
        if (obj.getClass().isArray()) {
            int n = Array.getLength(obj);
            for (int i = 0; i <= n - 1; i++) {
                tab.set(i + 1, asTable(Array.get(obj, i)));
            }
        } else if (obj instanceof Collection) {
            Collection list = (Collection) obj;
            int i = 1;
            for (Object v : list) {
                tab.set(i++, asTable(v));
            }
        } else if (obj instanceof Map) {
            Map map = (Map) obj;
            for (Object o : map.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                tab.set(CoerceJavaToLua.coerce(entry.getKey()), asTable(entry.getValue()));
            }
        } else if (obj instanceof JSONObject) {
            JSONObject map = (JSONObject) obj;
            Iterator<String> keys = map.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try {
                    tab.set(k, asTable(map.get(k)));
                } catch (JSONException e) {
                    
                        e.printStackTrace();
                }
            }
        } else if (obj instanceof JSONArray) {
            JSONArray map = (JSONArray) obj;
            int len = map.length();
            for (int i = 0; i < len; i++) {
                try {
                    tab.set(i + 1, asTable(map.get(i)));
                } catch (JSONException e) {
                    
                        e.printStackTrace();
                }
            }
        } else {
            return CoerceJavaToLua.coerce(obj);
        }
        return tab;
    }

    public static LuaUserdata createProxy(Class clazz, LuaValue lobj) {
        // get the interfaces
        final Class[] ifaces = new Class[]{clazz};
        // create the invocation handler
        InvocationHandler handler = new ProxyInvocationHandler(lobj);

        // create the proxy object
        Object proxy = Proxy.newProxyInstance(clazz.getClassLoader(), ifaces, handler);

        // return the proxy
        return LuaValue.userdataOf(proxy);
    }

    // load classes using app loader to allow luaj to be used as an extension
    protected Class<?> classForName(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    public ArrayList<ClassLoader> classLoaders = new ArrayList<>();
    public HashMap<String, LuaValue> modules = new HashMap<>();

    public LuaValue bindClassForName(String name) throws ClassNotFoundException {
        try {
            return JavaClass.forName(name);
        } catch (Exception e) {

        }
        for (ClassLoader loader : classLoaders) {
            try {
                return JavaClass.forName(name, loader);
            } catch (Exception e) {

            }
        }
        throw new ClassNotFoundException(name);
    }

    public static LuaValue override(Class clazz, LuaValue arg) throws InstantiationException, IllegalAccessException {
        Class<?> cls = new LuaEnhancer(clazz).create(arg);
        return JavaClass.forClass(cls);
    }

    public static final class override extends OneArgFunction {

        private final Class mClass;

        public override(JavaClass javaClass) {
            mClass = javaClass.touserdata(Class.class);
        }

        @Override
        public LuaValue call(LuaValue arg) {
            try {
                return override(mClass, arg);
            } catch (Exception e) {
                
                    e.printStackTrace();
                throw new LuaError(e);
            }
        }
    }

    private static final class ProxyInvocationHandler implements InvocationHandler {
        private final LuaValue lobj;

        private ProxyInvocationHandler(LuaValue lobj) {
            this.lobj = lobj;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            LuaValue func;
            if (lobj.isfunction())
                func = lobj;
            else
                func = lobj.get(name);

            if (func.isnil())
                return CoerceLuaToJava.coerce(LuaValue.NIL, method.getReturnType());

            boolean isvarargs = ((method.getModifiers() & METHOD_MODIFIERS_VARARGS) != 0);
            int n = args != null ? args.length : 0;
            LuaValue[] v;
            if (isvarargs) {
                Object o = args[--n];
                int m = Array.getLength(o);
                v = new LuaValue[n + m];
                for (int i = 0; i < n; i++)
                    v[i] = CoerceJavaToLua.coerce(args[i]);
                for (int i = 0; i < m; i++)
                    v[i + n] = CoerceJavaToLua.coerce(Array.get(o, i));
            } else {
                v = new LuaValue[n];
                for (int i = 0; i < n; i++) {
                    v[i] = CoerceJavaToLua.coerce(args[i]);
                }
            }
            try {
                LuaValue result = func.invoke(v).arg1();
                return CoerceLuaToJava.coerce(result, method.getReturnType());
            } catch (Exception e) {

                return CoerceLuaToJava.coerce(LuaValue.NIL, method.getReturnType());
            }
        }
    }

}