package org.minimallycorrect.tickprofiler.minecraft.profiling;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import lombok.val;

import org.minimallycorrect.tickprofiler.util.CollectionsUtil;
import org.minimallycorrect.tickprofiler.util.TableFormatter;

public class UtilisationProfiler extends Profile {
	private final Map<String, Long> monitorMap = new HashMap<>();

	@Override
	public void start() {
		val threadMxBean = ManagementFactory.getThreadMXBean();
		val elements = parameters.getInt("elements");
		if (elements <= 0)
			throw new IllegalArgumentException("elements must be > 0");

		start(() -> threadMxBean.setThreadCpuTimeEnabled(true), () -> {
			List<Long> threads = Thread.getAllStackTraces().keySet().stream().map(Thread::getId).collect(Collectors.toList());

			for (Long threadId_ : threads) {
				long threadId = threadId_;
				long time = threadMxBean.getThreadCpuTime(threadId);
				if (time < 0) {
					continue;
				}
				ThreadInfo thread = threadMxBean.getThreadInfo(threadId, 0);
				if (thread != null) {
					monitorMap.put(thread.getThreadName(), time);
				}
			}

			targets.forEach(it -> {
				val tf = it.getTableFormatter();
				dump(tf, elements);
				it.sendTables(tf);
			});
		}, () -> {
			threadMxBean.setThreadCpuTimeEnabled(false);
			monitorMap.clear();
		});
	}

	private void dump(final TableFormatter tf, int entries) {
		double seconds = TimeUnit.SECONDS.toNanos(runTimeSeconds);
		tf
			.heading("Thread")
			.heading("Used CPU Time (%)");
		for (String key : CollectionsUtil.sortedKeys(monitorMap, entries)) {
			tf
				.row(key)
				.row((100d * monitorMap.get(key)) / seconds);
		}
		tf.finishTable();
	}
}
