package com.winvector.util;

import junit.framework.TestCase;

import com.winvector.util.HBurster;

public class TestHBurster extends TestCase {
	public void testFix() {
		final String sep = "\\|";
		final String[] headerFlds = HBurster.buildHeaderFlds("a|a|b".split(sep));
		final String[] expect = { "a", "a_2", "b" };
		assertNotNull(headerFlds);
		assertEquals(expect.length,headerFlds.length);
		for(int i=0;i<expect.length;++i) {
			assertEquals(expect[i],headerFlds[i]);
		}
	}
}
