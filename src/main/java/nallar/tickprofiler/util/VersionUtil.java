package nallar.tickprofiler.util;

import cpw.mods.fml.common.Loader;

public enum VersionUtil {
	;

	public static String versionNumber() {
		return Loader.instance().getIndexedModList().get("TickProfiler").getVersion();
	}
}
