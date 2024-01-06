package androidx.top.hyperos.dynamic.script.function;

import androidx.top.hyperos.dynamic.LuaActivity;

import luaj.Globals;
import luaj.LuaValue;
import luaj.Varargs;
import luaj.lib.VarArgFunction;

public class print extends VarArgFunction {

    private Globals globals;

    public print(Globals globals) {
        this.globals = globals;
    }

    public Varargs invoke(Varargs args) {
        LuaValue tostring = globals.get("tostring");
        StringBuilder buf = new StringBuilder();
        for (int i = 1, n = args.narg(); i <= n; i++) {
            buf.append((tostring.call(args.arg(i)).tojstring()));
            if (i < n)
                buf.append("    ");
        }
        LuaActivity.sendMsg(buf.toString());
        return NONE;
    }
}

