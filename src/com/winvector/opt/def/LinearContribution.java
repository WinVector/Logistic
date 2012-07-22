package com.winvector.opt.def;

import java.io.Serializable;


/**
 * Interface for fast tightly coupled summation across fixed x,wantGrad,wantHessian varying ExampleRow into an accumulating VEval.
 * Designed usage: summing a score across a data set (i.e. plug into com.winvector.opt.impl.DataFn )
 * @author johnmount
 *
 */
public interface LinearContribution<T extends ExampleRow> extends Serializable {
	int dim();
	int noutcomes();
	
	/**
	 * 
	 * @param x model coefficients (must have length dim())
	 * @param wantGrad
	 * @param wantHessian
	 * @param d data row
	 * @param r where to sum results
	 * @param pscratch (dim noutcomes) scratch space
	 */
	void addTerm(double[] x, boolean wantGrad, boolean wantHessian, T d, VEval r, double[] pscratch);
}
