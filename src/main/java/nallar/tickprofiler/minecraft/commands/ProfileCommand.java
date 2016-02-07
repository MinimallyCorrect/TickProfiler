package nallar.tickprofiler.minecraft.commands;

import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.TickProfiler;
import nallar.tickprofiler.minecraft.profiling.ContentionProfiler;
import nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import nallar.tickprofiler.minecraft.profiling.PacketProfiler;
import nallar.tickprofiler.minecraft.profiling.UtilisationProfiler;
import nallar.tickprofiler.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.*;

public class ProfileCommand extends Command {
	public static String name = "profile";

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public boolean requireOp() {
		return TickProfiler.instance.requireOpForProfileCommand;
	}

	@Override
	public void processCommand(final ICommandSender commandSender, List<String> arguments) {
		process(commandSender, arguments);
	}

	private boolean process(final ICommandSender commandSender, List<String> arguments) {
		World world = null;
		int time_;
		Integer x = null;
		Integer z = null;
		ProfilingState type;
		try {
			type = ProfilingState.get(arguments.get(0));
			if (type == null)
				throw new RuntimeException();

			if (type == ProfilingState.CHUNK_ENTITIES && arguments.size() > 2) {
				x = Integer.valueOf(arguments.remove(1));
				z = Integer.valueOf(arguments.remove(1));
			}

			time_ = type.time;

			if (arguments.size() > 1) {
				time_ = Integer.valueOf(arguments.get(1));
			}

			switch (type) {
				case PACKETS:
					return PacketProfiler.profile(commandSender, time_);
				case UTILISATION:
					return UtilisationProfiler.profile(commandSender, time_);
				case LOCK_CONTENTION:
					int resolution = 240;
					if (arguments.size() > 2) {
						resolution = Integer.valueOf(arguments.get(2));
					}
					return ContentionProfiler.profile(commandSender, time_, resolution);
			}

			if (arguments.size() > 2) {
				world = DimensionManager.getWorld(Integer.valueOf(arguments.get(2)));
			} else if (type == ProfilingState.CHUNK_ENTITIES && commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
			if (type == ProfilingState.CHUNK_ENTITIES && x == null) {
				if (!(commandSender instanceof Entity)) {
					throw new UsageException("/profile c needs chunk arguments when used from console");
				}
				Entity entity = (Entity) commandSender;
				x = entity.chunkCoordX;
				z = entity.chunkCoordZ;
			}
		} catch (UsageException e) {
			sendChat(commandSender, "Usage: /profile [e/p/u/l/(c [chunkX] [chunk z])] timeInSeconds dimensionID\n" +
					"example - profile for 30 seconds in chunk 8,1 in all worlds: /profile c 8 1\n" +
					"example - profile for 10 seconds in dimension 4: /profile e 10 4\n" +
					"example - profile packets: /profile p");
			return true;
		}

		final List<World> worlds = new ArrayList<>();
		if (world == null) {
			Collections.addAll(worlds, DimensionManager.getWorlds());
		} else {
			worlds.add(world);
		}
		final int time = time_;
		final EntityTickProfiler entityTickProfiler = EntityTickProfiler.INSTANCE;
		if (!entityTickProfiler.startProfiling(new Runnable() {
			@Override
			public void run() {
				sendChat(commandSender, entityTickProfiler.writeStringData(new TableFormatter(commandSender)).toString());
			}
		}, type, time, worlds)) {
			sendChat(commandSender, "Someone else is currently profiling.");
		}
		if (type == ProfilingState.CHUNK_ENTITIES) {
			entityTickProfiler.setLocation(x, z);
		}
		sendChat(commandSender, "Profiling for " + time + " seconds in " + (world == null ? "all worlds " : Log.name(world))
				+ (type == ProfilingState.CHUNK_ENTITIES ? " at " + x + ',' + z : ""));
		return true;
	}

	@Override
	public String getCommandUsage(ICommandSender icommandsender) {
		return "Usage: /profile [e/(c [chunk x] [chunk z])] [time=30] [dimensionid=all]";
	}

	public enum ProfilingState {
		NONE(null, 0),
		ENTITIES("e", 30),
		CHUNK_ENTITIES("c", 30),
		PACKETS("p", 30),
		UTILISATION("u", 240),
		LOCK_CONTENTION("l", 240);

		static final Map<String, ProfilingState> states = new HashMap<>();

		static {
			for (ProfilingState p : ProfilingState.values()) {
				states.put(p.shortcut, p);
			}
		}

		final String shortcut;
		final int time;

		ProfilingState(String shortcut, int time) {
			this.shortcut = shortcut;
			this.time = time;
		}

		public static ProfilingState get(String shortcut) {
			return states.get(shortcut.toLowerCase());
		}
	}
}
