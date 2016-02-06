package nallar.tickprofiler.minecraft;

import cpw.mods.fml.relauncher.CoreModManager;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import me.nallar.modpatcher.ModPatcher;

import java.util.*;


@IFMLLoadingPlugin.SortingIndex(1001)
public class CoreMod implements IFMLLoadingPlugin {
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
		ModPatcher.getPatcher().readPatchesFromXmlInputStream(CoreMod.class.getResourceAsStream("/profilinghook.xml"));
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
