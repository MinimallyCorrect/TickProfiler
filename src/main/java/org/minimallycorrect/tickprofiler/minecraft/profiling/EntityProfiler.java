package org.minimallycorrect.tickprofiler.minecraft.profiling;

import lombok.EqualsAndHashCode;
import lombok.val;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import org.minimallycorrect.modpatcher.api.UsedByPatch;
import org.minimallycorrect.tickprofiler.Log;
import org.minimallycorrect.tickprofiler.minecraft.TickProfiler;
import org.minimallycorrect.tickprofiler.util.CollectionsUtil;
import org.minimallycorrect.tickprofiler.util.TableFormatter;

import java.util.*;
import java.util.concurrent.atomic.*;

public class EntityProfiler extends Profile {
	private static final AtomicBoolean running = new AtomicBoolean();
	private static final HashMap<Class<?>, AtomicInteger> invocationCount = new HashMap<>();
	private static final HashMap<Class<?>, AtomicLong> time = new HashMap<>();
	private static final HashMap<Object, AtomicLong> singleTime = new HashMap<>();
	private static final HashMap<Object, AtomicLong> singleInvocationCount = new HashMap<>();
	private static final AtomicLong totalTime = new AtomicLong();
	private static long startTick;
	private static long startTime;

	private static int getDimension(TileEntity o) {
		//noinspection ConstantConditions
		if (o.getWorld() == null)
			return -999;
		WorldProvider worldProvider = o.getWorld().provider;
		return worldProvider == null ? -999 : worldProvider.getDimension();
	}

	private static int getDimension(Entity o) {
		if (o.world == null)
			return -999;
		WorldProvider worldProvider = o.world.provider;
		return worldProvider == null ? -999 : worldProvider.getDimension();
	}

	private static Object niceName(Object o) {
		if (o instanceof TileEntity) {
			return niceName(o.getClass()) + ' ' + Log.toString(((TileEntity) o).getPos()) + ':' + getDimension((TileEntity) o);
		} else if (o instanceof Entity) {
			return niceName(o.getClass()) + ' ' + (int) ((Entity) o).posX + ',' + (int) ((Entity) o).posY + ',' + (int) ((Entity) o).posZ + ':' + getDimension((Entity) o);
		}
		return o.toString().substring(0, 48);
	}

	private static String niceName(Class<?> clazz) {
		String name = clazz.getName();
		if (name.contains(".")) {
			String cName = name.substring(name.lastIndexOf('.') + 1);
			String pName = name.substring(0, name.lastIndexOf('.'));
			if (pName.contains(".")) {
				pName = pName.substring(pName.lastIndexOf('.') + 1);
			}
			return (cName.length() < 15 ? pName + '.' : "") + cName;
		}
		return name;
	}

	@UsedByPatch("entityhook.xml")
	public static void record(Object o, long time) {
		if (time < 0) {
			time = 0;
		}
		getSingleTime(o).addAndGet(time);
		getSingleInvocationCount(o).incrementAndGet();
		Class<?> clazz = o.getClass();
		getTime(clazz).addAndGet(time);
		getInvocationCount(clazz).incrementAndGet();
		totalTime.addAndGet(time);
	}

	private static void writeStringData(TableFormatter tf, int elements) {
		long timeProfiled = System.currentTimeMillis() - startTime;
		float tps = (TickProfiler.tickCount - startTick) * 1000f / timeProfiled;
		tf.sb.append("TPS: ").append(tps).append('\n').append(tf.tableSeparator);
		writeData(tf, elements);
	}

