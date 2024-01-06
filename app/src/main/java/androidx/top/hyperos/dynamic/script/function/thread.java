package androidx.top.hyperos.dynamic.script.function;

import androidx.top.hyperos.dynamic.LuaActivity;
import luaj.LuaValue;
import luaj.Varargs;
import luaj.lib.VarArgFunction;
import luaj.lib.jse.CoerceJavaToLua;

public class thread extends VarArgFunction {
    private final LuaActivity mCotext;

    public thread(LuaActivity context) {
        this.mCotext = context;
    }

    public Varargs invoke(Varargs args) {
        LuaValue arg = args.arg1();
        int n = args.narg() - 1;
        LuaValue[] as = new LuaValue[n];
        for (int i = 0; i < n; i++) {
            as[i] = args.arg(i + 2);
        }
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    arg.invoke(as);
                } catch (Exception e) {
                    mCotext.sendMsg(e.toString());
                }
            }
        };
        thread.start();
        return CoerceJavaToLua.coerce(thread);
    }
}
