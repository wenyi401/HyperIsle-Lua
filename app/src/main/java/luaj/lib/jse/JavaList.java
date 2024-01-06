package luaj.lib.jse;

import luaj.LuaUserdata;
import luaj.LuaValue;
import luaj.Varargs;
import luaj.lib.OneArgFunction;

import java.lang.reflect.Array;
import java.util.List;

class JavaList extends LuaUserdata {

    private final List mList;

    private static final class LenFunction extends OneArgFunction {
        public LuaValue call(LuaValue u) {
            return LuaValue.valueOf(Array.getLength(((LuaUserdata)u).m_instance));
        }
    }

    static final LuaValue LENGTH = valueOf("length");
    static final LuaValue CLASS = valueOf("class");

    JavaList(Object instance) {
        super(instance);
        mList=(List)instance;
    }

    public Varargs next(LuaValue index) {
        int len = mList.size();
        int idx = index.isnil() ? 0 : index.toint()+1;
        if (idx>=len)
            return LuaValue.NIL;
        return LuaValue.varargsOf(new LuaValue[]{CoerceJavaToLua.coerce(idx),CoerceJavaToLua.coerce(mList.get(idx))});
    }

    public LuaValue get(LuaValue key) {
        if ( key.equals(LENGTH) )
            return valueOf(Array.getLength(m_instance));
        if ( key.equals(CLASS) )
            return CoerceJavaToLua.coerce(m_instance.getClass());
        if ( key.isint() ) {
            int i = key.toint();
            return i>=0 && i<mList.size()?
                    CoerceJavaToLua.coerce(mList.get(i)):
                    NIL;
        }
        return super.get(key);
    }

    public void set(LuaValue key, LuaValue value) {
        if ( key.isint() ) {
            int i = key.toint();
            if ( i>=0 && i<mList.size() )
                mList.set(i,CoerceLuaToJava.coerce(value, Object.class));
            else if ( m_metatable==null || metatag(NEWINDEX).isnil() && !settable(this,key,value) )
                error("list index out of bounds");
        }
        else
            super.set(key, value);
    }
}
