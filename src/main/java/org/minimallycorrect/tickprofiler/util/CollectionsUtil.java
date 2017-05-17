package org.minimallycorrect.tickprofiler.util;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;
import org.minimallycorrect.tickprofiler.Log;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public enum CollectionsUtil {
	;
	private static final String defaultDelimiter = ",";

	public static List<String> split(String input) {
		return split(input, defaultDelimiter);
	}

	public static <A, B> List<A> newList(List<B> input, Function<B, A> function) {
		return input.stream().map(function).collect(Collectors.toList());
	}

	private static List<String> split(String input, String delimiter) {
		if (input == null || input.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayList<>(Arrays.asList(input.split(delimiter)));
	}

	public static <T> List<T> toObjects(Iterable<String> stringIterable, Class<T> type) {
		Constructor<T> constructor;
		try {
			constructor = type.getConstructor(String.class);
		} catch (NoSuchMethodException e) {
			Log.error("Failed to convert string list to " + type, e);
			return Collections.emptyList();
		}
		List<T> objects = new ArrayList<>();
		for (String s : stringIterable) {
			try {
				objects.add(constructor.newInstance(s));
			} catch (Exception e) {
				Log.error("Failed to convert string list to " + type + " with string " + s, e);
			}
		}
		return objects;
	}

	public static String join(Iterable<File> iterable) {
		return join(iterable, defaultDelimiter);
	}

	public static String join(Iterable iterable, String delimiter) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean join = false;
		for (Object o : iterable) {
			if (join) {
				stringBuilder.append(delimiter);
			}
			stringBuilder.append(o);
			join = true;
		}
		return stringBuilder.toString();
	}

	public static String joinMap(Map<?, ?> map) {
		if (map.isEmpty()) {
			return "";
		}
		StringBuilder stringBuilder = new StringBuilder();
		boolean notFirst = false;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (notFirst) {
				stringBuilder.append(',');
			}
			stringBuilder.append(entry.getKey().toString()).append(':').append(entry.getValue().toString());
			notFirst = true;
		}
		return stringBuilder.toString();
	}

	public static <T> List<T> sortedKeys(Map<T, ? extends Comparable<?>> map, int elements) {
		List<T> list = Ordering.natural().reverse().onResultOf(Functions.forMap(map)).immutableSortedCopy(map.keySet());
		return list.size() > elements ? list.subList(0, elements) : list;
	}

	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> map(Object... objects) {
		HashMap map = new HashMap();
		Object key = null;
		for (final Object object : objects) {
			if (key == null) {
				key = object;
			} else {
				map.put(key, object);
				key = null;
			}
		}
		return map;
	}
}
