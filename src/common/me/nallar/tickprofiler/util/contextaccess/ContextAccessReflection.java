package me.nallar.tickprofiler.util.contextaccess;

public class ContextAccessReflection implements ContextAccess {
	@Override
	public Class getContext(int depth) {
		return sun.reflect.Reflection.getCallerClass(depth + 2);
	}
}
