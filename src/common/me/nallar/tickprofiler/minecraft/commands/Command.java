package me.nallar.tickprofiler.minecraft.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import me.nallar.tickprofiler.Log;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatMessageComponent;
import ru.tehkode.permissions.IPermissions;

public abstract class Command extends CommandBase {
	protected boolean requireOp() {
		return false;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender commandSender) {
		Boolean permission = null;
		if (commandSender instanceof EntityPlayer) {
			permission = checkPermission((EntityPlayer) commandSender);
		}
		if (permission != null) {
			return permission;
		}
		return !requireOp() || super.canCommandSenderUseCommand(commandSender);
	}

	private static IPermissions permissions;

	public Boolean checkPermission(EntityPlayer entityPlayer) {
		if (permissions == null) {
			return null;
		}
		return permissions.has(entityPlayer, this.getClass().getName());
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
			commandSender.sendChatToPlayer(ChatMessageComponent.createFromText(sent));
		}
	}

	@Override
	public final void processCommand(ICommandSender commandSender, String... argumentsArray) {
		processCommand(commandSender, new ArrayList<String>(Arrays.asList(argumentsArray)));
	}

	protected abstract void processCommand(ICommandSender commandSender, List<String> arguments);

	public static void checkForPermissions() {
		for (ModContainer modContainer : Loader.instance().getActiveModList()) {
			Object mod = modContainer.getMod();
			if (mod instanceof IPermissions) {
				Command.permissions = (IPermissions) mod;
				Log.info("Using " + Log.toString(mod) + ':' + modContainer.getModId() + " as a permissions source.");
				return;
			}
		}
	}
}
