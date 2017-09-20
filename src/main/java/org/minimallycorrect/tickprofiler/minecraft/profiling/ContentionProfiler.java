package org.minimallycorrect.tickprofiler.minecraft.profiling;

import com.google.common.primitives.Longs;
import lombok.EqualsAndHashCode;
import lombok.val;
import org.minimallycorrect.tickprofiler.util.CollectionsUtil;
import org.minimallycorrect.tickprofiler.util.TableFormatter;

import java.lang.management.*;
import java.util.*;
import java.util.stream.*;

public class ContentionProfiler extends Profile {
	private final Map<String, IntegerHolder> monitorMap = new IntHashMap<>();
	private final Map<String, IntegerHolder> waitingMap = new IntHashMap<>();
	private final Map<String, IntegerHolder> traceMap = new IntHashMap<>();
	private long[] threads;
	private int ticks = 0;

	private static String name(StackTraceElement stack) {
		if (stack == null) {
			return null;
		}
		String className = stack.getClassName();
		return className.substring(className.lastIndexOf('.') + 1) + '.' + stack.getMethodName();
	}

	@Override
	public void start() {
		val elements = parameters.getInt("elements");
		if (elements <= 0)
			throw new IllegalArgumentException("elements must be > 0");
		val intervalMs = parameters.getInt("interval_ms");
		start(() -> {
			List<Long> threads = Thread.getAllStackTraces().keySet().stream().map(Thread::getId).collect(Collectors.toList());
			this.threads = Longs.toArray(threads);
		}, () -> targets.forEach(it -> {
			val tf = it.getTableFormatter();
			dump(tf, elements);
			it.sendTables(tf);
		}), () -> {
			monitorMap.clear();
			waitingMap.clear();
			traceMap.clear();
			ticks = 0;
		}, intervalMs, this::tick);
	}

	private void dump(final TableFormatter tf, int entries) {
		float ticks = this.ticks;
		tf
			.heading("Monitor")
			.heading("Wasted Cores");
		for (String key : CollectionsUtil.sortedKeys(monitorMap, entries)) {
			tf
				.row(key)
				.row(monitorMap.get(key).value / ticks);
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
			.heading("Wait")
			.heading("Wasted Cores");
		for (String key : CollectionsUtil.sortedKeys(waitingMap, entries)) {
			tf
				.row(key)
				.row(waitingMap.get(key).value / ticks);
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
			.heading("Stack")
			.heading("Wasted Cores");
		for (String key : CollectionsUtil.sortedKeys(traceMap, entries)) {
			tf
				.row(key)
				.row(traceMap.get(key).value / ticks);
		}
		tf.finishTable();
	}

	private void tick() {
		ticks++;
		ThreadInfo[] threads = ManagementFactory.getThreadMXBean().getThreadInfo(this.threads, 6);
		for (ThreadInfo thread : threads) {
			if (thread == null) {
				continue;
			}
			Thread.State ts = thread.getThreadState();
			switch (ts) {
				case WAITING:
				case TIMED_WAITING:
				case BLOCKED:
					StackTraceElement[] stackTrace = thread.getStackTrace();
					StackTraceElement stack = null;
					StackTraceElement prevStack = null;
					for (StackTraceElement stackTraceElement : stackTrace) {
						String className = stackTraceElement.getClassName();
						if (className.startsWith("java") || className.startsWith("sun.")) {
							prevStack = stackTraceElement;
						} else {
							stack = stackTraceElement;
							break;
						}
					}
					LockInfo lockInfo = thread.getLockInfo();
					if (lockInfo != null) {
						(ts == Thread.State.BLOCKED ? monitorMap : waitingMap).get(lockInfo.toString()).value++;
					}
					if (stack != null) {
						String prev = name(prevStack);
						traceMap.get(name(stack) + (prev == null ? "" : " -> " + prev)).value++;
					}
					break;
			}
		}
	}

	private static class IntHashMap<K> extends HashMap<K, IntegerHolder> {
		IntHashMap() {}

		@SuppressWarnings("unchecked")
		@Override
		public IntegerHolder get(Object k) {
			IntegerHolder integerHolder = super.get(k);
			if (integerHolder == null) {
				integerHolder = new IntegerHolder();
				put((K) k, integerHolder);
			}
			return integerHolder;
		}
	}

	@EqualsAndHashCode
	private static class IntegerHolder implements Comparable<IntegerHolder> {
		int value;

		IntegerHolder() {}

		@Override
		public int compareTo(final IntegerHolder o) {
			return Integer.compare(value, o.value);
		}
	}
}
