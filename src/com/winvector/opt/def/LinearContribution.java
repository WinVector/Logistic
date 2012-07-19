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
	
	/**
	 * 
	 * @param x model coefficients (must have length dim())
	 * @param ei data row
	 * @param r where to write prediction results (must have length noutcomes())
	 */
	void predict(final double[] x, final Datum ei, final double[] r);
	
	/**
	 * 
	 * @param x model coefficients (must have length dim())
	 * @param ei data row
	 * @return predictions per category
	 */
	double[] predict(final double[] x, final Datum ei);
}
