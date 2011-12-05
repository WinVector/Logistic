package com.winvector.opt.impl;

import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;


/**
 * Class for slow loosely coupled summation of VectorFns.
 * designed usage: summing two unrelated functions (like data score and regularization penalty)
 * @author johnmount
 *
 */
public class SumFn implements VectorFn {
	private final Iterable<VectorFn> underlying;
	private final int dim;
	
	public SumFn(final Iterable<VectorFn> underlying) {
		this.underlying = underlying;
		dim = underlying.iterator().next().dim();
	}

	@Override
	public int dim() {
		return dim;
	}

	@Override
	public VEval eval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
		final VEval r = new VEval(x,wantGrad,wantHessian);
		for(final VectorFn fi: underlying) {
			final VEval vi = fi.eval(x,wantGrad,wantHessian);
			r.add(vi);
		}
		return r;
	}

}
