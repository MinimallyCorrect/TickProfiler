package org.minimallycorrect.tickprofiler.minecraft.profiling;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import lombok.val;

import org.minimallycorrect.tickprofiler.util.CollectionsUtil;

public class UtilisationProfiler extends Profile {
	@Override
	public void start() {
		val threadMxBean = ManagementFactory.getThreadMXBean();
		val elements = parameters.getInt("elements");
		if (elements <= 0)
			throw new IllegalArgumentException("elements must be > 0");

		val initialTimes = new HashMap<String, Long>();

		start(() -> {
			threadMxBean.setThreadCpuTimeEnabled(true);

			List<Long> threads = Thread.getAllStackTraces().keySet().stream().map(Thread::getId).collect(Collectors.toList());
			getThreadCpuTimes(threadMxBean, threads, initialTimes);
		}, () -> {
			List<Long> threads = Thread.getAllStackTraces().keySet().stream().map(Thread::getId).collect(Collectors.toList());

			val endTimes = new HashMap<String, Long>();
			getThreadCpuTimes(threadMxBean, threads, endTimes);

			val endDurations = new HashMap<String, Long>();
			for (val e : endTimes.entrySet()) {
				endDurations.put(e.getKey(), e.getValue() - initialTimes.getOrDefault(e.getKey(), 0L));
			}

			targets.forEach(it -> {
				val tf = it.getTableFormatter();
				double runTimeNanoSeconds = TimeUnit.SECONDS.toNanos(runTimeSeconds);
				tf
					.heading("Thread")
					.heading("Used CPU Time (%)");
				for (String key : CollectionsUtil.sortedKeys(endDurations, elements)) {
					tf
						.row(key)
						.row((100d * endDurations.get(key)) / runTimeNanoSeconds);
				}
				tf.finishTable();
				it.sendTables(tf);
			});
		}, () -> {
			threadMxBean.setThreadCpuTimeEnabled(false);
		});
	}

	private void getThreadCpuTimes(ThreadMXBean threadMxBean, List<Long> threads, HashMap<String, Long> map) {
		for (Long threadId_ : threads) {
			long threadId = threadId_;
			long time = threadMxBean.getThreadCpuTime(threadId);
			if (time < 0) {
				continue;
			}
			ThreadInfo thread = threadMxBean.getThreadInfo(threadId, 0);
			if (thread != null) {
				map.put(thread.getThreadName(), time);
			}
		}
	}
}
