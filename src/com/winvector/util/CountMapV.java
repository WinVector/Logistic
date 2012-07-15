package com.winvector.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;


public class CountMapV<T> implements Serializable {
	private static final long serialVersionUID = 1L;

	private final int dim;
	private final SortedMap<T,double[]> counts;
	
	public CountMapV(final Comparator<T> comp, final int dim) {
		this.dim = dim;
		counts = new TreeMap<T,double[]>(comp);
	}
	
	public int dim() {
		return dim;
	}
	
	/**
	 * Invariant: key set calls all keys ever observed (even those with net-zero count)
	 * @param key
	 * @param delta
	 */
	public void observe(final T key, final int index, final double delta) {
		double[] v = counts.get(key);
		if(v==null) {
			v = new double[dim];
			counts.put(key,v);
		}
		v[index] += delta;
	}
	
	public boolean contains(final T key) {
		return counts.containsKey(key);
	}
	
	public Set<T> keySet() {
		return counts.keySet();
	}
	
	public double get(final T key, final int index) {
		final double[] v = counts.get(key);
		if(v!=null) {
			return v[index];
		} else {
			return 0.0;
		}
	}
}
