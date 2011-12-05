package com.winvector.opt.def;

import java.io.Serializable;


/**
 * Interface for fast tightly coupled summation across fixed x,wantGrad,wantHessian varying ExampleRow into an accumulating VEval.
 * Designed usage: summing a score across a data set (i.e. plug into com.winvector.opt.impl.DataFn )
 * @author johnmount
 *
 */
public interface LinearContribution extends Serializable {
	int dim();
	void addTerm(double[] x, boolean wantGrad, boolean wantHessian, ExampleRow d, VEval r);
	double[] predict(final double[] x, final Datum ei);
}
