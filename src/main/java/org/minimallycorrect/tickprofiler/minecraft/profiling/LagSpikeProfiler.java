package org.minimallycorrect.tickprofiler.minecraft.profiling;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import lombok.SneakyThrows;
import lombok.val;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.minimallycorrect.tickprofiler.minecraft.TickProfiler;
import org.minimallycorrect.tickprofiler.util.CollectionsUtil;

public class LagSpikeProfiler extends Profile {
	private static final AtomicBoolean running = new AtomicBoolean();
	private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];
	private static final int lagSpikeMillis = 200;
	private static final long lagSpikeNanoSeconds = TimeUnit.MILLISECONDS.toNanos(lagSpikeMillis);
	private static final boolean ALL_THREADS = Boolean.parseBoolean(System.getProperty("TickProfiler.allThreads", "false"));
	private boolean detected;

	private static void printThreadDump(StringBuilder sb) {
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
		if (deadlockedThreads == null) {
			TreeMap<String, String> sortedThreads = sortedThreads(threadMXBean);
			sb.append(CollectionsUtil.join(sortedThreads.values(), "\n"));
		} else {
			ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlockedThreads, true, true);
			sb.append("Definitely deadlocked: \n");
			for (ThreadInfo threadInfo : infos) {
				sb.append(toString(threadInfo, true)).append('\n');
			}
		}
	}

	private static TreeMap<String, String> sortedThreads(ThreadMXBean threadMXBean) {
		LoadingCache<String, List<ThreadInfo>> threads = CacheBuilder.newBuilder().build(new CacheLoader<String, List<ThreadInfo>>() {
			@Override
			public List<ThreadInfo> load(final String key) throws Exception {
				return new ArrayList<>();
			}
		});

		boolean allThreads = ALL_THREADS;

		ThreadInfo[] t = threadMXBean.dumpAllThreads(allThreads, allThreads);
		for (ThreadInfo thread : t) {
			if (!allThreads && !includeThread(thread))
				continue;

			String info = toString(thread, false);
			if (info != null) {
				threads.getUnchecked(info).add(thread);
			}
		}

		TreeMap<String, String> sortedThreads = new TreeMap<>();
		for (Map.Entry<String, List<ThreadInfo>> entry : threads.asMap().entrySet()) {
			List<ThreadInfo> threadInfoList = entry.getValue();
			ThreadInfo lowest = null;
			for (ThreadInfo threadInfo : threadInfoList) {
				if (lowest == null || threadInfo.getThreadName().toLowerCase().compareTo(lowest.getThreadName().toLowerCase()) < 0) {
					lowest = threadInfo;
				}
			}
			val threadNameList = CollectionsUtil.newList(threadInfoList, ThreadInfo::getThreadName);
			Collections.sort(threadNameList);
			if (lowest != null) {
				sortedThreads.put(lowest.getThreadName(), '"' + CollectionsUtil.join(threadNameList, "\", \"") + "\" " + entry.getKey());
			}
		}

		return sortedThreads;
	}

	private static boolean includeThread(ThreadInfo thread) {
		return thread.getThreadName().toLowerCase().startsWith("server thread");
	}

	private static String toString(ThreadInfo threadInfo, boolean name) {
		if (threadInfo == null) {
			return null;
		}
		StackTraceElement[] stackTrace = threadInfo.getStackTrace();
		if (stackTrace == null) {
			stackTrace = EMPTY_STACK_TRACE;
		}
		StringBuilder sb = new StringBuilder();
		if (name) {
			sb.append('"').append(threadInfo.getThreadName()).append('"').append(" Id=").append(threadInfo.getThreadId()).append(' ');
		}
		sb.append(threadInfo.getThreadState());
		if (threadInfo.getLockName() != null) {
			sb.append(" on ").append(threadInfo.getLockName());
		}
		if (threadInfo.getLockOwnerName() != null) {
			sb.append(" owned by \"").append(threadInfo.getLockOwnerName()).append("\" Id=").append(threadInfo.getLockOwnerId());
		}
		if (threadInfo.isSuspended()) {
			sb.append(" (suspended)");
		}
		if (threadInfo.isInNative()) {
			sb.append(" (in native)");
		}
		int run = 0;
		sb.append('\n');
		for (int i = 0; i < stackTrace.length; i++) {
			String steString = stackTrace[i].toString();
			if (steString.contains(".run(")) {
				run++;
			}
			sb.append("\tat ").append(steString);
			sb.append('\n');
			if (i == 0 && threadInfo.getLockInfo() != null) {
				Thread.State ts = threadInfo.getThreadState();
				switch (ts) {
					case BLOCKED:
						sb.append("\t-  blocked on ").append(threadInfo.getLockInfo());
						sb.append('\n');
						break;
					case WAITING:
						sb.append("\t-  waiting on ").append(threadInfo.getLockInfo());
						sb.append('\n');
						break;
					case TIMED_WAITING:
						sb.append("\t-  waiting on ").append(threadInfo.getLockInfo());
						sb.append('\n');
						break;
					default:
				}
			}

			for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
				if (mi.getLockedStackDepth() == i) {
					sb.append("\t-  locked ").append(mi);
					sb.append('\n');
				}
			}
		}

		LockInfo[] locks = threadInfo.getLockedSynchronizers();
		if (locks.length > 0) {
			sb.append("\n\tNumber of locked synchronizers = ").append(locks.length);
			sb.append('\n');
			for (LockInfo li : locks) {
				sb.append("\t- ").append(li);
				sb.append('\n');
			}
		}
		sb.append('\n');
		return (run <= 2 && sb.indexOf("at java.util.concurrent.LinkedBlockingQueue.take(") != -1) ? null : sb.toString();
	}

	@Override
	protected AtomicBoolean getRunning() {
		return running;
	}

	@Override
	public void start() {
		start(null, null, null, 1 + Math.min(1000, lagSpikeMillis / 6), this::checkForLagSpikes);
	}

	private void checkForLagSpikes() {
		long deadTime = System.nanoTime() - TickProfiler.lastTickTime;
		if (deadTime < lagSpikeNanoSeconds) {
			detected = false;
			return;
		}

		if (detected)
			return;

		detected = true;
		handleLagSpike(deadTime);
	}

	@SneakyThrows
	private void handleLagSpike(long deadNanoSeconds) {
		StringBuilder sb = new StringBuilder();
		sb
			.append("The server appears to have ").append("lag spiked.")
			.append("\nLast tick ").append(deadNanoSeconds / 1000000000f).append("s ago.");

		printThreadDump(sb);

		targets.forEach(it -> it.sendMessage(sb.toString()));
		Thread.sleep(2000);
	}
}
