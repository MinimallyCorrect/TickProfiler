package me.nallar.tickprofiler.minecraft.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.nallar.tickprofiler.Log;
import me.nallar.tickprofiler.minecraft.TickProfiler;
import me.nallar.tickprofiler.minecraft.entitylist.EntityList;
import me.nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import me.nallar.tickprofiler.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

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
		long time_ = 30;
		boolean location = false;
		Integer x = null;
		Integer z = null;
		try {
			if (arguments.isEmpty()) {
				throw new Exception();
			}
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
			} else if (commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
		} catch (Exception e) {
			sendChat(commandSender, "Usage: /profile e/(c [chunk x] [chunk z])] [time=30] [dimensionid=all]");
			return;
		}
		final List<World> worlds = new ArrayList<World>();
		if (world == null) {
			Collections.addAll(worlds, DimensionManager.getWorlds());
		} else {
			worlds.add(world);
		}
		final long time = time_;
		synchronized (EntityList.class) {
			if (!EntityList.startProfiling(location ? ProfilingState.CHUNK : ProfilingState.GLOBAL)) {
				sendChat(commandSender, "Someone else is currently profiling.");
				return;
			}
			for (World world_ : worlds) {
				TickProfiler.instance.hookProfiler(world_);
			}
			EntityTickProfiler entityTickProfiler = EntityList.ENTITY_TICK_PROFILER;
			if (location) {
				entityTickProfiler.setLocation(x, z);
			}
		}
		Runnable profilingRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000 * time);
				} catch (InterruptedException ignored) {
				}

				synchronized (EntityList.class) {
					EntityList.endProfiling();
					sendChat(commandSender, EntityList.ENTITY_TICK_PROFILER.writeData(new TableFormatter(commandSender)).toString());
					EntityList.ENTITY_TICK_PROFILER.clear();
					for (World world_ : worlds) {
						TickProfiler.instance.unhookProfiler(world_);
					}
				}
			}
		};
		Thread profilingThread = new Thread(profilingRunnable);
		profilingThread.setName("TickProfiler");
		sendChat(commandSender, "Profiling for " + time + " seconds in " + (world == null ? "all worlds " : Log.name(world)) + (location ? " at " + x + ',' + z : ""));
		profilingThread.start();
	}

	public static enum ProfilingState {
		NONE,
		GLOBAL,
		CHUNK
	}
}
