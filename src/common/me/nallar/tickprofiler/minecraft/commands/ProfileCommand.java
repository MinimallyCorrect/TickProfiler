package me.nallar.tickprofiler.minecraft.commands;

import me.nallar.tickprofiler.Log;
import me.nallar.tickprofiler.minecraft.TickProfiler;
import me.nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import me.nallar.tickprofiler.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
		boolean location = false;
		Integer x = null;
		Integer z = null;
		try {
			if ("c".equals(arguments.get(0))) {
				location = true;
				if (arguments.size() > 2) {
					x = Integer.valueOf(arguments.remove(1));
					z = Integer.valueOf(arguments.remove(1));
				}
			}
			if (arguments.size() > 1) {
				time_ = Integer.valueOf(arguments.get(1));
			}
			if (arguments.size() > 2) {
				world = DimensionManager.getWorld(Integer.valueOf(arguments.get(2)));
			} else if (location && commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
			if (location && x == null) {
				Entity entity = (Entity) commandSender;
				x = entity.chunkCoordX;
				z = entity.chunkCoordZ;
			}
		} catch (Exception e) {
			sendChat(commandSender, "Usage: /profile [e/(c [chunk x] [chunk z])] [time=30] [dimensionid=all]");
			return;
		}
		final List<World> worlds = new ArrayList<World>();
		if (world == null) {
			Collections.addAll(worlds, DimensionManager.getWorlds());
		} else {
			worlds.add(world);
		}
		final int time = time_;
		final EntityTickProfiler entityTickProfiler = EntityTickProfiler.ENTITY_TICK_PROFILER;
		if (!entityTickProfiler.startProfiling(new Runnable() {
			@Override
			public void run() {
				sendChat(commandSender, entityTickProfiler.writeStringData(new TableFormatter(commandSender)).toString());
			}
		}, location ? ProfilingState.CHUNK : ProfilingState.GLOBAL, time, worlds)) {
			sendChat(commandSender, "Someone else is currently profiling.");
		}
		if (location) {
			entityTickProfiler.setLocation(x, z);
		}
		sendChat(commandSender, "Profiling for " + time + " seconds in " + (world == null ? "all worlds " : Log.name(world)) + (location ? " at " + x + ',' + z : ""));
	}

	public static enum ProfilingState {
		NONE,
		GLOBAL,
		CHUNK
	}

	@Override
	public String getCommandUsage(ICommandSender icommandsender) {
		return "Usage: /profile [e/(c [chunk x] [chunk z])] [time=30] [dimensionid=all]";
	}
}
