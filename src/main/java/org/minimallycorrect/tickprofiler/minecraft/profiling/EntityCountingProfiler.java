package org.minimallycorrect.tickprofiler.minecraft.profiling;

import java.util.*;

import lombok.val;

import org.minimallycorrect.tickprofiler.util.CollectionsUtil;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class EntityCountingProfiler extends Profile {
	private static <T> void increment(HashMap<T, Integer> counts, T t) {
		val current = counts.get(t);
		counts.put(t, current == null ? 1 : current + 1);
	}

	@Override
	public void start() {
		val elements = parameters.getInt("elements");
		if (elements <= 0)
			throw new IllegalArgumentException("elements must be > 0");
		val entityCounts = new HashMap<Class<?>, Integer>();
		val tileEntityCounts = new HashMap<Class<?>, Integer>();
		val worlds = parameters.getString("worlds").toLowerCase();
		val worldList = new ArrayList<WorldServer>();
		if (worlds.equals("all")) {
			Collections.addAll(worldList, DimensionManager.getWorlds());
			worldList.removeIf(Objects::isNull);// #100 null world in world list?
		} else {
			// TODO: handle multiple entries, split by ','
			worldList.add(DimensionManager.getWorld(Integer.parseInt(worlds)));
		}
		for (val world : worldList) {
			for (val entity : world.loadedEntityList)
				increment(entityCounts, entity.getClass());
			for (val entity : world.loadedTileEntityList)
				increment(tileEntityCounts, entity.getClass());
		}
		targets.forEach(it -> {
			val tf = it.getTableFormatter();
			tf.heading("Class").heading("Count");
			for (val key : CollectionsUtil.sortedKeys(entityCounts, elements))
				tf.row(key.getSimpleName()).row(entityCounts.get(key));
			tf.finishTable();
			tf.sb.append('\n');
			tf.heading("Class").heading("Count");
			for (val key : CollectionsUtil.sortedKeys(tileEntityCounts, elements))
				tf.row(key.getSimpleName()).row(tileEntityCounts.get(key));
			tf.finishTable();
			it.sendTables(tf);
		});
	}
}
