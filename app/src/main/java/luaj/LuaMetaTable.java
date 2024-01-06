package luaj;

public interface LuaMetaTable {
    //public LuaValue __call(LuaValue args);
    public void __newindex(LuaValue key,LuaValue value);
    public LuaValue __index(LuaValue key);

}
