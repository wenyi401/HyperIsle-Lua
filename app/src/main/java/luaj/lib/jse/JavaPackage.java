package luaj.lib.jse;

import java.util.HashMap;

import luaj.LuaValue;

/**
 * Created by nirenr on 2020/1/2.
 */

public class JavaPackage extends LuaValue {
    private final String mName;
    private final HashMap<String, LuaValue> cache = new HashMap<>();

    public JavaPackage(String name) {
        mName = name;
    }

    @Override
    public LuaValue get(String key) {
        LuaValue ret = cache.get(key);
        if (ret != null) {
            return ret;
        }
        String name = mName + "." + key;
        try {
            ret = JavaClass.forClass(Class.forName(name));
        } catch (Exception e) {
            ret = new JavaPackage(name);
        }
        cache.put(key, ret);
        return ret;
    }

    @Override
    public LuaValue get(LuaValue key) {
        return get(key.tojstring());
    }

    @Override
    public int type() {
        return LuaValue.TUSERDATA;
    }

    @Override
    public String typename() {
        return "userdata";
    }

    @Override
    public String tojstring() {
        return "JavaPackage: " + mName;
    }
}