	private static void writeData(TableFormatter tf, int elements) {
		Map<Class<?>, Long> time = new HashMap<>();
		synchronized (EntityProfiler.time) {
			for (Map.Entry<Class<?>, AtomicLong> entry : EntityProfiler.time.entrySet()) {
				time.put(entry.getKey(), entry.getValue().get());
			}
		}
		Map<Object, Long> singleTime = new HashMap<>();
		synchronized (EntityProfiler.singleTime) {
			for (Map.Entry<Object, AtomicLong> entry : EntityProfiler.singleTime.entrySet()) {
				singleTime.put(entry.getKey(), entry.getValue().get());
			}
		}
		double totalTime = EntityProfiler.totalTime.get();
		tf
			.heading("Single Entity")
			.heading("Time/Tick")
			.heading("%");
		final List<Object> sortedSingleKeysByTime = CollectionsUtil.sortedKeys(singleTime, elements);
		for (Object o : sortedSingleKeysByTime) {
			tf
				.row(niceName(o))
				.row(singleTime.get(o) / (1000000d * singleInvocationCount.get(o).get()))
				.row((singleTime.get(o) / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		final Map<ChunkCoords, ComparableLongHolder> chunkTimeMap = new HashMap<ChunkCoords, ComparableLongHolder>() {
			@Override
			public ComparableLongHolder get(Object key_) {
				ChunkCoords key = (ChunkCoords) key_;
				ComparableLongHolder value = super.get(key);
				if (value == null) {
					value = new ComparableLongHolder();
					put(key, value);
				}
				return value;
			}
		};
		val ticks = TickProfiler.tickCount - startTick;
		for (final Map.Entry<Object, Long> singleTimeEntry : singleTime.entrySet()) {
			int x;
			int z;
			int dimension;
			Object o = singleTimeEntry.getKey();
			if (o instanceof Entity) {
				x = ((Entity) o).chunkCoordX;
				z = ((Entity) o).chunkCoordZ;
				dimension = getDimension((Entity) o);
			} else if (o instanceof TileEntity) {
				x = ((TileEntity) o).getPos().getX() >> 4;
				z = ((TileEntity) o).getPos().getZ() >> 4;
				dimension = getDimension((TileEntity) o);
			} else {
				throw new RuntimeException("Wrong block: " + o.getClass());
			}
			if (x != Integer.MIN_VALUE) {
				chunkTimeMap.get(new ChunkCoords(x, z, dimension)).value += singleTimeEntry.getValue();
			}
		}
		tf
			.heading("Chunk")
			.heading("Time/Tick")
			.heading("%");
		for (ChunkCoords chunkCoords : CollectionsUtil.sortedKeys(chunkTimeMap, elements)) {
			long chunkTime = chunkTimeMap.get(chunkCoords).value;
			tf
				.row(chunkCoords.dimension + ": " + chunkCoords.chunkXPos + ", " + chunkCoords.chunkZPos)
				.row(chunkTime / (1000000d * ticks))
				.row((chunkTime / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
			.heading("All Entities of Type")
			.heading("Time/Tick")
			.heading("%");
		for (Class<?> c : CollectionsUtil.sortedKeys(time, elements)) {
			tf
				.row(niceName(c))
				.row(time.get(c) / (1000000d * ticks))
				.row((time.get(c) / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		Map<Class<?>, Long> timePerTick = new HashMap<>();
		for (Map.Entry<Class<?>, AtomicLong> entry : EntityProfiler.time.entrySet()) {
			timePerTick.put(entry.getKey(), entry.getValue().get() / invocationCount.get(entry.getKey()).get());
		}
		tf
			.heading("Average Entity of Type")
			.heading("Time/tick")
			.heading("Calls");
		for (Class<?> c : CollectionsUtil.sortedKeys(timePerTick, elements)) {
			tf
				.row(niceName(c))
				.row(timePerTick.get(c) / 1000000d)
				.row(invocationCount.get(c));
		}
		tf.finishTable();
	}

	private static AtomicLong getSingleInvocationCount(Object o) {
		AtomicLong t = singleInvocationCount.get(o);
		if (t == null) {
			t = singleInvocationCount.computeIfAbsent(o, k -> new AtomicLong());
		}
		return t;
	}

	private static AtomicInteger getInvocationCount(Class<?> clazz) {
		AtomicInteger i = invocationCount.get(clazz);
		if (i == null) {
			i = invocationCount.computeIfAbsent(clazz, k -> new AtomicInteger());
		}
		return i;
	}

	private static AtomicLong getSingleTime(Object o) {
		return getTime(o, singleTime);
	}

	private static AtomicLong getTime(Class<?> clazz) {
		return getTime(clazz, time);
	}

	@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
	private static <T> AtomicLong getTime(T clazz, HashMap<T, AtomicLong> time) {
		AtomicLong t = time.get(clazz);
		if (t == null) {
			t = time.get(clazz);
			synchronized (time) {
				if (t == null) {
					t = new AtomicLong();
					time.put(clazz, t);
				}
			}
		}
		return t;
	}

	@Override
	protected AtomicBoolean getRunning() {
		return running;
	}

	@Override
	public void start() {
		val elements = parameters.getInt("elements");
		if (elements <= 0)
			throw new IllegalArgumentException("elements must be > 0");
		val worlds = parameters.getString("worlds").toLowerCase();
		val worldList = new ArrayList<WorldServer>();
		if (worlds.equals("all")) {
			Collections.addAll(worldList, DimensionManager.getWorlds());
		} else {
			// TODO: handle multiple entries, split by ','
			worldList.add(DimensionManager.getWorld(Integer.parseInt(worlds)));
		}
		start(() -> {
			for (World world_ : worldList) {
				TickProfiler.instance.hookProfiler(world_);
			}
			startTick = TickProfiler.tickCount;
			startTime = System.currentTimeMillis();
		}, () -> targets.forEach(it -> {
			val tf = it.getTableFormatter();
			writeStringData(tf, elements);
			it.sendTables(tf);
		}), () -> {
			for (World world_ : worldList) {
				TickProfiler.instance.unhookProfiler(world_);
			}
			clear();
		});
	}

	private void clear() {
		invocationCount.clear();
		synchronized (time) {
			time.clear();
		}
		totalTime.set(0);
		synchronized (singleTime) {
			singleTime.clear();
		}
		singleInvocationCount.clear();
	}

	private static final class ChunkCoords {
		final int chunkXPos;
		final int chunkZPos;
		final int dimension;

		ChunkCoords(final int chunkXPos, final int chunkZPos, final int dimension) {
			this.chunkXPos = chunkXPos;
			this.chunkZPos = chunkZPos;
			this.dimension = dimension;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ChunkCoords && ((ChunkCoords) o).chunkXPos == this.chunkXPos && ((ChunkCoords) o).chunkZPos == this.chunkZPos && ((ChunkCoords) o).dimension == this.dimension;
		}

		@Override
		public int hashCode() {
			return (chunkXPos << 16) ^ (chunkZPos << 4) ^ dimension;
		}
	}

	@EqualsAndHashCode
	private static class ComparableLongHolder implements Comparable<ComparableLongHolder> {
		long value;

		ComparableLongHolder() {}

		@Override
		public int compareTo(final ComparableLongHolder comparableLongHolder) {
			return Long.compare(value, comparableLongHolder.value);
		}
	}
}
