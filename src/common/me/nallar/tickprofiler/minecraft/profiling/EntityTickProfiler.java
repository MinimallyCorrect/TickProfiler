package me.nallar.tickprofiler.minecraft.profiling;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import me.nallar.tickprofiler.minecraft.commands.ProfileCommand;
import me.nallar.tickprofiler.minecraft.entitylist.EntityList;
import me.nallar.tickprofiler.util.MappingUtil;
import me.nallar.tickprofiler.util.ReflectUtil;
import me.nallar.tickprofiler.util.TableFormatter;
import me.nallar.tickprofiler.util.UnsafeUtil;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

public class EntityTickProfiler {
	private int ticks;
	private final AtomicLong totalTime = new AtomicLong();
	private final Field unloadedEntityList = ReflectUtil.getFields(World.class, List.class)[1];
	private int chunkX;
	private int chunkZ;

	public void setLocation(final int x, final int z) {
		chunkX = x;
		chunkZ = z;
	}

	public void runEntities(World world, ArrayList<Entity> toTick) {
		List<Entity> unloadedEntityList;
		try {
			unloadedEntityList = (List<Entity>) this.unloadedEntityList.get(world);
		} catch (IllegalAccessException e) {
			throw UnsafeUtil.throwIgnoreChecked(e);
		}
		Iterator<Entity> iterator = toTick.iterator();

		long end = System.nanoTime();
		long start;
		boolean isGlobal = EntityList.profilingState == ProfileCommand.ProfilingState.GLOBAL;
		while (iterator.hasNext()) {
			Entity entity = iterator.next();

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
					entity.func_85029_a(crashReportCategory);

					throw new ReportedException(crashReport);
				}
			}

			if (entity.isDead) {
				unloadedEntityList.add(entity);
			}
			end = System.nanoTime();

