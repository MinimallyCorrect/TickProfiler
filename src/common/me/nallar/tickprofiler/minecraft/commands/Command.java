package me.nallar.tickprofiler.minecraft.commands;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import me.nallar.tickprofiler.Log;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatMessageComponent;
import com.sperion.forgeperms.api.IPermissionManager;

import java.util.*;

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

	private static IPermissionManager permissions;

	public Boolean checkPermission(EntityPlayer entityPlayer) {
		if (permissions == null) {
			return null;
		}
		String perm = this.getClass().getName();
                return permissions.canAccess(entityPlayer.username, entityPlayer.worldObj.getProviderName(), perm);
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
			commandSender.sendChatToPlayer(new ChatMessageComponent().addText(sent));
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
                        if (mod instanceof IPermissionManager) {
                                Command.permissions = (IPermissionManager) mod;
				Log.info("Using " + Log.toString(mod) + ':' + modContainer.getModId() + " as a permissions source.");
				return;
			}
		}
	}
}
