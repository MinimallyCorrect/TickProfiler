package nallar.tickprofiler.util;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public class BlockInfo {
	public final int meta;
	public final Block block;
	public final String name;

	public BlockInfo(final Block block, final int meta) {
		this.meta = meta;
		this.block = block;
		Item item = block == null ? null : Item.getItemFromBlock(block);
		ItemStack itemType = item == null ? null : new ItemStack(item, 1, meta);
		String name = itemType == null ?
				(block == null ? "unknown" : block.getLocalizedName()) :
				item.getUnlocalizedName(itemType);
		String preTranslateName = "item." + name;
		String localizedName = StatCollector.translateToLocal(preTranslateName);
		//noinspection StringEquality
		if (localizedName != null && localizedName != preTranslateName) {
			name = localizedName;
		}
		this.name = name;
	}

	@Override
	public String toString() {
		return block.toString() + ':' + meta + ", " + name;
	}
}
