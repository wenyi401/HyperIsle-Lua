package android.cglib.proxy;

import java.lang.reflect.Method;

public class MethodProxy {
	
	private Class subClass;
	private String methodName;
	private Class[] argsType;
	
	@SuppressWarnings("rawtypes")
	public MethodProxy(Class subClass, String methodName, Class[] argsType) {
		this.subClass = subClass;
		this.methodName = methodName;
		this.argsType = argsType;
	}
	
	public String getMethodName() {
		return methodName;
	}
	
	@SuppressWarnings("unchecked")
	public Method getOriginalMethod() {
		try {
			return subClass.getMethod(methodName, argsType);
		} catch (NoSuchMethodException e) {
			try {
				return subClass.getDeclaredMethod(methodName, argsType);
			} catch (NoSuchMethodException e2) {
				throw new ProxyException(e2.getMessage());
			}
		}
	}
	@SuppressWarnings("unchecked")
	public Method getSuperMethod() {
		try {
			return subClass.getSuperclass().getMethod(methodName, argsType);
		} catch (NoSuchMethodException e) {
			try {
				return subClass.getSuperclass().getDeclaredMethod(methodName, argsType);
			} catch (NoSuchMethodException e2) {
				throw new ProxyException(e2.getMessage());
			}
		}
	}
	@SuppressWarnings("unchecked")
	public Method getProxyMethod() {
		try {
			return subClass.getMethod(methodName + Const.SUBCLASS_INVOKE_SUPER_SUFFIX, argsType);
		} catch (NoSuchMethodException e) {
			throw new ProxyException(e.getMessage());
		}
	}
	
	public Object invokeSuper(Object object, Object[] argsValue) {
		return ((EnhancerInterface) object).executeSuperMethod_Enhancer(methodName, argsType, argsValue);
	}

	@Override
	public String toString() {
		return "MethodProxy{" +
				getSuperMethod()+
				'}';
	}
}
