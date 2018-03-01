package org.minimallycorrect.tickprofiler.minecraft.commands;

import java.util.*;

import lombok.val;

public class Parameters {
	private final ArrayList<String> unmatched = new ArrayList<>();
	private final HashMap<String, String> map = new HashMap<>();
	private final LinkedHashSet<String> expected = new LinkedHashSet<>();

	public Parameters(Iterable<String> args) {
		for (val arg : args) {
			val equals = arg.indexOf('=');
			if (equals == -1)
				unmatched.add(arg);
			else
				map.put(arg.substring(0, equals), arg.substring(equals + 1));
		}
	}

	public void order(List<String> keys) {
		int i = 0;
		int size = unmatched.size();
		for (val key : keys) {
			if (i < size)
				map.put(key, unmatched.get(i++));
			expected.add(key);
		}
		if (i != 0)
			unmatched.subList(0, i).clear();
	}

	public void orderWithDefault(List<String> keys) {
		int i = 0;
		int size = unmatched.size();
		for (int j = 0; j < keys.size(); j += 2) {
			val key = keys.get(j);
			if (!map.containsKey(key))
				map.put(key, i < size ? unmatched.get(i++) : keys.get(j + 1));
			expected.add(key);
		}
		if (i != 0)
			unmatched.subList(0, i).clear();
	}

	public void checkUsage(boolean allowExtra) {
		for (String s : expected)
			if (!map.containsKey(s))
				throw new UsageException("Missing required parameter " + s);

		if (!allowExtra) {
			if (!unmatched.isEmpty())
				throw new UsageException("Unmatched parameters " + unmatched);

			for (String s : map.keySet())
				if (!expected.contains(s))
					throw new UsageException("Unexpected parameter " + s);
		}
	}

	public String getString(String type) {
		val result = map.get(type);
		if (result == null)
			throw new UsageException("Missing parameter " + type);
		return result;
	}

	public int getInt(String time) {
		return Integer.parseInt(getString(time));
	}

	void writeExpectedParameters(StringBuilder sb) {
		for (val s : expected) {
			sb.append(' ');
			sb.append(s);
			val current = map.get(s);
			if (current != null)
				sb.append('=').append(current);
		}
	}

	public boolean has(String key) {
		return map.containsKey(key);
	}
}
