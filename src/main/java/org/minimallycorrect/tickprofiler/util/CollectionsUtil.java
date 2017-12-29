package org.minimallycorrect.tickprofiler.util;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import lombok.val;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

public enum CollectionsUtil {
	;

	public static <A, B> List<A> newList(List<B> input, Function<B, A> function) {
		return input.stream().map(function).collect(Collectors.toList());
	}

	public static String join(Iterable<?> iterable, String delimiter) {
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
		val map = new HashMap<K, V>();
		Object key = null;
		for (final Object object : objects) {
			if (key == null) {
				key = object;
			} else {
				map.put((K) key, (V) object);
				key = null;
			}
		}
		return map;
	}
}
