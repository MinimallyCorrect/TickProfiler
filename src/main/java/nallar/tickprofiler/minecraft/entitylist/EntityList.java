package nallar.tickprofiler.minecraft.entitylist;

import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.commands.ProfileCommand;
import nallar.tickprofiler.minecraft.profiling.EntityTickProfiler;
import nallar.tickprofiler.util.contextaccess.ContextAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/*
* Used to override World.loadedTile/EntityList.
* */
public abstract class EntityList<T> implements Queue<T> {
	private static final ContextAccess contextAccess = ContextAccess.$;
	protected final Queue<T> innerList;
	protected final World world;
	private final Field overridenField;

	EntityList(World world, Field overriddenField) {
		this.overridenField = overriddenField;
		this.world = world;
		overriddenField.setAccessible(true);
		Queue<T> worldList = new ConcurrentLinkedQueue<T>();
		try {
			worldList = (Queue<T>) overriddenField.get(world);
			if (!"kcauldron.wrapper.ProcessingQueue".equals(worldList.getClass().getName())) {
				Log.error("Another mod has replaced an entity list with " + Log.toString(worldList));
			}
		} catch (Throwable t) {
			Log.error("Failed to get " + overriddenField.getName() + " in world " + Log.name(world), t);
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

	@Override
	public int size() {
		boolean tick = EntityTickProfiler.profilingState != ProfileCommand.ProfilingState.NONE && World.class.isAssignableFrom(contextAccess.getContext(1));
		if (tick) {
			Class secondCaller = contextAccess.getContext(2);
			if (secondCaller == MinecraftServer.class || World.class.isAssignableFrom(secondCaller)) {
				doTick();
				return 0;
			}
		}
		return innerList.size();
	}

	private void doTick() {
		try {
			tick();
		} catch (Throwable t) {
			Log.error("Caught error while profiling in TP tick hook " + this, t);
		}
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
	public Object clone() {
		return new ConcurrentLinkedQueue<T>(innerList);
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
	public boolean add(final T t) {
		return innerList.add(t);
	}

	@Override
	public boolean offer(T t) {
		return innerList.offer(t);
	}

	@Override
	public T remove() {
		return innerList.remove();
	}

	@Override
	public T poll() {
		return innerList.poll();
	}

	@Override
	public T element() {
		return innerList.element();
	}

	@Override
	public T peek() {
		return innerList.peek();
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
	public boolean removeAll(final Collection<?> c) {
		return innerList.removeAll(c);
	}

	@Override
	public boolean retainAll(final Collection<?> c) {
		return innerList.retainAll(c);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return innerList.containsAll(c);
	}

	@Override
	public Iterator<T> iterator() {
		boolean tick = EntityTickProfiler.profilingState != ProfileCommand.ProfilingState.NONE && World.class.isAssignableFrom(contextAccess.getContext(1));
		if (tick) {
			Class secondCaller = contextAccess.getContext(2);
			if (secondCaller == MinecraftServer.class || World.class.isAssignableFrom(secondCaller)) {
				doTick();
				return Collections.<T>emptyList().iterator();
			}
		}
		return innerList.iterator();
	}
}
