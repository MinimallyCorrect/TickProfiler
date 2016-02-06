package nallar.tickprofiler.minecraft.commands;

import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.TickProfiler;
import nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import nallar.tickprofiler.minecraft.profiling.PacketProfiler;
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
		World world = null;
		int time_ = 30;
		ProfilingState type = null;
		Integer x = null;
		Integer z = null;
		try {
			switch (arguments.get(0).toLowerCase()) {
				case "c":
					type = ProfilingState.CHUNK_ENTITIES;
					if (arguments.size() > 2) {
						x = Integer.valueOf(arguments.remove(1));
						z = Integer.valueOf(arguments.remove(1));
					}
					break;
				case "p":
					type = ProfilingState.PACKETS;
					break;
				case "e":
				default:
					type = ProfilingState.ENTITIES;
					break;
			}
			if (arguments.size() > 1) {
				time_ = Integer.valueOf(arguments.get(1));
			}
			if (arguments.size() > 2) {
				world = DimensionManager.getWorld(Integer.valueOf(arguments.get(2)));
			} else if (type == ProfilingState.CHUNK_ENTITIES && commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
			if (type == ProfilingState.CHUNK_ENTITIES && x == null) {
				Entity entity = (Entity) commandSender;
				x = entity.chunkCoordX;
				z = entity.chunkCoordZ;
			}
		} catch (Exception e) {
			sendChat(commandSender, "Usage: /profile [e/p/(c [chunkX] [chunk z])] timeInSeconds dimensionID\n" +
					"example - profile for 30 seconds in chunk 8,1 in all worlds: /profile c 8 1\n" +
					"example - profile for 10 seconds in dimension 4: /profile e 10 4\n" +
					"example - profile packets: /profile p");
			return;
		}

		if (type == ProfilingState.PACKETS) {
			PacketProfiler.startProfiling(commandSender, time_);
			return;
		}

		final List<World> worlds = new ArrayList<World>();
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
	}

	public enum ProfilingState {
		NONE,
		ENTITIES,
		CHUNK_ENTITIES,
		PACKETS
	}

	@Override
	public String getCommandUsage(ICommandSender icommandsender) {
		return "Usage: /profile [e/(c [chunk x] [chunk z])] [time=30] [dimensionid=all]";
	}
}
