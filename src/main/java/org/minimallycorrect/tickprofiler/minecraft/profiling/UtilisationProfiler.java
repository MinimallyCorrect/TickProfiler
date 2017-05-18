package org.minimallycorrect.tickprofiler.minecraft.profiling;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import org.minimallycorrect.tickprofiler.Log;
import org.minimallycorrect.tickprofiler.minecraft.commands.Command;
import org.minimallycorrect.tickprofiler.util.CollectionsUtil;
import org.minimallycorrect.tickprofiler.util.TableFormatter;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class UtilisationProfiler {
	private final int seconds;
	private final Map<String, Long> monitorMap = new HashMap<>();

	private UtilisationProfiler(int seconds) {
		this.seconds = seconds;
	}

	public static void profile(final ICommandSender commandSender, int seconds) {
		Command.sendChat(commandSender, "Performing utilisation profiling for " + seconds + " seconds.");
		final UtilisationProfiler contentionProfiler = new UtilisationProfiler(seconds);
		contentionProfiler.run(() -> {
			TableFormatter tf = new TableFormatter(commandSender);
			contentionProfiler.dump(tf, commandSender instanceof MinecraftServer ? 15 : 6);
			Command.sendChat(commandSender, tf.toString());
		});
	}

	private void run(final Runnable completed) {
		new Thread(() -> {
			profile();
			completed.run();
			monitorMap.clear();
		}, "Contention Profiler").start();
	}

	private void dump(final TableFormatter tf, int entries) {
		double seconds = TimeUnit.SECONDS.toNanos(this.seconds);
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

	private void profile() {
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		try {
			threadMXBean.setThreadCpuTimeEnabled(true);

			List<Long> threads = Thread.getAllStackTraces().keySet().stream().map(Thread::getId).collect(Collectors.toList());

			Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));

			for (Long threadId_ : threads) {
				long threadId = threadId_;
				long time = threadMXBean.getThreadCpuTime(threadId);
				if (time < 0) {
					continue;
				}
				ThreadInfo thread = threadMXBean.getThreadInfo(threadId, 0);
				if (thread != null) {
					monitorMap.put(thread.getThreadName(), time);
				}
			}
		} catch (InterruptedException e) {
			Log.error("Interrupted in profiling", e);
		} finally {
			threadMXBean.setThreadCpuTimeEnabled(false);
		}
	}
}
