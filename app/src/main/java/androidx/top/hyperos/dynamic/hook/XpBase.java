package androidx.top.hyperos.dynamic.hook;

import de.robv.android.xposed.SELinuxHelper;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import luaj.LuaValue;
import luaj.lib.TwoArgFunction;
import luaj.lib.jse.CoerceJavaToLua;

public class XpBase extends TwoArgFunction {
    // 适配
    static final String[] xposedHelpers = {
            "XposedHelpers",
            "xp",
            "XposedHelper"
    };

    @Override
    public LuaValue call(LuaValue mode, LuaValue env) {
        for (String key : xposedHelpers) {
            env.set(key, CoerceJavaToLua.coerce(XposedHelpers.class));
        }
        env.set("XposedHelper", CoerceJavaToLua.coerce(XposedHelpers.class));
        env.set("SELinuxHelper", CoerceJavaToLua.coerce(SELinuxHelper.class));
        env.set("XSharedPreferences", CoerceJavaToLua.coerce(XSharedPreferences.class));
        return env;
    }
}