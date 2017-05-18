package org.minimallycorrect.tickprofiler.minecraft.profiling;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;
import net.minecraft.command.ICommandSender;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import org.minimallycorrect.modpatcher.api.UsedByPatch;
import org.minimallycorrect.tickprofiler.minecraft.commands.Command;
import org.minimallycorrect.tickprofiler.util.TableFormatter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class PacketProfiler {
	private static final Map<String, AtomicInteger> size = new ConcurrentHashMap<>();
	private static final Map<String, AtomicInteger> count = new ConcurrentHashMap<>();
	private static boolean profiling = false;

	public static synchronized void profile(final ICommandSender commandSender, final int time) {
		if (profiling) {
			Command.sendChat(commandSender, "Someone else is already profiling packets.");
			return;
		}
		profiling = true;
		Command.sendChat(commandSender, "Profiling packets for " + time + " seconds.");
		new Thread(() -> {
			try {
				Thread.sleep(time * 1000);
			} catch (InterruptedException ignored) {
			}
			Command.sendChat(commandSender, writeStats(new TableFormatter(commandSender)).toString());
			synchronized (PacketProfiler.class) {
				size.clear();
				count.clear();
				profiling = false;
			}
		}).start();
	}

	private static <T> List<T> sortedKeys(Map<T, ? extends Comparable<?>> map, int elements) {
		List<T> list = Ordering.natural().reverse().onResultOf(Functions.forMap(map)).immutableSortedCopy(map.keySet());
		return list.size() > elements ? list.subList(0, elements) : list;
	}

	private static TableFormatter writeStats(final TableFormatter tf) {
		return writeStats(tf, 9);
	}

	private static TableFormatter writeStats(final TableFormatter tf, int elements) {
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
		return tf;
	}

	private static String humanReadableName(String name) {
		if (name.startsWith("net.minecraft.network.")) {
			return name.substring(name.lastIndexOf('.') + 1);
		}
		return name;
	}

	@UsedByPatch("packethook.xml")
	public static void record(final Packet packet, PacketBuffer buffer) {
		if (!profiling) {
			return;
		}
		String id = packet.getClass().getSimpleName();
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
}
