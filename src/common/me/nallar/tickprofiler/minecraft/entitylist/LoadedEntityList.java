package me.nallar.tickprofiler.minecraft.entitylist;

import me.nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.lang.reflect.Field;

public class LoadedEntityList extends EntityList<Entity> {
	public LoadedEntityList(World world, Field overriddenField) {
		super(world, overriddenField);
	}

	@Override
	public void tick() {
		EntityTickProfiler.ENTITY_TICK_PROFILER.runEntities(world, innerList);
	}
}
