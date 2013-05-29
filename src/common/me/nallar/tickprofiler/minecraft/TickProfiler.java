package me.nallar.tickprofiler.minecraft;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import me.nallar.tickprofiler.Log;
import me.nallar.tickprofiler.minecraft.commands.Command;
import me.nallar.tickprofiler.minecraft.commands.DumpCommand;
import me.nallar.tickprofiler.minecraft.commands.ProfileCommand;
import me.nallar.tickprofiler.minecraft.entitylist.EntityList;
import me.nallar.tickprofiler.minecraft.entitylist.LoadedEntityList;
import me.nallar.tickprofiler.minecraft.entitylist.LoadedTileEntityList;
import me.nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import me.nallar.tickprofiler.reporting.Metrics;
import me.nallar.tickprofiler.util.ReflectUtil;
import me.nallar.tickprofiler.util.TableFormatter;
import me.nallar.tickprofiler.util.VersionUtil;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.PacketCount;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@SuppressWarnings ("WeakerAccess")
@Mod (modid = "TickProfiler", name = "TickProfiler", version = "@MOD_VERSION@")
@NetworkMod (clientSideRequired = false, serverSideRequired = false)
public class TickProfiler {
	@Mod.Instance
	public static TickProfiler instance;
	private static final int loadedEntityFieldIndex = 0;
	private static final int loadedTileEntityFieldIndex = 2;
	public boolean requireOpForProfileCommand = true;
	public boolean requireOpForDumpCommand = true;
	private int profilingInterval = 0;
	private String profilingFileName = "profile.txt";

	static {
		new Metrics("TickProfiler", VersionUtil.versionNumber());
	}

	public TickProfiler() {
		Log.LOGGER.getLevel(); // Force log class to load
	}