			if (isGlobal || (entity.chunkCoordX == chunkX && entity.chunkCoordZ == chunkZ)) {
				record(entity, end - start);
			}
		}
	}

	public void runTileEntities(World world, ArrayList<TileEntity> toTick) {
		IChunkProvider chunkProvider = world.getChunkProvider();
		Iterator<TileEntity> iterator = toTick.iterator();
		long end = System.nanoTime();
		long start;
		boolean isGlobal = EntityList.profilingState == ProfileCommand.ProfilingState.GLOBAL;
		while (iterator.hasNext()) {
			start = end;
			TileEntity tileEntity = iterator.next();

			int x = tileEntity.xCoord;
			int z = tileEntity.zCoord;
			if (!tileEntity.isInvalid() && tileEntity.func_70309_m() && chunkProvider.chunkExists(x >> 4, z >> 4)) {
				try {
					tileEntity.updateEntity();
				} catch (Throwable var6) {
					CrashReport crashReport = CrashReport.makeCrashReport(var6, "Ticking tile entity");
					CrashReportCategory crashReportCategory = crashReport.makeCategory("Tile entity being ticked");
					tileEntity.func_85027_a(crashReportCategory);

					throw new ReportedException(crashReport);
				}
			}

			if (tileEntity.isInvalid()) {
				iterator.remove();

				if (chunkProvider.chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
					Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);

					if (chunk != null) {
						chunk.cleanChunkBlockTileEntity(tileEntity.xCoord & 15, tileEntity.yCoord, tileEntity.zCoord & 15);
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
		time.clear();
		totalTime.set(0);
		singleTime.clear();
		singleInvocationCount.clear();
		ticks = 0;
	}

	public void tick() {
		ticks++;
	}

	public TableFormatter writeData(TableFormatter tf) {
		Map<Class<?>, Long> time = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			time.put(entry.getKey(), entry.getValue().get());
		}
		Map<Object, Long> singleTime = new HashMap<Object, Long>();
		for (Map.Entry<Object, AtomicLong> entry : this.singleTime.entrySet()) {
			singleTime.put(entry.getKey(), entry.getValue().get());
		}
		final List<Object> sortedSingleKeysByTime = Ordering.natural().reverse().onResultOf(Functions.forMap(singleTime)).immutableSortedCopy(singleTime.keySet());
		tf
				.heading("Obj")
				.heading("Time/Tick")
				.heading("%");
		for (int i = 0; i < 5 && i < sortedSingleKeysByTime.size(); i++) {
			tf
					.row(niceName(sortedSingleKeysByTime.get(i)))
					.row(singleTime.get(sortedSingleKeysByTime.get(i)) / (1000000d * singleInvocationCount.get(sortedSingleKeysByTime.get(i)).get()))
					.row((singleTime.get(sortedSingleKeysByTime.get(i)) / (double) totalTime.get()) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		final List<Class<?>> sortedKeysByTime = Ordering.natural().reverse().onResultOf(Functions.forMap(time)).immutableSortedCopy(time.keySet());
		tf
				.heading("Class")
				.heading("Total Time/Tick")
				.heading("%");
		for (int i = 0; i < 5 && i < sortedKeysByTime.size(); i++) {
			tf
					.row(niceName(sortedKeysByTime.get(i)))
					.row(time.get(sortedKeysByTime.get(i)) / (1000000d * ticks))
					.row((time.get(sortedKeysByTime.get(i)) / (double) totalTime.get()) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		Map<Class<?>, Long> timePerTick = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			timePerTick.put(entry.getKey(), entry.getValue().get() / invocationCount.get(entry.getKey()).get());
		}
		final List<Class<?>> sortedKeysByTimePerTick = Ordering.natural().reverse().onResultOf(Functions.forMap(timePerTick)).immutableSortedCopy(timePerTick.keySet());
		tf
				.heading("Class")
				.heading("Time/tick")
				.heading("Calls");
		for (int i = 0; i < 5 && i < sortedKeysByTimePerTick.size(); i++) {
			tf
					.row(niceName(sortedKeysByTimePerTick.get(i)))
					.row(timePerTick.get(sortedKeysByTimePerTick.get(i)) / 1000000d)
					.row(invocationCount.get(sortedKeysByTimePerTick.get(i)));
		}
		tf.finishTable();
		return tf;
	}

	private static Object niceName(Object o) {
		if (o instanceof TileEntity) {
			return niceName(o.getClass()) + ' ' + ((TileEntity) o).xCoord + ',' + ((TileEntity) o).yCoord + ',' + ((TileEntity) o).zCoord;
		} else if (o instanceof Entity) {
			return niceName(o.getClass()) + ' ' + (int) ((Entity) o).posX + ',' + (int) ((Entity) o).posY + ',' + (int) ((Entity) o).posZ;
		}
		return o.toString().substring(0, 48);
	}

	private static String niceName(Class<?> clazz) {
		String name = MappingUtil.debobfuscate(clazz.getName());
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

	private final Map<Class<?>, AtomicInteger> invocationCount = new NonBlockingHashMap<Class<?>, AtomicInteger>();
	private final Map<Class<?>, AtomicLong> time = new NonBlockingHashMap<Class<?>, AtomicLong>();
	private final Map<Object, AtomicLong> singleTime = new NonBlockingHashMap<Object, AtomicLong>();
	private final Map<Object, AtomicLong> singleInvocationCount = new NonBlockingHashMap<Object, AtomicLong>();

	private AtomicLong getSingleInvocationCount(Object o) {
		AtomicLong t = singleInvocationCount.get(o);
		if (t == null) {
			synchronized (o) {
				t = singleInvocationCount.get(o);
				if (t == null) {
					t = new AtomicLong();
					singleInvocationCount.put(o, t);
				}
			}
		}
		return t;
	}

	// We synchronize on the class name as it is always the same object
	// We do not synchronize on the class object as that would also
	// prevent any synchronized static methods on it from running
	private AtomicInteger getInvocationCount(Class<?> clazz) {
		AtomicInteger i = invocationCount.get(clazz);
		if (i == null) {
			synchronized (clazz.getName()) {
				i = invocationCount.get(clazz);
				if (i == null) {
					i = new AtomicInteger();
					invocationCount.put(clazz, i);
				}
			}
		}
		return i;
	}

	private AtomicLong getSingleTime(Object o) {
		AtomicLong t = singleTime.get(o);
		if (t == null) {
			synchronized (o) {
				t = singleTime.get(o);
				if (t == null) {
					t = new AtomicLong();
					singleTime.put(o, t);
				}
			}
		}
		return t;
	}

	private AtomicLong getTime(Class<?> clazz) {
		AtomicLong t = time.get(clazz);
		if (t == null) {
			synchronized (clazz.getName()) {
				t = time.get(clazz);
				if (t == null) {
					t = new AtomicLong();
					time.put(clazz, t);
				}
			}
		}
		return t;
	}
}
