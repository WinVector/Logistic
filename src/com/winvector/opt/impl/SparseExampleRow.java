package com.winvector.opt.impl;

import java.util.Map;

import com.winvector.opt.def.ExampleRow;

public final class SparseExampleRow implements ExampleRow {
	private final int category;
	private final double wt;
	private final int[] indices;
	private final double[] values;

	public SparseExampleRow(final Map<Integer,Double> x, final int category) {
		this.category = category;
		wt = 1.0;
		final int card = x.size();
		indices = new int[card];
		values = new double[card];
		int ii = 0;
		for(final Map.Entry<Integer,Double> me: x.entrySet()) {
			indices[ii] = me.getKey();
			values[ii] = me.getValue();
			++ii;
		}
	}
	

	@Override
	public int getNIndices() {
		return indices.length;
	}
	
	@Override
	public int getKthIndex(final int ii) {
		return indices[ii];
	}

	@Override
	public double getKthValue(final int ii) {
		return values[ii];
	}	
	
	@Override
	public int category() {
		return category;
	}
	
	@Override
	public double weight() {
		return wt;
	}
	
	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("[");
		final int card = indices.length;
		for(int ii=0;ii<card;++ii) {
			final int i = indices[ii];
			final double v = values[ii];
			b.append("\t" + i + ":" + v);
		}
		b.append("\t]:" + category);
		return b.toString();
	}
}
