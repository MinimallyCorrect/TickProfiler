package me.nallar.tickprofiler.minecraft.entitylist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import me.nallar.tickprofiler.Log;
import me.nallar.tickprofiler.minecraft.commands.ProfileCommand;
import me.nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import me.nallar.tickprofiler.util.contextaccess.ContextAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/*
* Used to override World.loadedTile/EntityList.
* */
public abstract class EntityList<T> extends ArrayList<T> {
	public static final EntityTickProfiler ENTITY_TICK_PROFILER = new EntityTickProfiler();
	public static ProfileCommand.ProfilingState profilingState = ProfileCommand.ProfilingState.NONE;
	private static final ContextAccess contextAccess = ContextAccess.$;
	protected final ArrayList<T> innerList;
	protected final World world;
	private final Field overridenField;

	EntityList(World world, Field overriddenField) {
		this.overridenField = overriddenField;
		this.world = world;
		overriddenField.setAccessible(true);
		ArrayList<T> worldList = new ArrayList<T>();
		try {
			worldList = (ArrayList<T>) overriddenField.get(world);
			if (worldList.getClass() != ArrayList.class) {
				Log.severe("Another mod has replaced an entity list with " + Log.toString(worldList));
			}
		} catch (Throwable t) {
			Log.severe("Failed to get " + overriddenField.getName() + " in world " + Log.name(world));
		}
		innerList = worldList;
		try {
			overriddenField.set(world, this);
		} catch (Exception e) {
			throw new RuntimeException("Failed to override " + overriddenField.getName() + " in world " + Log.name(world), e);
		}
	}

	public void unhook() throws IllegalAccessException {
		overridenField.set(world, innerList);
	}

	public abstract void tick();

	public static synchronized boolean startProfiling(ProfileCommand.ProfilingState profilingState_) {
		if (profilingState != ProfileCommand.ProfilingState.NONE) {
			return false;
		}
		profilingState = profilingState_;
		return true;
	}

	public static synchronized void endProfiling() {
		profilingState = ProfileCommand.ProfilingState.NONE;
	}

	@Override
	public void trimToSize() {
		innerList.trimToSize();
	}

	@Override
	public void ensureCapacity(final int minCapacity) {
		innerList.ensureCapacity(minCapacity);
	}

	@Override
	public int size() {
		if (profilingState == ProfileCommand.ProfilingState.NONE || !World.class.isAssignableFrom(contextAccess.getContext(1)) || !World.class.isAssignableFrom(contextAccess.getContext(2))) {
			return innerList.size();
		}
		tick();
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return innerList.isEmpty();
	}

	@Override
	public boolean contains(final Object o) {
		return innerList.contains(o);
	}

	@Override
	public int indexOf(final Object o) {
		return innerList.indexOf(o);
	}

	@Override
	public int lastIndexOf(final Object o) {
		return innerList.lastIndexOf(o);
	}

	@Override
	public Object clone() {
		return innerList.clone();
	}

	@Override
	public Object[] toArray() {
		return innerList.toArray();
	}

	@Override
	public <T1> T1[] toArray(final T1[] a) {
		return innerList.toArray(a);
	}

	@Override
	public T get(final int index) {
		return innerList.get(index);
	}

	@Override
	public T set(final int index, final T element) {
		return innerList.set(index, element);
	}

	@Override
	public boolean add(final T t) {
		return innerList.add(t);
	}

	@Override
	public void add(final int index, final T element) {
		innerList.add(index, element);
	}

	@Override
	public T remove(final int index) {
		return innerList.remove(index);
	}

	@Override
	public boolean remove(final Object o) {
		return innerList.remove(o);
	}

	@Override
	public void clear() {
		innerList.clear();
	}

	@Override
	public boolean addAll(final Collection<? extends T> c) {
		return innerList.addAll(c);
	}

	@Override
	public boolean addAll(final int index, final Collection<? extends T> c) {
		return innerList.addAll(index, c);
	}

	@Override
	public boolean removeAll(final Collection<?> c) {
		return innerList.removeAll(c);
	}

	@Override
	public boolean retainAll(final Collection<?> c) {
		return innerList.retainAll(c);
	}

	@Override
	public ListIterator<T> listIterator(final int index) {
		return innerList.listIterator(index);
	}

	@Override
	public ListIterator<T> listIterator() {
		return innerList.listIterator();
	}

	@Override
	public Iterator<T> iterator() {
		if (profilingState == ProfileCommand.ProfilingState.NONE || !World.class.isAssignableFrom(contextAccess.getContext(1)) || !World.class.isAssignableFrom(contextAccess.getContext(2))) {
			return innerList.iterator();
		}
		tick();
		return Collections.<T>emptyList().iterator();
	}

	@Override
	public List<T> subList(final int fromIndex, final int toIndex) {
		return innerList.subList(fromIndex, toIndex);
	}
}
