package org.minimallycorrect.tickprofiler.test;

import java.util.*;

import lombok.val;

import org.junit.Test;

import org.minimallycorrect.tickprofiler.minecraft.commands.Parameters;

public class ParameterTest {
	@Test
	public void testSingle() {
		val p = new Parameters(Collections.singletonList("e"));
		p.order(Collections.singletonList("type"));
		p.checkUsage(false);
	}

	@Test
	public void testMultiple() {
		val p = new Parameters(Arrays.asList("e", "10", "2"));
		p.order(Collections.singletonList("type"));
		p.checkUsage(true);
		p.orderWithDefault(Arrays.asList("time", "30", "types", "20"));
		p.checkUsage(false);
	}
}
