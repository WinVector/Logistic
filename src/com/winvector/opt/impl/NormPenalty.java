package com.winvector.opt.impl;

import java.util.ArrayList;

import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.variables.ConstRecord;
import com.winvector.variables.VariableMapping;

/**
 * return -x.x (maximum value 0 achieved at x = 0).
 * @author johnmount
 *
 */
public class NormPenalty implements VectorFn {
	private final int skipIndex;
	private final double regularizeTerm;
	private final int dim;
	
	/**
	 * 
	 * @param dim
	 * @param regularizeTerm
	 * @param adaptions used to scan for const term (no penalty on this)
	 */
	public NormPenalty(final int dim, final double regularizeTerm, final ArrayList<VariableMapping> adaptions) {
		this.dim = dim;
		this.regularizeTerm = regularizeTerm;
		int toSkip = -1;
		if(null!=adaptions) {
			for(final VariableMapping ai: adaptions) {
				if(ai instanceof ConstRecord) {
					toSkip = ai.indexL();
				}
			}
		}
		skipIndex = toSkip;
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
				if(skipIndex!=i) {
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
		}
		return r;
	}
	
	/**
	 * 
	 * @param f
	 * @param regularizeTerm
	 * @param adaptions used to scan for const term (no penalty on this)
	 * @return
	 */
	public static VectorFn addPenalty(final VectorFn f, final double regularizeTerm, final ArrayList<VariableMapping> adaptions) {
		final ArrayList<VectorFn> fns = new ArrayList<VectorFn>();
		fns.add(f);
		fns.add(new NormPenalty(f.dim(),regularizeTerm,adaptions));
		return new SumFn(fns);
	}

}
