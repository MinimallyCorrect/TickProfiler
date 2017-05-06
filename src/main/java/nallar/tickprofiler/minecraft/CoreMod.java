package nallar.tickprofiler.minecraft;

import me.nallar.modpatcher.ModPatcher;
import nallar.tickprofiler.Log;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.*;


@IFMLLoadingPlugin.MCVersion("@MC_VERSION@")
@IFMLLoadingPlugin.SortingIndex(1001)
public class CoreMod implements IFMLLoadingPlugin {
	static {
		ModPatcher.requireVersion("1.11.2.12");
	}

	@Override
	public String[] getASMTransformerClass() {
		return new String[0];
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return ModPatcher.getSetupClass();
	}

	@Override
	public void injectData(Map<String, Object> data) {
		Log.info("TickProfiler v@MOD_VERSION@ coremod injectData called, loading patches");

		ModPatcher.loadPatches(CoreMod.class.getResourceAsStream("/entityhook.xml"));
		// TODO: Not implemented
		// ModPatcher.loadPatches(CoreMod.class.getResourceAsStream("/packethook.xml"));
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
