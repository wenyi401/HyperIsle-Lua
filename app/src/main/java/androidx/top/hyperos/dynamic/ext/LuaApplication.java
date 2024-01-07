package androidx.top.hyperos.dynamic.ext;

import android.app.Application;

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
