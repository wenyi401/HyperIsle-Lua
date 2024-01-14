package androidx.top.hyperos.dynamic.script.lib;

import luaj.Globals;
import luaj.LuaTable;
import luaj.LuaValue;
import luaj.lib.TwoArgFunction;

public class HttpLib extends TwoArgFunction {
    private static final String[] NAMES = {
            "makeRequest",
            "get",
            "post"
    };
    private Globals globals;

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        globals = env.getGlobals().checkglobals();
        LuaTable http = new LuaTable();

        return null;
    }
}
