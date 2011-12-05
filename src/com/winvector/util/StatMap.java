package com.winvector.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public final class StatMap implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final class SimpleStat implements Serializable {
		private static final long serialVersionUID = 1L;
		public double sumW = 0.0;
		public double sumWX = 0.0;
	}
	
	private final Map<String,SimpleStat> counts = new HashMap<String,SimpleStat>();
	
	/**
	 * Invariant: key set calls all keys ever observed (even those with net-zero count)
	 */
	public void observe(final String key, final double x, final double wt) {
		SimpleStat v = counts.get(key);
		if(v==null) {
			v = new SimpleStat();
			counts.put(key,v);
		}
		v.sumW += wt;
		v.sumWX += wt*x;
	}

	public void observe(final String key, final SimpleStat val) {
		SimpleStat v = counts.get(key);
		if(v==null) {
			v = new SimpleStat();
			counts.put(key,v);
		}
		v.sumW += val.sumW;
		v.sumWX += val.sumWX;
	}
	
	public boolean contains(final String key) {
		return counts.containsKey(key);
	}
	
	public Set<String> keySet() {
		return counts.keySet();
	}
	
	public SimpleStat get(final String key) {
		final SimpleStat v = counts.get(key);
		if(v!=null) {
			return v;
		} else {
			return new SimpleStat();
		}
	}
	
	public SimpleStat remove(final String key) {
		final SimpleStat v = counts.remove(key);
		return v;
	}
}
