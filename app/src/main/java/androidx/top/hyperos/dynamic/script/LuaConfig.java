package androidx.top.hyperos.dynamic.script;

import androidx.top.hyperos.dynamic.ext.Config;
import androidx.top.hyperos.dynamic.ext.Tools;

public class LuaConfig {
    public static String luaDir = Tools.concat(Config.AppDir, "project/");
    public static String[] luaLabels = new String[]{"label", "app_name", "title"};
    public static String luaVersion = "lua_version";
    public static String[] luaModules = new String[]{"lua_modules", "modules"};
    public static String[] luaThemes = new String[]{"theme", "style"};

}
