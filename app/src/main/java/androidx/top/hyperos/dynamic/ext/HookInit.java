package androidx.top.hyperos.dynamic.ext;

import androidx.top.hyperos.dynamic.LuaActivity;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import luaj.Globals;
import luaj.lib.jse.JsePlatform;

public class HookInit implements IXposedHookLoadPackage {
    private Globals globals;
    private void init() {
        if (globals!=null) {
            globals = JsePlatform.standardGlobals();
        }
        globals.load("");
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        String packageName = lpparam.packageName;
        if (packageName.equals(Config.AppPackage)) {
            XposedHelpers.findAndHookMethod(LuaActivity.class.getName(), lpparam.classLoader, "isModuleActivated", XC_MethodReplacement.returnConstant(true));
        }
        if (packageName.equals(Config.SystemUiPackage)) {
            XposedInfo.setLoad(lpparam);
            XposedInfo.init();
        }
    }

}
