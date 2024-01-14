package androidx.top.hyperos.dynamic.script;

import luaj.LuaValue;
import luaj.lib.TwoArgFunction;
import luaj.lib.jse.CoerceJavaToLua;
public class Base extends TwoArgFunction {
    @Override
    public LuaValue call(LuaValue mode, LuaValue env) {
        //env.set("Http", CoerceJavaToLua.coerce(Http.class));
        env.set("Ticker", CoerceJavaToLua.coerce(Ticker.class));
        return env;
    }
}
