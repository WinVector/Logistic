package com.winvector.opt.def;

import java.io.Serializable;

public interface DModel<T extends ExampleRow> extends Serializable {
	int dim();
	int noutcomes();
	
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
