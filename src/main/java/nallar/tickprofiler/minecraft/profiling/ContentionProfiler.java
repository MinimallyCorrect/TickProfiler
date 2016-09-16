package nallar.tickprofiler.minecraft.profiling;

import com.google.common.primitives.Longs;
import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.commands.Command;
import nallar.tickprofiler.util.CollectionsUtil;
import nallar.tickprofiler.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.lang.management.*;
import java.util.*;
import java.util.stream.*;

public class ContentionProfiler {
	private final int seconds;
	private final int resolution;
	private final Map<String, IntegerHolder> monitorMap = new IntHashMap<>();
	private final Map<String, IntegerHolder> waitingMap = new IntHashMap<>();
	private final Map<String, IntegerHolder> traceMap = new IntHashMap<>();
	private long ticks;
	private long[] threads;

	public ContentionProfiler(int seconds, int resolution) {
		this.seconds = seconds;
		this.resolution = resolution;
	}

	public static boolean profile(final ICommandSender commandSender, int seconds, int resolution) {
		Command.sendChat(commandSender, "Performing lock contention profiling for " + seconds + " seconds.");
		final ContentionProfiler contentionProfiler = new ContentionProfiler(seconds, resolution);
		contentionProfiler.run(() -> {
			TableFormatter tf = new TableFormatter(commandSender);
			contentionProfiler.dump(tf, commandSender instanceof MinecraftServer ? 15 : 6);
			Command.sendChat(commandSender, tf.toString());
		});
		return true;
	}

	private static String name(StackTraceElement stack) {
		if (stack == null) {
			return null;
		}
		String className = stack.getClassName();
		return className.substring(className.lastIndexOf('.') + 1) + '.' + stack.getMethodName();
	}

	public void run(final Runnable completed) {
		final int ticks = seconds * 1000 / resolution;
		new Thread(() -> {
			profile(ticks);
			completed.run();
		}, "Contention Profiler").start();
	}

	public void dump(final TableFormatter tf, int entries) {
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

	private void profile(int ticks) {
		List<Long> threads = Thread.getAllStackTraces().keySet().stream().map(Thread::getId).collect(Collectors.toList());
		// TODO
		this.threads = Longs.toArray(threads);
		while (ticks-- > 0) {
			long r = resolution - tick();

			if (r > 0) {
				try {
					Thread.sleep(r, 0);
				} catch (InterruptedException e) {
					Log.error("Interrupted in profiling", e);
					return;
				}
			} else if (r < -10) {
				ticks--;
			}
			this.ticks++;
		}
	}

	private long tick() {
		long t = System.currentTimeMillis();
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
					if (stack != null && ("waitForCompletion".equals(stack.getMethodName()) || "nallar.tickthreading.minecraft.ThreadManager$1".equals(stack.getClassName()))) {
						continue;
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
		return System.currentTimeMillis() - t;
	}

	private static class IntHashMap<K> extends HashMap<K, IntegerHolder> {
		IntHashMap() {
		}

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

	private static class IntegerHolder implements Comparable<IntegerHolder> {
		public int value;

		IntegerHolder() {
		}

		@Override
		public int compareTo(final IntegerHolder o) {
			int x = value;
			int y = o.value;
			return (x < y) ? -1 : ((x == y) ? 0 : 1);
		}
	}
}
