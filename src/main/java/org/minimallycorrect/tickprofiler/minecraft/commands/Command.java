package org.minimallycorrect.tickprofiler.minecraft.commands;

import java.util.*;

import org.minimallycorrect.tickprofiler.Log;
import org.minimallycorrect.tickprofiler.minecraft.profiling.AlreadyRunningException;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public abstract class Command extends CommandBase {
	public static void sendChat(ICommandSender commandSender, String message) {
		if (commandSender instanceof MinecraftServer) {
			Log.info('\n' + message);
			return;
		}
		while (message != null) {
			int nlIndex = message.indexOf('\n');
			String sent;
			if (nlIndex == -1) {
				sent = message;
				message = null;
			} else {
				sent = message.substring(0, nlIndex);
				message = message.substring(nlIndex + 1);
			}
			commandSender.sendMessage(new TextComponentString(sent));
		}
	}

	boolean requireOp() {
		return false;
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender commandSender) {
		return !requireOp() || super.checkPermission(server, commandSender);
	}

	@Override
	public final void execute(MinecraftServer server, ICommandSender commandSender, String[] argumentsArray) {
		try {
			processCommand(commandSender, new ArrayList<>(Arrays.asList(argumentsArray)));
		} catch (UsageException e) {
			String message = e.getMessage();
			if (message != null && !message.isEmpty())
				sendChat(commandSender, "Usage exception: " + message);
			sendChat(commandSender, getUsage(commandSender));
		} catch (AlreadyRunningException e) {
			sendChat(commandSender, e.getMessage());
		}
	}

	protected abstract void processCommand(ICommandSender commandSender, List<String> arguments);
}
