package com.winvector.opt.impl;

import com.winvector.opt.def.ExampleRow;

public final class SparseExampleRow implements ExampleRow {
	private final int category;
	private final double wt;
	private final SparseSemiVec v;

	public SparseExampleRow(final SparseSemiVec v, final double weight, final int category) {
		this.category = category;
		wt = weight;
		this.v = v;
	}
	

	@Override
	public int getNIndices() {
		return v.getNIndices();
	}
	
	@Override
	public int getKthIndex(final int ii) {
		return v.getKthIndex(ii);
	}

	@Override
	public double getKthValue(final int ii) {
		return v.getKthValue(ii);
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
		return v.toString() + "(" + wt +"): " + category;
	}
}
