package me.nallar.tickprofiler.minecraft.entitylist;

import java.lang.reflect.Field;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class LoadedEntityList extends EntityList<Entity> {
	public LoadedEntityList(World world, Field overriddenField) {
		super(world, overriddenField);
	}

	@Override
	public void tick() {
		ENTITY_TICK_PROFILER.tick();
		ENTITY_TICK_PROFILER.runEntities(world, innerList);
	}
}
