package com.winvector.opt.impl;

import java.util.ArrayList;


import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;

/**
 * return -x.x (maximum value 0 achieved at x = 0).
 * @author johnmount
 *
 */
public class NormPenalty implements VectorFn {
	private final double regularizeTerm;
	private final int dim;
	
	public NormPenalty(final int dim, final double regularizeTerm) {
		this.dim = dim;
		this.regularizeTerm = regularizeTerm;
	}

	@Override
	public int dim() {
		return dim;
	}

	@Override
	public VEval eval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
		final VEval r = new VEval(x,wantGrad,wantHessian);
		if(regularizeTerm>0.0) {
			for(int i=0;i<dim;++i) {
				final double xi = x[i];
				r.fx += -regularizeTerm*xi*xi;
				if(wantGrad) {
					r.gx[i] = -2.0*regularizeTerm*xi;
				}
				if(wantHessian) {
					r.hx[i][i] = -2.0*regularizeTerm;
				}
			}
		}
		return r;
	}
	
	public static VectorFn addPenalty(final VectorFn f, final double regularizeTerm) {
		final ArrayList<VectorFn> fns = new ArrayList<VectorFn>();
		fns.add(f);
		fns.add(new NormPenalty(f.dim(),regularizeTerm));
		return new SumFn(fns);
	}

}
