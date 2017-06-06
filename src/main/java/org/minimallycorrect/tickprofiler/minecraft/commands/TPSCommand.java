package org.minimallycorrect.tickprofiler.minecraft.commands;

import com.google.common.base.Strings;
import lombok.val;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import org.minimallycorrect.tickprofiler.Log;
import org.minimallycorrect.tickprofiler.minecraft.TickProfiler;
import org.minimallycorrect.tickprofiler.util.ChatFormat;
import org.minimallycorrect.tickprofiler.util.TableFormatter;

import java.util.*;
import java.util.concurrent.*;

public class TPSCommand extends Command {
	private static final int tpsWidth = 40;
	public static String name = "tps";

	private static String getTPSString(boolean withColour) {
		double targetTPS = 20;
		double tps = TimeUnit.SECONDS.toNanos(1) / (double) TickProfiler.tickTime;
		if (tps > 20) {
			tps = 20;
		}
		double difference = Math.abs(targetTPS - tps);
		int charsFirst = (int) Math.round((tps / targetTPS) * tpsWidth);
		int charsAfter = tpsWidth - charsFirst;
		return " " +
			TableFormatter.formatDoubleWithPrecision(tps, 2) +
			" TPS [ " +
			(withColour ? getColourForDifference(difference, targetTPS) : "") +
			Strings.repeat("#", charsFirst) +
			Strings.repeat("~", charsAfter) +
			(withColour ? ChatFormat.RESET : "") +
			" ] ";
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

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public boolean requireOp() {
		return TickProfiler.instance.requireOpForTPSCommand;
	}

	@Override
	public String getCommandUsage(ICommandSender commandSender) {
		return "Usage: /tps";
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		val server = commandSender.getServer();
		if (server == null)
			throw new RuntimeException("Could not get server instance from commandSender " + commandSender);
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
		for (World world : server.worldServers) {
			int worldEntities = world.loadedEntityList.size();
			int worldTileEntities = world.loadedTileEntityList.size();
			int worldChunks = 0;
			val provider = world.getChunkProvider();
			if (provider instanceof ChunkProviderServer)
				worldChunks = ((ChunkProviderServer) world.getChunkProvider()).getLoadedChunkCount();
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
			.row(server.getPlayerList().getCurrentPlayerCount() + " Players")
			.row(entities)
			.row(tileEntities)
			.row(chunks)
			.row(TableFormatter.formatDoubleWithPrecision((TickProfiler.tickTime * 100D) / 50000000, 2) + '%');
		tf.finishTable();
		tf.sb.append('\n').append(getTPSString(commandSender instanceof EntityPlayer));
		sendChat(commandSender, tf.toString());
	}
}
