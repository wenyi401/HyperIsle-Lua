package androidx.top.hyperos.dynamic.script;

import android.content.Context;

import luaj.Globals;
import luaj.lib.ResourceFinder;

public interface LuaContext extends ResourceFinder {
    public Context getContext();

    public Globals getGlobals();

    public String getLuaFile();

    public String getLuaFile(String filename);

    public String getLuaDir();

    public Object doFile(String path, Object... args);

    public int getHeight();

    public int getWidth();

    public void call(String func, Object... args);

    public void set(String name, Object value);
}
