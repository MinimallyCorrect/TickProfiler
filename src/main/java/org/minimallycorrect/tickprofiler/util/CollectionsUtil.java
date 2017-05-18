package org.minimallycorrect.tickprofiler.util;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public enum CollectionsUtil {
	;

	public static <A, B> List<A> newList(List<B> input, Function<B, A> function) {
		return input.stream().map(function).collect(Collectors.toList());
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
