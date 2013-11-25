package me.nallar.tickprofiler.minecraft.entitylist;

import me.nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.lang.reflect.Field;

public class LoadedTileEntityList extends EntityList<TileEntity> {
	public LoadedTileEntityList(World world, Field overriddenField) {
		super(world, overriddenField);
	}

	@Override
	public void tick() {
		EntityTickProfiler.ENTITY_TICK_PROFILER.runTileEntities(world, innerList);
	}
}
