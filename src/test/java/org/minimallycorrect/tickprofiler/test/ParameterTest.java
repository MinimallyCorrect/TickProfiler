package org.minimallycorrect.tickprofiler.test;

import java.util.*;

import lombok.val;

import org.junit.Assert;
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

	@Test
	public void testOverloadedDefault() {
		val p = new Parameters(Arrays.asList("e", "chunks=current", "time=5"));
		p.order(Collections.singletonList("type"));
		p.checkUsage(true);
		p.orderWithDefault(Arrays.asList("time", "30", "chunks", "all"));
		p.checkUsage(false);
		Assert.assertEquals("5", p.getString("time"));
		Assert.assertEquals("current", p.getString("chunks"));
	}
}
