package me.nallar.tickprofiler.util.contextaccess;

public class ContextAccessReflection implements ContextAccess {
	@Override
	public Class getContext(int depth) {
		
		final StackTraceElement callingMethod = Thread.currentThread()
				.getStackTrace()[depth + 2];
		try {
			return Class.forName(callingMethod.getClassName());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
}
