package androidx.top.hyperos.dynamic;

import androidx.top.hyperos.dynamic.ext.Config;
import androidx.top.hyperos.dynamic.ext.Tools;
import androidx.top.hyperos.dynamic.hook.XpBase;
import androidx.top.hyperos.dynamic.hook.XpConfig;
import androidx.top.hyperos.dynamic.script.function.print;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import luaj.Globals;
import luaj.LuaValue;
import luaj.lib.TwoArgFunction;
import luaj.lib.jse.CoerceJavaToLua;
import luaj.lib.jse.JsePlatform;

public class LuaHook implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {
    private static Globals globals;
    private static LoadPackageParam lpparam;
    public String luaMain = Tools.concat(XpConfig.luaDir, "main.lua");

    public static Class<?> findClass(String classname) {
        return getClass(lpparam, classname);
    }

    public static Class<?> getClass(LoadPackageParam lpparam, String classname) {
        try {
            return lpparam.classLoader.loadClass(classname);
        } catch (ClassNotFoundException e) {
            XposedBridge.log(e);
            e.printStackTrace();
            return null;
        }
    }

    public static XSharedPreferences getSharedPreferences(String key) {
        XSharedPreferences sp = new XSharedPreferences(Config.AppPackage, key);
        sp.makeWorldReadable();
        return sp;
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;
        this.lpparam = lpparam;
        if (packageName.equals(Config.AppPackage)) {
            XposedHelpers.findAndHookMethod(LuaActivity.class.getName(), lpparam.classLoader, "isModuleActivated", XC_MethodReplacement.returnConstant(true));
        }
        try {
            initGlobals();
            globals.loadfile(luaMain).call();
            callFunc("handleLoadPackage", this.lpparam);
        } catch (Exception e) {
            XposedBridge.log(Tools.concat("[ ISLE ] :", e.toString()));
        }
    }

    public Object callFunc(String name, Object... args) {
        try {
            LuaValue fun = globals.get(name);
            if (fun.isfunction()) {
                return fun.jcall(args);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private void initGlobals() {
        globals = JsePlatform.standardGlobals();
        globals.jset("this", this);
        globals.set("print", new print(globals));
        globals.set("ArgBuilder", CoerceJavaToLua.coerce(ArgBuilder.class));
        globals.set("XCMethodHook", new XCMethodHook());
        globals.load(new XpBase());
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam initPackageResourcesParam) throws Throwable {
        try {
            callFunc("handleInitPackageResources", this.lpparam);
        } catch (Exception e) {
            XposedBridge.log(Tools.concat("[ ISLE ] :", e.toString()));
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        try {
            callFunc("initZygote", this.lpparam);
        } catch (Exception e) {
            XposedBridge.log(Tools.concat("[ ISLE ] :", e.toString()));
        }
    }

    public static class ArgBuilder {
        private List<Object> paramList = new ArrayList<>();
        private XC_MethodHook methodHook;

        public ArgBuilder addParameterType(Object object) {
            paramList.add(object);
            return this;
        }

        public ArgBuilder setCallback(XC_MethodHook methodHook) {
            this.methodHook = methodHook;
            return this;
        }

        public Object[] toParams() {
            paramList.add(this.methodHook);
            return paramList.toArray();
        }
    }

    public static class XCMethodHook extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue arg1, LuaValue arg2) {
            return CoerceJavaToLua.coerce(new LuaXCMethodHook(arg1, arg2));
        }

        static class LuaXCMethodHook extends XC_MethodHook {
            private LuaValue beforeHookedMethod;
            private LuaValue afterHookedMethod;

            LuaXCMethodHook(LuaValue beforeHookedMethod, LuaValue afterHookedMethod) {
                this.afterHookedMethod = afterHookedMethod;
                this.beforeHookedMethod = beforeHookedMethod;
            }

            @Override
            public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                if (!this.beforeHookedMethod.isnil()) {
                    this.beforeHookedMethod.call(CoerceJavaToLua.coerce(param));
                }
            }

            @Override
            public void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (!this.afterHookedMethod.isnil()) {
                    this.afterHookedMethod.call(CoerceJavaToLua.coerce(param));
                }
            }
        }
    }
}
