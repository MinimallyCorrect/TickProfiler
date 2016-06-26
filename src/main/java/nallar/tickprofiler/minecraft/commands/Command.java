package nallar.tickprofiler.minecraft.commands;

import nallar.tickprofiler.Log;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

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
			commandSender.addChatMessage(new TextComponentString(sent));
		}
	}

	protected boolean requireOp() {
		return false;
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender commandSender) {
		return !requireOp() || super.checkPermission(server, commandSender);
	}

	@Override
	public final void execute(MinecraftServer server, ICommandSender commandSender, String... argumentsArray) {
		processCommand(commandSender, new ArrayList<>(Arrays.asList(argumentsArray)));
	}

	protected abstract void processCommand(ICommandSender commandSender, List<String> arguments);
}
