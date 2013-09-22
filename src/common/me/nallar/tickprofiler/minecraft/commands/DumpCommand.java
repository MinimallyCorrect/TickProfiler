package me.nallar.tickprofiler.minecraft.commands;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import me.nallar.tickprofiler.Log;
import me.nallar.tickprofiler.minecraft.TickProfiler;
import me.nallar.tickprofiler.util.BlockInfo;
import me.nallar.tickprofiler.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class DumpCommand extends Command {

    public static String name = "dump";

    @Override
    public String getCommandUsage(ICommandSender icommandsender) {
        return "what.the.hell.goes.here";
    }

    @Override
    public String getCommandName() {
        return name;
    }

    @Override
    public boolean requireOp() {
        return TickProfiler.instance.requireOpForDumpCommand;
    }

    @Override
    public void processCommand(final ICommandSender commandSender, List<String> arguments) {
        World world = DimensionManager.getWorld(0);
        int x = 0;
        int y = 0;
        int z = 0;
        try {
            if (commandSender instanceof Entity) {
                world = ((Entity) commandSender).worldObj;
            }
            x = Integer.parseInt(arguments.remove(0));
            y = Integer.parseInt(arguments.remove(0));
            z = Integer.parseInt(arguments.remove(0));
            if (!arguments.isEmpty()) {
                world = DimensionManager.getWorld(Integer.parseInt(arguments.remove(0)));
            }
        } catch (Exception e) {
            world = null;
        }
        if (world == null) {
            sendChat(commandSender, "Usage: /dump x y z [world=currentworld]");
        }
        sendChat(commandSender, dump(new TableFormatter(commandSender), world, x, y, z, commandSender instanceof Entity ? 35 : 70).toString());
    }

    public static TableFormatter dump(TableFormatter tf, World world, int x, int y, int z, int maxLen) {
        @SuppressWarnings("MismatchedQueryAndUpdateOfStringBuilder")
        StringBuilder sb = tf.sb;
        int blockId = world.getBlockId(x, y, z);
        if (blockId < 1) {
            sb.append("No block at ").append(Log.name(world)).append(" x,y,z").append(x).append(',').append(y).append(',').append(z).append('\n');
        } else {
            int metaData = world.getBlockMetadata(x, y, z);
            BlockInfo blockInfo = new BlockInfo(blockId, metaData);
            sb.append(blockId).append(':').append(blockInfo.name).append(':').append(metaData).append('\n');
        }
        sb.append("World time: ").append(world.getWorldTime()).append('\n');
        TileEntity toDump = world.getBlockTileEntity(x, y, z);
        if (toDump == null) {
            sb.append("No tile entity at ").append(Log.name(world)).append(" x,y,z").append(x).append(',').append(y).append(',').append(z).append('\n');
            return tf;
        }
        dump(tf, toDump, maxLen);
        return tf;
    }

    private static void dump(TableFormatter tf, Object toDump, int maxLen) {
        tf.sb.append(toDump.getClass().getName()).append('\n');
        tf
                .heading("Field")
                .heading("Value");
        Class<?> clazz = toDump.getClass();
        do {
            for (Field field : clazz.getDeclaredFields()) {
                if ((field.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
                    continue;
                }
                field.setAccessible(true);
                tf.row(field.getName());
                try {
                    String value = String.valueOf(field.get(toDump));
                    tf.row(value.substring(0, Math.min(value.length(), maxLen)));
                } catch (IllegalAccessException e) {
                    tf.row(e.getMessage());
                }
            }
        } while ((clazz = clazz.getSuperclass()) != Object.class);
        tf.finishTable();
    }
}
