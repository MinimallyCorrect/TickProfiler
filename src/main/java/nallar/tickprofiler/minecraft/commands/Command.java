package nallar.tickprofiler.minecraft.commands;

import nallar.tickprofiler.Log;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.util.*;

public abstract class Command extends CommandBase {
	protected boolean requireOp() {
		return false;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender commandSender) {
		return !requireOp() || super.canCommandSenderUseCommand(commandSender);
	}

	public static void sendChat(ICommandSender commandSender, String message) {
		if (commandSender == MinecraftServer.getServer()) {
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
			commandSender.addChatMessage(new ChatComponentText(sent));
		}
	}

	@Override
	public final void processCommand(ICommandSender commandSender, String... argumentsArray) {
		processCommand(commandSender, new ArrayList<String>(Arrays.asList(argumentsArray)));
	}

	protected abstract void processCommand(ICommandSender commandSender, List<String> arguments);
}
