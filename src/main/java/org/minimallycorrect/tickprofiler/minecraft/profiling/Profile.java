package org.minimallycorrect.tickprofiler.minecraft.profiling;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;

import org.minimallycorrect.tickprofiler.Log;
import org.minimallycorrect.tickprofiler.minecraft.commands.Command;
import org.minimallycorrect.tickprofiler.minecraft.commands.Parameters;
import org.minimallycorrect.tickprofiler.minecraft.commands.UsageException;
import org.minimallycorrect.tickprofiler.util.TableFormatter;
import org.minimallycorrect.tickprofiler.util.stringfillers.StringFiller;

import net.minecraft.command.ICommandSender;

public abstract class Profile {
	@Nullable
	ICommandSender commandSender;
	List<ProfileTarget> targets;
	Parameters parameters;
	long startTime;
	int runTimeSeconds;
	private Thread thread;

	protected AtomicBoolean getRunning() {
		return null;
	}

	public abstract void start();

	public final void start(ICommandSender commandSender, List<ProfileTarget> targets, Parameters p) {
		this.commandSender = commandSender;
		this.targets = targets;
		this.parameters = p;
		startTime = System.nanoTime();
		if (p.has("time")) {
			runTimeSeconds = p.getInt("time");
			if (runTimeSeconds <= 0)
				throw new UsageException("time must be > 0, got " + runTimeSeconds);
		}
		start();
	}

	public void closeIfNeeded() {
		if (thread == null)
			end();
	}

	private String getName() {
		return getClass().getSimpleName();
	}

	public void abort() {
		thread.interrupt();
	}

	void start(Runnable before, Runnable after, Runnable finally_, long interval, Runnable intervalRunnable) {
		val running = getRunning();
		if (running != null && !running.compareAndSet(false, true))
			throw new AlreadyRunningException(getName() + " is already running");

		thread = new Thread(() -> {
			if (before != null)
				before.run();
			try {
				targets.forEach(it -> it.sendMessage("Running " + getName() + " for " + runTimeSeconds + " seconds"));
				if (intervalRunnable == null)
					Thread.sleep(runTimeSeconds * 1000L);
				else {
					val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(runTimeSeconds);
					while (System.nanoTime() < end) {
						Thread.sleep(interval);
						intervalRunnable.run();
					}
				}
				if (after != null)
					after.run();
			} catch (InterruptedException ignored) {
				targets.forEach(it -> it.sendMessage("Profiling aborted due to interrupt"));
			} finally {
				if (finally_ != null)
					finally_.run();
				end();
			}
		}, getName());
		thread.start();
	}

	void start(Runnable before, Runnable after, Runnable finally_) {
		start(before, after, finally_, 0, null);
	}

	private void end() {
		val running = getRunning();
		if (running != null && !running.compareAndSet(true, false))
			throw new IllegalStateException(getName() + " is not already running");
		for (ProfileTarget target : targets) {
			target.close();
		}
	}

	@RequiredArgsConstructor
	public enum Types {
		COUNT_ENTITIES("c", EntityCountingProfiler.class, Arrays.asList("worlds", "all", "elements", "10")),
		ENTITIES("e", EntityProfiler.class, Arrays.asList("time", "30", "worlds", "all", "elements", "5", "chunk", "all")),
		PACKETS("p", PacketProfiler.class, Arrays.asList("time", "30", "elements", "5")),
		UTILISATION("u", UtilisationProfiler.class, Arrays.asList("time", "30", "elements", "5")),
		LOCK_CONTENTION("l", ContentionProfiler.class, Arrays.asList("time", "30", "elements", "5", "interval_ms", "23")),
		LAG_SPIKE_DETECTOR("s", LagSpikeProfiler.class, Arrays.asList("time", "500"));
		private static final Map<String, Types> types = new HashMap<>();

		static {
			for (val t : Types.values()) {
				types.put(t.shortName.toLowerCase(), t);
				types.put(t.name().toLowerCase(), t);
			}
		}

		public final String shortName;
		final Class<? extends Profile> clazz;
		final List<String> orderWithDefaults;

		public static Types byName(String typeName) {
			return types.get(typeName.toLowerCase());
		}

		public void addParameters(Parameters p) {
			p.orderWithDefault(orderWithDefaults);
		}

		@SneakyThrows
		public Profile create() {
			return clazz.newInstance();
		}
	}

	public interface ProfileTarget extends AutoCloseable {
		static ProfileTarget console() {
			return new ProfileTarget() {
				@Override
				public void sendMessage(String message) {
					Log.info('\n' + message);
				}

				@Override
				public TableFormatter getTableFormatter() {
					return new TableFormatter(StringFiller.FIXED_WIDTH, false);
				}
			};
		}

		static ProfileTarget commandSender(ICommandSender commandSender) {
			return new ProfileTarget() {
				@Override
				public void sendMessage(String message) {
					Command.sendChat(commandSender, message);
				}

				@Override
				public TableFormatter getTableFormatter() {
					return new TableFormatter(commandSender);
				}
			};
		}

		@SneakyThrows
		static ProfileTarget json(File file) {
			val w = Files.newBufferedWriter(file.toPath());
			return new ProfileTarget() {
				@SneakyThrows
				@Override
				public void sendMessage(String message) {
					w.write(message + "\n");
				}

				@Override
				public TableFormatter getTableFormatter() {
					return new TableFormatter(StringFiller.FIXED_WIDTH, true);
				}

				@SneakyThrows
				@Override
				public void close() {
					w.close();
				}
			};
		}

		void sendMessage(String message);

		TableFormatter getTableFormatter();

		default void sendTables(TableFormatter tf) {
			sendMessage(tf.toString());
		}

		default void close() {}
	}
}
