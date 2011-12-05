package com.winvector.opt.def;



/**
 * Interface to optimize (usually maximize) over.
 * @author johnmount
 *
 */
public interface VectorFn {
	int dim();
	VEval eval(double[] x, boolean wantGrad, boolean wantHessian);
}
