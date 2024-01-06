package luaj;

import android.cglib.proxy.Enhancer;
import android.cglib.proxy.EnhancerInterface;
import android.cglib.proxy.MethodFilter;
import android.cglib.proxy.MethodInterceptor;

import androidx.top.hyperos.dynamic.ext.Tools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by nirenr on 2018/12/19.
 */

public class LuaEnhancer {

    private Enhancer mEnhancer;

    public LuaEnhancer(String cls) throws ClassNotFoundException {
        this(Class.forName(cls));
    }

    public LuaEnhancer(Class<?> cls) {
        mEnhancer = new Enhancer(Tools.getContext());
        mEnhancer.setSuperclass(cls);
    }

    public void setInterceptor(EnhancerInterface obj, MethodInterceptor interceptor) {
        obj.setMethodInterceptor_Enhancer(interceptor);
    }

    public static void setInterceptor(Class obj, MethodInterceptor interceptor) {
        try {
            Field field = obj.getDeclaredField("methodInterceptor");
            field.setAccessible(true);
            field.set(obj, interceptor);

        } catch (Exception e) {
            
                e.printStackTrace();
        }
    }

    public Class<?> create() {
        try {
            return mEnhancer.create();
        } catch (Exception e) {
            
                e.printStackTrace();
        }
        return null;
    }

    public Class<?> create(MethodFilter filer) {
        try {
            mEnhancer.setMethodFilter(filer);
            return mEnhancer.create();
        } catch (Exception e) {
            
                e.printStackTrace();
        }
        return null;
    }

    public Class<?> create(LuaValue arg) {
        MethodFilter filter = new MethodFilter() {
            @Override
            public boolean filter(Method method, String name) {
                if (!arg.get(name).isnil())
                    return true;
                return false;
            }
        };
        try {
            mEnhancer.setMethodFilter(filter);
            Class<?> cls = mEnhancer.create();
            setInterceptor(cls, new LuaMethodInterceptor(arg));
            return cls;
        } catch (Exception e) {
            
                e.printStackTrace();
        }
        return null;
    }
}
