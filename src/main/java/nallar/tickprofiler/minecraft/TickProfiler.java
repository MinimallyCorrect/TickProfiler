package nallar.tickprofiler.minecraft;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.commands.Command;
import nallar.tickprofiler.minecraft.commands.DumpCommand;
import nallar.tickprofiler.minecraft.commands.ProfileCommand;
import nallar.tickprofiler.minecraft.entitylist.EntityList;
import nallar.tickprofiler.minecraft.entitylist.LoadedEntityList;
import nallar.tickprofiler.minecraft.entitylist.LoadedTileEntityList;
import nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import nallar.tickprofiler.reporting.Metrics;
import nallar.tickprofiler.util.ReflectUtil;
import nallar.tickprofiler.util.TableFormatter;
import nallar.tickprofiler.util.VersionUtil;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

@SuppressWarnings("WeakerAccess")
@Mod(modid = "TickProfiler", name = "TickProfiler", version = "1.7.10", acceptableRemoteVersions = "*")
public class TickProfiler {
	@Mod.Instance("TickProfiler")
	public static TickProfiler instance;
	private static final int loadedEntityFieldIndex = 0;
	private static final int loadedTileEntityFieldIndex = 2;
	public boolean requireOpForProfileCommand = true;
	public boolean requireOpForDumpCommand = true;
	private int profilingInterval = 0;
	private String profilingFileName = "world/computer/<computer id>/profile.txt";
	private boolean profilingJson = false;

	static {
		new Metrics("TickProfiler", VersionUtil.versionNumber());
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		FMLCommonHandler.instance().bus().register(new ProfilingScheduledTickHandler(profilingInterval, new File(".", profilingFileName), profilingJson));
	}

	@SuppressWarnings("FieldRepeatedlyAccessedInMethod")
	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		String GENERAL = Configuration.CATEGORY_GENERAL;

		ProfileCommand.name = config.get(GENERAL, "profileCommandName", ProfileCommand.name, "Name of the command to be used for profiling reports.").getString();
		DumpCommand.name = config.get(GENERAL, "dumpCommandName", DumpCommand.name, "Name of the command to be used for profiling reports.").getString();
		requireOpForProfileCommand = config.get(GENERAL, "requireOpForProfileCommand", requireOpForProfileCommand, "If a player must be opped to use /profile").getBoolean(requireOpForProfileCommand);
		requireOpForDumpCommand = config.get(GENERAL, "requireOpForProfileCommand", requireOpForDumpCommand, "If a player must be opped to use /dump").getBoolean(requireOpForDumpCommand);
		profilingInterval = config.get(GENERAL, "profilingInterval", profilingInterval, "Interval, in minutes, to record profiling information to disk. 0 = never. Recommended >= 2.").getInt();
		profilingFileName = config.get(GENERAL, "profilingFileName", profilingFileName, "Location to store profiling information to, relative to the server folder. For example, why not store it in a computercraft computer's folder?").getString();
		profilingJson = config.get(GENERAL, "profilingJson", profilingJson, "Whether to write periodic profiling in JSON format").getBoolean(profilingJson);
		config.save();
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		ServerCommandManager serverCommandManager = (ServerCommandManager) event.getServer().getCommandManager();
		serverCommandManager.registerCommand(new ProfileCommand());
		serverCommandManager.registerCommand(new DumpCommand());
	}

	public synchronized void hookProfiler(World world) {
		if (world.isRemote) {
			Log.error("World " + Log.name(world) + " seems to be a client world", new Throwable());
		}
		try {
			Field loadedTileEntityField = ReflectUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			new LoadedTileEntityList(world, loadedTileEntityField);
			Field loadedEntityField = ReflectUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
			new LoadedEntityList(world, loadedEntityField);
			Log.trace("Profiling hooked for world " + Log.name(world));
		} catch (Exception e) {
			Log.error("Failed to initialise profiling for world " + Log.name(world), e);
		}
	}

	public synchronized void unhookProfiler(World world) {
		if (world.isRemote) {
			Log.error("World " + Log.name(world) + " seems to be a client world", new Throwable());
		}
		try {
			Field loadedTileEntityField = ReflectUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			Object loadedTileEntityList = loadedTileEntityField.get(world);
			if (loadedTileEntityList instanceof EntityList) {
				((EntityList) loadedTileEntityList).unhook();
			} else {
				Log.error("Looks like another mod broke TickProfiler's replacement tile entity list in world: " + Log.name(world));
			}
			Field loadedEntityField = ReflectUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
			Object loadedEntityList = loadedEntityField.get(world);
			if (loadedEntityList instanceof EntityList) {
				((EntityList) loadedEntityList).unhook();
			} else {
				Log.error("Looks like another mod broke TickProfiler's replacement entity list in world: " + Log.name(world));
			}
			Log.trace("Profiling unhooked for world " + Log.name(world));
		} catch (Exception e) {
			Log.error("Failed to unload TickProfiler for world " + Log.name(world), e);
		}
	}

	@SubscribeEvent
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
			EntityPlayer entityPlayer = event.entityPlayer;
			ItemStack usedItem = entityPlayer.getCurrentEquippedItem();
			if (usedItem != null) {
				Item usedItemType = usedItem.getItem();
				if (usedItemType == Items.clock && (!requireOpForDumpCommand || entityPlayer.canCommandSenderUseCommand(4, "dump"))) {
					Command.sendChat(entityPlayer, DumpCommand.dump(new TableFormatter(entityPlayer), entityPlayer.worldObj, event.x, event.y, event.z, 35).toString());
					event.setCanceled(true);
				}
			}
		}
	}

	public static class ProfilingScheduledTickHandler {
		private final int profilingInterval;
		private final File profilingFile;
		private final boolean json;
		private int counter = 0;

		public ProfilingScheduledTickHandler(final int profilingInterval, final File profilingFile, final boolean json) {
			this.profilingInterval = profilingInterval;
			this.profilingFile = profilingFile;
			this.json = json;
		}

		@SubscribeEvent
		public void tick(TickEvent.ServerTickEvent tick) {
			if (tick.phase != TickEvent.Phase.START) {
				return;
			}
			final EntityTickProfiler entityTickProfiler = EntityTickProfiler.ENTITY_TICK_PROFILER;
			entityTickProfiler.tick();
			int profilingInterval = this.profilingInterval;
			if (profilingInterval <= 0 || counter++ % (profilingInterval * 60 * 20) != 0) {
				return;
			}
			entityTickProfiler.startProfiling(new Runnable() {
				@Override
				public void run() {
					try {
						TableFormatter tf = new TableFormatter(MinecraftServer.getServer());
						tf.tableSeparator = "\n";
						if (json) {
							entityTickProfiler.writeJSONData(profilingFile);
						} else {
							Files.write(entityTickProfiler.writeStringData(tf, 6).toString(), profilingFile, Charsets.UTF_8);
						}
					} catch (Throwable t) {
						Log.error("Failed to save periodic profiling data to " + profilingFile, t);
					}
				}
			}, ProfileCommand.ProfilingState.GLOBAL, 10, Arrays.<World>asList(DimensionManager.getWorlds()));
		}
	}
}
