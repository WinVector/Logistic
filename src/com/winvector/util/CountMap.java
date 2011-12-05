package com.winvector.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public final class CountMap<T> implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final class MutableDouble implements Serializable {
		private static final long serialVersionUID = 1L;
		public double v = 0.0;
	}
	
	private final SortedMap<T,MutableDouble> counts;
	
	public CountMap(final Comparator<T> comp) {
		counts = new TreeMap<T,MutableDouble>(comp);
	}
	
	/**
	 * Invariant: key set calls all keys ever observed (even those with net-zero count)
	 * @param key
	 * @param delta
	 */
	public void observe(final T key, final double delta) {
		MutableDouble v = counts.get(key);
		if(v==null) {
			v = new MutableDouble();
			counts.put(key,v);
		}
		v.v += delta;
	}
	
	public void observe(final CountMap<T> o) {
		for(final T k: o.keySet()) {
			final double d = o.get(k);
			observe(k,d);
		}
	}
	
	public boolean contains(final T key) {
		return counts.containsKey(key);
	}
	
	public Set<T> keySet() {
		return counts.keySet();
	}
	
	public double get(final T key) {
		final MutableDouble v = counts.get(key);
		if(v!=null) {
			return v.v;
		} else {
			return 0.0;
		}
	}
	
	private static class StrCmp implements Comparator<String>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final String arg0, final String arg1) {
			return arg0.compareTo(arg1);
		}
	}
	
	public static final Comparator<String> strCmp = new StrCmp();
	
	private static class StrACmp implements Comparator<String[]>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final String[] arg0, final String[] arg1) {
			final int n = arg0.length;
			if(n!=arg1.length) {
				if(n>arg1.length) {
					return 1;
				} else {
					return -1;
				}
			}
			for(int i=0;i<n;++i) {
				final int cmpi = arg0[i].compareTo(arg1[i]);
				if(cmpi!=0) {
					return cmpi;
				}
			}
			return 0;
		}
	}
	
	public static final Comparator<String[]> strACmp = new StrACmp();
}
