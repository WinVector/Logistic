package com.winvector.opt.impl;

import java.io.Serializable;

import com.winvector.opt.def.Datum;


public final class SparseSemiVec implements Datum,Serializable {
	private static final long serialVersionUID = 1L;

	private final int[] indices;
	private final double[] values;

	public SparseSemiVec(final double[] denseVec) {
		final int dim = denseVec.length;
		int ii = 0;
		for(int i=0;i<dim;++i) {
			if(denseVec[i]!=0.0) {
				++ii;
			}
		}
		final int card = ii;
		indices = new int[card];
		values = new double[card];
		ii = 0;
		for(int i=0;(i<dim)&&(ii<card);++i) {
			if(denseVec[i]!=0.0) {
				indices[ii] = i;
				values[ii] = denseVec[i];
				++ii;
			}
		}
	}

	public int getNIndices() {
		return indices.length;
	}
	
	public int getKthIndex(final int ii) {
		return indices[ii];
	}

	public double getKthValue(final int ii) {
		return values[ii];
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
		b.append("\t]");
		return b.toString();
	}
}
