package nallar.tickprofiler.minecraft.commands;

import com.google.common.base.Strings;
import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.TickProfiler;
import nallar.tickprofiler.util.ChatFormat;
import nallar.tickprofiler.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.*;

public class TPSCommand extends Command {
	public static String name = "tps";

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public String getCommandUsage(ICommandSender commandSender) {
		return "Usage: /tps";
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		TableFormatter tf = new TableFormatter(commandSender);
		int entities = 0;
		int tileEntities = 0;
		int chunks = 0;
		tf
				.heading("")
				.heading("E")
				.heading("TE")
				.heading("C")
				.heading("");
		for (World world : MinecraftServer.getServer().worldServers) {
			int worldEntities = world.loadedEntityList.size();
			int worldTileEntities = world.loadedTileEntityList.size();
			int worldChunks = world.getChunkProvider().getLoadedChunkCount();
			entities += worldEntities;
			tileEntities += worldTileEntities;
			chunks += worldChunks;
			tf
					.row(Log.name(world))
					.row(worldEntities)
					.row(worldTileEntities)
					.row(worldChunks)
					.row("")
			;
		}
		tf
				.row(MinecraftServer.getServer().getConfigurationManager().getCurrentPlayerCount() + " Players")
				.row(entities)
				.row(tileEntities)
				.row(chunks)
				.row(TableFormatter.formatDoubleWithPrecision((TickProfiler.tickTime * 100D) / 50000000, 2) + '%');
		tf.finishTable();
		tf.sb.append('\n').append(getTPSString(commandSender instanceof EntityPlayer));
		sendChat(commandSender, tf.toString());
	}

	private static final int tpsWidth = 40;

	private static String getTPSString(boolean withColour) {
		double targetTPS = 20;
		double tps = TimeUnit.SECONDS.toNanos(1) / (double) TickProfiler.tickTime;
		if (tps > 20) {
			tps = 20;
		}
		double difference = Math.abs(targetTPS - tps);
		int charsFirst = (int) Math.round((tps / targetTPS) * tpsWidth);
		int charsAfter = tpsWidth - charsFirst;
		StringBuilder sb = new StringBuilder();
		sb
				.append(' ')
				.append(TableFormatter.formatDoubleWithPrecision(tps, 2))
				.append(" TPS [ ")
				.append(withColour ? getColourForDifference(difference, targetTPS) : "")
				.append(Strings.repeat("#", charsFirst))
				.append(Strings.repeat("~", charsAfter))
				.append(withColour ? ChatFormat.RESET : "")
				.append(" ] ");
		return sb.toString();
	}

	private static String getColourForDifference(double difference, double targetTPS) {
		switch ((int) (difference / (targetTPS / 4))) {
			case 0:
				return ChatFormat.GREEN.toString();
			case 1:
				return ChatFormat.YELLOW.toString();
			case 2:
				return ChatFormat.RED.toString();
			case 3:
				return ChatFormat.RED.toString() + ChatFormat.BOLD;
			default:
				return ChatFormat.MAGIC.toString();
		}
	}
}
