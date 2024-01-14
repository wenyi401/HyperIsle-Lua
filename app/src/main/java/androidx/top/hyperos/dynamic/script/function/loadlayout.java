package androidx.top.hyperos.dynamic.script.function;

import androidx.top.hyperos.dynamic.script.LuaContext;
import androidx.top.hyperos.dynamic.script.LuaLayout;

import luaj.Globals;
import luaj.Varargs;
import luaj.lib.VarArgFunction;

public class loadlayout extends VarArgFunction {
    private LuaContext mContext;
    private Globals mGlobals;
    public loadlayout(LuaContext context) {
        this.mContext = context;
        this.mGlobals = this.mContext.getGlobals();
    }

    @Override
    public Varargs invoke(Varargs args) {
        if (args.narg() == 1) {
            return new LuaLayout(mContext.getContext()).load(args.arg1(), mGlobals);
        }
        return new LuaLayout(mContext.getContext()).load(args.arg1(), args.arg(2).checktable());
    }
}