	@Mod.Init
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		initPeriodicProfiling();
	}

	private void initPeriodicProfiling() {
		final int profilingInterval = this.profilingInterval;
		if (profilingInterval == 0) {
			return;
		}
		TickRegistry.registerScheduledTickHandler(new ProfilingScheduledTickHandler(profilingInterval, MinecraftServer.getServer().getFile(profilingFileName)), Side.SERVER);
	}

	@SuppressWarnings ("FieldRepeatedlyAccessedInMethod")
	@Mod.PreInit
	public void preInit(FMLPreInitializationEvent event) {
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		String GENERAL = Configuration.CATEGORY_GENERAL;

		ProfileCommand.name = config.get(GENERAL, "profileCommandName", ProfileCommand.name, "Name of the command to be used for profiling reports.").value;
		DumpCommand.name = config.get(GENERAL, "dumpCommandName", DumpCommand.name, "Name of the command to be used for profiling reports.").value;
		requireOpForProfileCommand = config.get(GENERAL, "requireOpForProfileCommand", requireOpForProfileCommand, "If a player must be opped to use /profile").getBoolean(requireOpForProfileCommand);
		requireOpForDumpCommand = config.get(GENERAL, "requireOpForProfileCommand", requireOpForDumpCommand, "If a player must be opped to use /dump").getBoolean(requireOpForDumpCommand);
		profilingInterval = config.get(GENERAL, "profilingInterval", profilingInterval, "Interval, in minutes, to record profiling information to disk. 0 = never. Recommended >= 5.").getInt();
		profilingFileName = config.get(GENERAL, "profilingFileName", profilingFileName, "Location to store profiling information to. For example, why not store it in a computercraft computer's folder?").value;
		config.save();
		PacketCount.allowCounting = false;
	}

	@Mod.ServerStarting
	public void serverStarting(FMLServerStartingEvent event) {
		ServerCommandManager serverCommandManager = (ServerCommandManager) event.getServer().getCommandManager();
		serverCommandManager.registerCommand(new ProfileCommand());
		serverCommandManager.registerCommand(new DumpCommand());
		Command.checkForPermissions();
	}

	public synchronized void hookProfiler(World world) {
		if (world.isRemote) {
			Log.severe("World " + Log.name(world) + " seems to be a client world", new Throwable());
		}
		try {
			Field loadedTileEntityField = ReflectUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			new LoadedTileEntityList(world, loadedTileEntityField);
			Field loadedEntityField = ReflectUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
			new LoadedEntityList(world, loadedEntityField);
			Log.finer("Profiling hooked for world " + Log.name(world));
		} catch (Exception e) {
			Log.severe("Failed to initialise profiling for world " + Log.name(world), e);
		}
	}

	public synchronized void unhookProfiler(World world) {
		if (world.isRemote) {
			Log.severe("World " + Log.name(world) + " seems to be a client world", new Throwable());
		}
		try {
			Field loadedTileEntityField = ReflectUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			Object loadedTileEntityList = loadedTileEntityField.get(world);
			if (loadedTileEntityList instanceof EntityList) {
				((EntityList) loadedTileEntityList).unhook();
			} else {
				Log.severe("Looks like another mod broke TickProfiler's replacement tile entity list in world: " + Log.name(world));
			}
			Field loadedEntityField = ReflectUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
			Object loadedEntityList = loadedEntityField.get(world);
			if (loadedEntityList instanceof EntityList) {
				((EntityList) loadedEntityList).unhook();
			} else {
				Log.severe("Looks like another mod broke TickProfiler's replacement entity list in world: " + Log.name(world));
			}
			Log.finer("Profiling unhooked for world " + Log.name(world));
		} catch (Exception e) {
			Log.severe("Failed to unload TickProfiler for world " + Log.name(world), e);
		}
	}

	@ForgeSubscribe
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
			EntityPlayer entityPlayer = event.entityPlayer;
			ItemStack usedItem = entityPlayer.getCurrentEquippedItem();
			if (usedItem != null) {
				Item usedItemType = usedItem.getItem();
				if (usedItemType == Item.pocketSundial && (!requireOpForDumpCommand || entityPlayer.canCommandSenderUseCommand(4, "dump"))) {
					Command.sendChat(entityPlayer, DumpCommand.dump(new TableFormatter(entityPlayer), entityPlayer.worldObj, event.x, event.y, event.z, 35).toString());
					event.setCanceled(true);
				}
			}
		}
	}

	private static class ProfilingScheduledTickHandler implements IScheduledTickHandler {
		private static final EnumSet<TickType> TICKS = EnumSet.of(TickType.SERVER);
		private final int profilingInterval;
		private final File profilingFile;
		private int counter = 0;

		public ProfilingScheduledTickHandler(final int profilingInterval, final File profilingFile) {
			this.profilingInterval = profilingInterval;
			this.profilingFile = profilingFile;
		}

		@Override
		public int nextTickSpacing() {
			return 1;
		}

		@Override
		public void tickStart(final EnumSet<TickType> type, final Object... tickData) {
			final EntityTickProfiler entityTickProfiler = EntityTickProfiler.ENTITY_TICK_PROFILER;
			entityTickProfiler.tick();
			if (counter++ % profilingInterval * 60 * 20 != 0) {
				return;
			}
			entityTickProfiler.startProfiling(new Runnable() {
				@Override
				public void run() {
					try {
						TableFormatter tf = new TableFormatter(MinecraftServer.getServer());
						tf.tableSeparator = "\n";
						Files.write(entityTickProfiler.writeData(tf).toString(), profilingFile, Charsets.UTF_8);
					} catch (Throwable t) {
						Log.severe("Failed to save periodic profiling data to " + profilingFile, t);
					}
				}
			}, ProfileCommand.ProfilingState.GLOBAL, 20, Arrays.<World>asList(DimensionManager.getWorlds()));
		}

		@Override
		public void tickEnd(final EnumSet<TickType> type, final Object... tickData) {
		}

		@Override
		public EnumSet<TickType> ticks() {
			return TICKS;
		}

		@Override
		public String getLabel() {
			return "TickProfiler scheduled profiling handler";
		}
	}
}
