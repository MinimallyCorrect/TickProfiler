package nallar.tickprofiler.minecraft.profiling;

import com.google.common.base.Functions;
import com.google.common.base.Throwables;
import com.google.common.collect.Ordering;
import cpw.mods.fml.common.FMLLog;
import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.TickProfiler;
import nallar.tickprofiler.minecraft.commands.ProfileCommand;
import nallar.tickprofiler.util.CollectionsUtil;
import nallar.tickprofiler.util.TableFormatter;
import nallar.tickprofiler.util.stringfillers.StringFiller;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeModContainer;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class EntityTickProfiler {
	private static final Method isActiveChunk = getIsActiveChunkMethod();

	private static Method getIsActiveChunkMethod() {
		Class<World> clazz = World.class;
		try {
			Method method = clazz.getDeclaredMethod("isActiveChunk", int.class, int.class);
			Log.info("Found Cauldron isActiveChunk method " + method);
			return method;
		} catch (NoSuchMethodException e) {
			Log.warn("Did not find Cauldron isActiveChunk method, assuming vanilla entity ticking.");
		}
		return null;
	}

	private int lastCX = Integer.MIN_VALUE;
	private int lastCZ = Integer.MIN_VALUE;
	private boolean cachedActive = false;
	public static final EntityTickProfiler ENTITY_TICK_PROFILER = new EntityTickProfiler();
	public static ProfileCommand.ProfilingState profilingState = ProfileCommand.ProfilingState.NONE;
	private final HashMap<Class<?>, AtomicInteger> invocationCount = new HashMap<Class<?>, AtomicInteger>();
	private final HashMap<Class<?>, AtomicLong> time = new HashMap<Class<?>, AtomicLong>();
	private final HashMap<Object, AtomicLong> singleTime = new HashMap<Object, AtomicLong>();
	private final HashMap<Object, AtomicLong> singleInvocationCount = new HashMap<Object, AtomicLong>();
	private int ticks;
	private final AtomicLong totalTime = new AtomicLong();
	private volatile int chunkX;
	private volatile int chunkZ;
	private volatile long startTime;

	private EntityTickProfiler() {
	}

	public static synchronized boolean startProfiling(ProfileCommand.ProfilingState profilingState_) {
		if (profilingState != ProfileCommand.ProfilingState.NONE) {
			return false;
		}
		profilingState = profilingState_;
		return true;
	}

	public static synchronized void endProfiling() {
		profilingState = ProfileCommand.ProfilingState.NONE;
	}

	public void setLocation(final int x, final int z) {
		chunkX = x;
		chunkZ = z;
	}

	public boolean startProfiling(final Runnable runnable, ProfileCommand.ProfilingState state, final int time, final Collection<World> worlds_) {
		if (time <= 0) {
			throw new IllegalArgumentException("time must be > 0");
		}
		final Collection<World> worlds = new ArrayList<World>(worlds_);
		synchronized (EntityTickProfiler.class) {
			if (!startProfiling(state)) {
				return false;
			}
			for (World world_ : worlds) {
				TickProfiler.instance.hookProfiler(world_);
			}
		}

		Runnable profilingRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000 * time);
				} catch (InterruptedException ignored) {
				}

				synchronized (EntityTickProfiler.class) {
					endProfiling();
					for (World world_ : worlds) {
						TickProfiler.instance.unhookProfiler(world_);
					}
					runnable.run();
					clear();
				}
			}
		};
		Thread profilingThread = new Thread(profilingRunnable);
		profilingThread.setName("TickProfiler");
		profilingThread.start();
		startTime = System.currentTimeMillis();
		return true;
	}

	public void runEntities(World world, Queue<Entity> toTick) {
		long end = System.nanoTime();
		long start;
		boolean isGlobal = profilingState == ProfileCommand.ProfilingState.GLOBAL;
		Iterator<Entity> iter = toTick.iterator();
		while (iter.hasNext()) {
			Entity entity = iter.next();

			start = end;
			if (entity.ridingEntity != null) {
				if (!entity.ridingEntity.isDead && entity.ridingEntity.riddenByEntity == entity) {
					continue;
				}

				entity.ridingEntity.riddenByEntity = null;
				entity.ridingEntity = null;
			}

			if (!entity.isDead) {
				try {
					world.updateEntity(entity);
				} catch (Throwable var8) {
					CrashReport crashReport = CrashReport.makeCrashReport(var8, "Ticking entity");
					CrashReportCategory crashReportCategory = crashReport.makeCategory("Entity being ticked");
					entity.addEntityCrashInfo(crashReportCategory);

					if (ForgeModContainer.removeErroringEntities) {
						FMLLog.severe(crashReport.getCompleteReport());
						world.removeEntity(entity);
					} else {
						throw new ReportedException(crashReport);
					}
				}
			}

			if (entity.isDead) {
				int chunkX = entity.chunkCoordX;
				int chunkZ = entity.chunkCoordZ;

				if (entity.addedToChunk && world.getChunkProvider().chunkExists(chunkX, chunkZ)) {
					world.getChunkFromChunkCoords(chunkX, chunkZ).removeEntity(entity);
				}

				iter.remove();

				world.onEntityRemoved(entity);
			}
			end = System.nanoTime();

			if (isGlobal || (entity.chunkCoordX == chunkX && entity.chunkCoordZ == chunkZ)) {
				record(entity, end - start);
			}
		}
	}

	public void runTileEntities(World world, Iterable<TileEntity> toTick) {
		IChunkProvider chunkProvider = world.getChunkProvider();
		Iterator<TileEntity> iterator = toTick.iterator();
		long end = System.nanoTime();
		long start;
		boolean isGlobal = profilingState == ProfileCommand.ProfilingState.GLOBAL;
		while (iterator.hasNext()) {
			start = end;
			TileEntity tileEntity = iterator.next();

			int x = tileEntity.xCoord;
			int z = tileEntity.zCoord;

			if (!isActiveChunk(world, x >> 4, z >> 4)) {
				continue;
			}

			if (!tileEntity.isInvalid() && tileEntity.hasWorldObj() && chunkProvider.chunkExists(x >> 4, z >> 4)) {
				try {
					tileEntity.updateEntity();
				} catch (Throwable var6) {
					CrashReport crashReport = CrashReport.makeCrashReport(var6, "Ticking tile entity");
					CrashReportCategory crashReportCategory = crashReport.makeCategory("Tile entity being ticked");
					tileEntity.func_145828_a(crashReportCategory);
					if (ForgeModContainer.removeErroringTileEntities) {
						FMLLog.severe(crashReport.getCompleteReport());
						tileEntity.invalidate();
						world.setBlockToAir(x, tileEntity.yCoord, z);
					} else {
						throw new ReportedException(crashReport);
					}
				}
			}

			if (tileEntity.isInvalid()) {
				iterator.remove();

				if (chunkProvider.chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
					Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);

					if (chunk != null) {
						chunk.removeTileEntity(tileEntity.xCoord & 15, tileEntity.yCoord, tileEntity.zCoord & 15);
					}
				}
			}
			end = System.nanoTime();
			if (isGlobal || (x >> 4 == chunkX && z >> 4 == chunkZ)) {
				record(tileEntity, end - start);
			}
		}
	}

	public void record(Object o, long time) {
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

	public void clear() {
		invocationCount.clear();
		synchronized (time) {
			time.clear();
		}
		totalTime.set(0);
		synchronized (singleTime) {
			singleTime.clear();
		}
		singleInvocationCount.clear();
		synchronized (this) {
			ticks = 0;
		}
	}

	public synchronized void tick() {
		if (profilingState != ProfileCommand.ProfilingState.NONE) {
			ticks++;
		}
	}

	public void writeJSONData(File file) throws IOException {
		TableFormatter tf = new TableFormatter(StringFiller.FIXED_WIDTH);
		tf.recordTables();
		writeData(tf, 20);
		ObjectMapper objectMapper = new ObjectMapper();
		List<Object> tables = tf.getTables();
		long timeProfiled = System.currentTimeMillis() - startTime;
		float tps = ticks * 1000f / timeProfiled;
		tables.add(0, CollectionsUtil.map(
				"TPS", tps
		));
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, tables);
	}

	private static <T> List<T> sortedKeys(Map<T, ? extends Comparable<?>> map, int elements) {
		List<T> list = Ordering.natural().reverse().onResultOf(Functions.forMap(map)).immutableSortedCopy(map.keySet());
		return list.size() > elements ? list.subList(0, elements) : list;
	}

	public TableFormatter writeStringData(TableFormatter tf) {
		return writeStringData(tf, 5);
	}

	public TableFormatter writeStringData(TableFormatter tf, int elements) {
		long timeProfiled = System.currentTimeMillis() - startTime;
		float tps = ticks * 1000f / timeProfiled;
		tf.sb.append("TPS: ").append(tps).append('\n').append(tf.tableSeparator);
		return writeData(tf, elements);
	}

	public TableFormatter writeData(TableFormatter tf, int elements) {
		Map<Class<?>, Long> time = new HashMap<Class<?>, Long>();
		synchronized (this.time) {
			for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
				time.put(entry.getKey(), entry.getValue().get());
			}
		}
		Map<Object, Long> singleTime = new HashMap<Object, Long>();
		synchronized (this.singleTime) {
			for (Map.Entry<Object, AtomicLong> entry : this.singleTime.entrySet()) {
				singleTime.put(entry.getKey(), entry.getValue().get());
			}
		}
		double totalTime = this.totalTime.get();
		tf
				.heading("Single Entity")
				.heading("Time/Tick")
				.heading("%");
		final List<Object> sortedSingleKeysByTime = sortedKeys(singleTime, elements);
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
				x = ((TileEntity) o).xCoord >> 4;
				z = ((TileEntity) o).zCoord >> 4;
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
		for (ChunkCoords chunkCoords : sortedKeys(chunkTimeMap, elements)) {
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
		for (Class c : sortedKeys(time, elements)) {
			tf
					.row(niceName(c))
					.row(time.get(c) / (1000000d * ticks))
					.row((time.get(c) / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		Map<Class<?>, Long> timePerTick = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			timePerTick.put(entry.getKey(), entry.getValue().get() / invocationCount.get(entry.getKey()).get());
		}
		tf
				.heading("Average Entity of Type")
				.heading("Time/tick")
				.heading("Calls");
		for (Class c : sortedKeys(timePerTick, elements)) {
			tf
					.row(niceName(c))
					.row(timePerTick.get(c) / 1000000d)
					.row(invocationCount.get(c));
		}
		tf.finishTable();
		return tf;
	}

	private static int getDimension(TileEntity o) {
		if (o.getWorldObj() == null) return -999;
		WorldProvider worldProvider = o.getWorldObj().provider;
		return worldProvider == null ? -999 : worldProvider.dimensionId;
	}

	private static int getDimension(Entity o) {
		if (o.worldObj == null) return -999;
		WorldProvider worldProvider = o.worldObj.provider;
		return worldProvider == null ? -999 : worldProvider.dimensionId;
	}

	private static Object niceName(Object o) {
		if (o instanceof TileEntity) {
			return niceName(o.getClass()) + ' ' + ((TileEntity) o).xCoord + ',' + ((TileEntity) o).yCoord + ',' + ((TileEntity) o).zCoord + ':' + getDimension((TileEntity) o);
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

	private AtomicLong getSingleInvocationCount(Object o) {
		AtomicLong t = singleInvocationCount.get(o);
		if (t == null) {
			t = singleInvocationCount.get(o);
			if (t == null) {
				t = new AtomicLong();
				singleInvocationCount.put(o, t);
			}
		}
		return t;
	}

	private AtomicInteger getInvocationCount(Class<?> clazz) {
		AtomicInteger i = invocationCount.get(clazz);
		if (i == null) {
			i = invocationCount.get(clazz);
			if (i == null) {
				i = new AtomicInteger();
				invocationCount.put(clazz, i);
			}
		}
		return i;
	}

	private AtomicLong getSingleTime(Object o) {
		return this.getTime(o, singleTime);
	}

	private AtomicLong getTime(Class<?> clazz) {
		return this.getTime(clazz, time);
	}

	private <T> AtomicLong getTime(T clazz, HashMap<T, AtomicLong> time) {
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

	private boolean isActiveChunk(World world, int chunkX, int chunkZ) {
		if (isActiveChunk == null) {
			return true;
		}

		if (lastCX == chunkX && lastCZ == chunkZ) {
			return cachedActive;
		}

		try {
			boolean result = (Boolean) isActiveChunk.invoke(world, chunkX, chunkZ);
			cachedActive = result;
			lastCX = chunkX;
			lastCZ = chunkZ;
			return result;
		} catch (Throwable t) {
			throw Throwables.propagate(t);
		}
	}

	private class ComparableLongHolder implements Comparable<ComparableLongHolder> {
		public long value;

		ComparableLongHolder() {
		}

		@Override
		public int compareTo(final ComparableLongHolder comparableLongHolder) {
			long otherValue = comparableLongHolder.value;
			return (value < otherValue) ? -1 : ((value == otherValue) ? 0 : 1);
		}
	}

	private static final class ChunkCoords {
		public final int chunkXPos;
		public final int chunkZPos;
		public final int dimension;

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
}
