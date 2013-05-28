package me.nallar.tickprofiler.util;

public class UnsafeUtil {
	public static RuntimeException throwIgnoreChecked(Throwable t) {
		throw UnsafeUtil.<RuntimeException>throwIgnoreCheckedErasure(t);
	}

	private static <T extends Throwable> T throwIgnoreCheckedErasure(Throwable toThrow) throws T {
		throw (T) toThrow;
	}
}
