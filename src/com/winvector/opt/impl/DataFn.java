package com.winvector.opt.impl;

import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.util.Ticker;


public final class DataFn<T extends ExampleRow,X extends T> implements VectorFn {
	private final Iterable<X> dat;
	private final LinearContribution<T> underlying;
	
	public DataFn(final LinearContribution<T> underlying, final Iterable<X> dat) {
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
		final Ticker ticker = new Ticker();
		final double[] pscratch = new double[underlying.noutcomes()];
		for(final X di: dat) {
			ticker.tick();
			if((di.category()>=0)&&(di.weight()>0.0)) {
				underlying.addTerm(x,wantGrad,wantHessian,di,r,pscratch);
			}
		}
		return r;
	}
}
