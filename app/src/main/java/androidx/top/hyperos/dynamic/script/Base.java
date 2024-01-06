package androidx.top.hyperos.dynamic.script;

import luaj.LuaValue;
import luaj.lib.TwoArgFunction;
import luaj.lib.jse.CoerceJavaToLua;
import luaj.lib.jse.JavaPackage;
public class Base extends TwoArgFunction {
    @Override
    public LuaValue call(LuaValue mode, LuaValue env) {
        //env.set("Http", CoerceJavaToLua.coerce(Http.class));
        env.set("Ticker", CoerceJavaToLua.coerce(Ticker.class));
        env.set("android", new JavaPackage("android"));
        env.set("androidx", new JavaPackage("androidx"));
        env.set("java", new JavaPackage("java"));
        env.set("org", new JavaPackage("org"));
        return env;
    }
}
