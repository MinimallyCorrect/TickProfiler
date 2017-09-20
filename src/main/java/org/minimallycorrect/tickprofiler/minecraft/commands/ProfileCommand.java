package org.minimallycorrect.tickprofiler.minecraft.commands;

import lombok.val;
import net.minecraft.command.ICommandSender;
import org.minimallycorrect.tickprofiler.minecraft.TickProfiler;
import org.minimallycorrect.tickprofiler.minecraft.profiling.Profile;

import java.util.*;

public class ProfileCommand extends Command {
	public static String name = "profile";

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean requireOp() {
		return TickProfiler.instance.requireOpForProfileCommand;
	}

	@Override
	public void processCommand(final ICommandSender commandSender, List<String> arguments) {
		process(commandSender, arguments);
	}

	private void addPreParameters(Parameters p) {
		p.order(Collections.singletonList("type"));
	}

	private void addPostParameters(Parameters p) {
		p.orderWithDefault(Arrays.asList("output", "commandsender"));
	}

	private void process(final ICommandSender commandSender, List<String> arguments) {
		val p = new Parameters(arguments);
		addPreParameters(p);
		p.checkUsage(true);
		val typeName = p.getString("type");
		val type = Profile.Types.byName(typeName);

		if (type == null)
			throw new UsageException("Unknown profiling type " + typeName);

		type.addParameters(p);
		addPostParameters(p);
		p.checkUsage(false);
		String output = p.getString("output");
		val targets = new ArrayList<Profile.ProfileTarget>();
		for (String s : output.split(",")) {
			switch (s.toLowerCase()) {
				case "commandsender":
					targets.add(Profile.ProfileTarget.commandSender(commandSender));
					break;
				case "console":
					targets.add(Profile.ProfileTarget.console());
					break;
				default:
					throw new UsageException("Unknown output: " + output);
			}
		}

		val profile = type.create();
		try {
			profile.start(targets, p);
		} finally {
			profile.closeIfNeeded();
		}
	}

	@Override
	public String getUsage(ICommandSender icommandsender) {
		val sb = new StringBuilder("Usage:\n");
		for (val type : Profile.Types.values()) {
			val p = new Parameters(Collections.singletonList(type.shortName));
			type.addParameters(p);
			addPostParameters(p);
			sb.append(type.name()).append(": ").append("/profile ").append(type.shortName);
			p.writeExpectedParameters(sb);
			sb.append('\n');
		}
		return sb.toString();
	}
}
