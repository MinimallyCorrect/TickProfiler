package nallar.tickprofiler.util;

import net.minecraftforge.fml.common.Loader;

public enum VersionUtil {
	;

	public static String versionNumber() {
		return Loader.instance().getIndexedModList().get("TickProfiler").getVersion();
	}
}
