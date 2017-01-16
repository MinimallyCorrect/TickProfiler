package nallar.tickprofiler.minecraft.profiling;

import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.TickProfiler;
import nallar.tickprofiler.minecraft.commands.ProfileCommand;
import nallar.tickprofiler.util.CollectionsUtil;
import nallar.tickprofiler.util.TableFormatter;
import nallar.tickprofiler.util.stringfillers.StringFiller;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class EntityTickProfiler {
	public static final EntityTickProfiler INSTANCE = new EntityTickProfiler();
	public static ProfileCommand.ProfilingState profilingState = ProfileCommand.ProfilingState.NONE;
	private final HashMap<Class<?>, AtomicInteger> invocationCount = new HashMap<>();
	private final HashMap<Class<?>, AtomicLong> time = new HashMap<>();
	private final HashMap<Object, AtomicLong> singleTime = new HashMap<>();
	private final HashMap<Object, AtomicLong> singleInvocationCount = new HashMap<>();
	private final AtomicLong totalTime = new AtomicLong();
	private int lastCX = Integer.MIN_VALUE;
	private int lastCZ = Integer.MIN_VALUE;
	private boolean cachedActive = false;
	private int ticks;
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
		final Collection<World> worlds = new ArrayList<>(worlds_);
		synchronized (EntityTickProfiler.class) {
			if (!startProfiling(state)) {
				return false;
			}
			for (World world_ : worlds) {
				TickProfiler.instance.hookProfiler(world_);
			}
		}

		Runnable profilingRunnable = () -> {
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
		};
		Thread profilingThread = new Thread(profilingRunnable);
		profilingThread.setName("TickProfiler");
		profilingThread.start();
		startTime = System.currentTimeMillis();
		return true;
	}

	public void profileEntity(World w, Entity entity) {
		final boolean profile = profilingState == ProfileCommand.ProfilingState.ENTITIES || (entity.chunkCoordX == chunkX && entity.chunkCoordZ == chunkZ);

		if (!profile) {
			w.updateEntity(entity);
			return;
		}

		long start = System.nanoTime();
		w.updateEntity(entity);
		record(entity, System.nanoTime() - start);
	}

	public void profileTickable(ITickable tickable) {
		final boolean profile = profilingState == ProfileCommand.ProfilingState.ENTITIES || shouldProfilePos(((TileEntity) tickable).getPos());

		if (!profile) {
			tickable.update();
			return;
		}

		long start = System.nanoTime();
		tickable.update();
		record(tickable, System.nanoTime() - start);
	}

	private boolean shouldProfilePos(BlockPos pos) {
		return pos.getX() >> 4 == chunkX && pos.getZ() >> 4 == chunkZ;
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

	@SuppressWarnings("unchecked")
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
		Map<Class<?>, Long> time = new HashMap<>();
		synchronized (this.time) {
			for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
				time.put(entry.getKey(), entry.getValue().get());
			}
		}
		Map<Object, Long> singleTime = new HashMap<>();
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
		for (Class c : CollectionsUtil.sortedKeys(time, elements)) {
			tf
				.row(niceName(c))
				.row(time.get(c) / (1000000d * ticks))
				.row((time.get(c) / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		Map<Class<?>, Long> timePerTick = new HashMap<>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			timePerTick.put(entry.getKey(), entry.getValue().get() / invocationCount.get(entry.getKey()).get());
		}
		tf
			.heading("Average Entity of Type")
			.heading("Time/tick")
			.heading("Calls");
		for (Class c : CollectionsUtil.sortedKeys(timePerTick, elements)) {
			tf
				.row(niceName(c))
				.row(timePerTick.get(c) / 1000000d)
				.row(invocationCount.get(c));
		}
		tf.finishTable();
		return tf;
	}

	private static int getDimension(TileEntity o) {
		//noinspection ConstantConditions
		if (o.getWorld() == null) return -999;
		WorldProvider worldProvider = o.getWorld().provider;
		return worldProvider == null ? -999 : worldProvider.getDimension();
	}

	private static int getDimension(Entity o) {
		if (o.world == null) return -999;
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

	@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
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
}
