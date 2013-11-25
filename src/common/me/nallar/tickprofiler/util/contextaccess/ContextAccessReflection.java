package me.nallar.tickprofiler.util.contextaccess;

public class ContextAccessReflection implements ContextAccess {
	@Override
	public Class getContext(int depth) {
		// Broken on newer JDKs, automatically falls back to security manager implementation when this doesn't work.
		//noinspection deprecation
		return sun.reflect.Reflection.getCallerClass(depth + 2);
	}
}
