package com.winvector.opt.impl;

import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;


public class DataFn implements VectorFn {
	private final Iterable<ExampleRow> dat;
	private final LinearContribution underlying;
	
	public DataFn(final LinearContribution underlying, final Iterable<ExampleRow> dat) {
		this.underlying = underlying;
		this.dat = dat;
	}

	@Override
	public int dim() {
		return underlying.dim();
	}

	@Override
	public VEval eval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
		final VEval r = new VEval(x,wantGrad,wantHessian);
		for(final ExampleRow di: dat) {
			if(di.category()>=0) {
				underlying.addTerm(x,wantGrad,wantHessian,di,r);
			}
		}
		return r;
	}

}
