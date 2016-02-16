package nallar.tickprofiler.minecraft.profiling;

import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.commands.Command;
import nallar.tickprofiler.util.CollectionsUtil;
import nallar.tickprofiler.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

public class UtilisationProfiler {
	private final int seconds;
	private final Map<String, Long> monitorMap = new HashMap<>();

	public UtilisationProfiler(int seconds) {
		this.seconds = seconds;
	}

	public static boolean profile(final ICommandSender commandSender, int seconds) {
		Command.sendChat(commandSender, "Performing utilisation profiling for " + seconds + " seconds.");
		final UtilisationProfiler contentionProfiler = new UtilisationProfiler(seconds);
		contentionProfiler.run(new Runnable() {
			@Override
			public void run() {
				TableFormatter tf = new TableFormatter(commandSender);
				contentionProfiler.dump(tf, commandSender instanceof MinecraftServer ? 15 : 6);
				Command.sendChat(commandSender, tf.toString());
			}
		});
		return true;
	}

	public void run(final Runnable completed) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				profile();
				completed.run();
				monitorMap.clear();
			}
		}, "Contention Profiler").start();
	}

	public void dump(final TableFormatter tf, int entries) {
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

			List<Long> threads = new ArrayList<>();
			for (Thread thread : Thread.getAllStackTraces().keySet()) {
				threads.add(thread.getId());
			}

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
