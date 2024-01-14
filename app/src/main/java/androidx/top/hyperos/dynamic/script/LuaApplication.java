package androidx.top.hyperos.dynamic.script;

import android.app.Application;

import androidx.top.hyperos.dynamic.ext.CrashHandler;

public class LuaApplication extends Application {
    private static LuaApplication instance;

    public static LuaApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        CrashHandler.getInstance().init(this);
    }
}
