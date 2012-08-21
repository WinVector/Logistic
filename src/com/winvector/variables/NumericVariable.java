package com.winvector.variables;

import java.util.SortedMap;
import java.util.TreeMap;

import com.winvector.util.BurstMap;

public final class NumericVariable implements VariableMapping {
	private static final long serialVersionUID = 1L;
	
	private final String origColumn;
	private final int index;
	
	public NumericVariable(final String origColumn, final int index) {
		this.origColumn = origColumn;
		this.index = index;
	}

	@Override
	public String origColumn() {
		return origColumn;
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
		return false;
	}
	
	@Override
	public void process(final BurstMap row, final double[] vec) {
		final double v = row.getAsDouble(origColumn);
		if(!Double.isNaN(v)) {
			if(v!=0.0) {
				vec[index] = v;
			}				
		}
	}

	@Override
	public String name() {
		return "Numeric";
	}

	@Override
	public String toString() {
		return "'" + origColumn + "'->[" + index + "]";
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
		return x[base+index]*Double.valueOf(level);
	}
	
	@Override
	public double effectTest(final int base, final double[] x, final String level) {
		return effects(base, x).values().iterator().next()*Double.valueOf(level);
	}
}
