package com.winvector.variables;

import java.util.SortedMap;
import java.util.TreeMap;

import com.winvector.util.BurstMap;

public final class ConstRecord implements VariableMapping {
	private static final long serialVersionUID = 1L;
	
	final int index;
	
	public ConstRecord(final int index) {
		this.index = index;
	}

	@Override
	public String origColumn() {
		return "";
	}

	@Override
	public int indexL() {
		return index;
	}

	@Override
	public int indexR() {
		return index + 1;
	}
	
	@Override
	public boolean wantRegularization() {
		return true;
	}

	@Override
	public void process(final BurstMap row, final double[] vec) {
		vec[index] = 1.0;
	}

	@Override
	public String name() {
		return "Const";
	}
	
	@Override
	public String toString() {
		return "Const->[" + index + "]";
	}
	
	@Override
	public SortedMap<String,Double> effects(final int base, final double[] x) {
		final SortedMap<String,Double> r = new TreeMap<String,Double>();
		r.put("",x[base+index]);
		return r;
	}

	@Override
	public SortedMap<String,Double> detailedEffects(final int base, final double[] x) {
		return effects(base,x);
	}
	
	@Override
	public double effect(final int base, final double[] x, final String level) {
		return x[base+index];
	}

	@Override
	public double effectTest(final int base, final double[] x, final String level) {
		return effects(base, x).values().iterator().next();
	}

}
