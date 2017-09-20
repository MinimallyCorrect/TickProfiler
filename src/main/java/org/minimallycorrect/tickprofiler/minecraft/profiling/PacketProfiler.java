package org.minimallycorrect.tickprofiler.minecraft.profiling;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;
import lombok.val;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import org.minimallycorrect.modpatcher.api.UsedByPatch;
import org.minimallycorrect.tickprofiler.util.TableFormatter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class PacketProfiler extends Profile {
	private static final AtomicBoolean running = new AtomicBoolean();
	private static final Map<String, AtomicInteger> size = new ConcurrentHashMap<>();
	private static final Map<String, AtomicInteger> count = new ConcurrentHashMap<>();

	private static <T> List<T> sortedKeys(Map<T, ? extends Comparable<?>> map, int elements) {
		List<T> list = Ordering.natural().reverse().onResultOf(Functions.forMap(map)).immutableSortedCopy(map.keySet());
		return list.size() > elements ? list.subList(0, elements) : list;
	}

	private static void writeStats(final TableFormatter tf, int elements) {
		Map<String, Integer> count = new HashMap<>();
		for (Map.Entry<String, AtomicInteger> entry : PacketProfiler.count.entrySet()) {
			count.put(entry.getKey(), entry.getValue().get());
		}
		Map<String, Integer> size = new HashMap<>();
		for (Map.Entry<String, AtomicInteger> entry : PacketProfiler.size.entrySet()) {
			size.put(entry.getKey(), entry.getValue().get());
		}

		tf
			.heading("Packet")
			.heading("Count")
			.heading("Size");
		final List<String> sortedIdsByCount = sortedKeys(count, elements);
		for (String id : sortedIdsByCount) {
			tf
				.row(humanReadableName(id))
				.row(count.get(id))
				.row(humanReadableByteCount(size.get(id)));
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
			.heading("Packet")
			.heading("Count")
			.heading("Size");
		final List<String> sortedIdsBySize = sortedKeys(size, elements);
		for (String id : sortedIdsBySize) {
			tf
				.row(humanReadableName(id))
				.row(count.get(id))
				.row(humanReadableByteCount(size.get(id)));
		}
		tf.finishTable();
	}

	private static String humanReadableName(String name) {
		if (name.startsWith("net.minecraft.network.")) {
			return name.substring(name.lastIndexOf('.') + 1);
		}
		return name;
	}

	@UsedByPatch("packethook.xml")
	public static void record(final Packet<?> packet, PacketBuffer buffer) {
		if (!running.get()) {
			return;
		}
		String id;

		if (packet instanceof SPacketCustomPayload) {
			id = ((SPacketCustomPayload) packet).getChannelName();
		} else {
			id = packet.getClass().getName();
			id = id.substring(id.lastIndexOf('.') + 1);
		}
		int size = buffer.readableBytes();
		getAtomicInteger(id, count).getAndIncrement();
		getAtomicInteger(id, PacketProfiler.size).addAndGet(size);
	}

	@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
	private static AtomicInteger getAtomicInteger(String key, Map<String, AtomicInteger> map) {
		AtomicInteger t = map.get(key);
		if (t == null) {
			synchronized (map) {
				t = map.computeIfAbsent(key, k -> new AtomicInteger());
			}
		}
		return t;
	}

	// http://stackoverflow.com/a/3758880/250076
	private static String humanReadableByteCount(int bytes) {
		int unit = 1024;
		if (bytes < unit) {
			return bytes + " B";
		}
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		char pre = ("KMGTPE").charAt(exp - 1);
		return String.format("%.1f%cB", bytes / Math.pow(unit, exp), pre);
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
		start(() -> {}, () -> targets.forEach(it -> {
			val tf = it.getTableFormatter();
			writeStats(tf, elements);
			it.sendTables(tf);
		}), () -> {
			size.clear();
			count.clear();
		});
	}
}
