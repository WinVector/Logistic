package com.winvector.opt.impl;


import com.winvector.opt.def.LinearSolver;

public class SOR implements LinearSolver {
	public double diffTol = 1.0e-6;

	/**
	 * a is symmetric positive semi-definite
	 */
	@Override
	public double[] solve(final double[][] a, final double[] b) {
		final int dim = b.length;
		final double[] xP = new double[dim];
		final double[] x = new double[dim];
		final double w = 0.5;
		while(true) {
			//System.out.println(x);
			for(int i=0;i<dim;++i) {
				final double aii = a[i][i];
				final double xi;
				if(Math.abs(aii)>1.0e-8) {
					double sum = b[i];
					for(int j=0;j<i;++j) {
						sum -= a[i][j]*x[j];
					}
					for(int j=i+1;j<dim;++j) {
						sum -= a[i][j]*xP[j];
					}
					xi = (1-w)*xP[i] + (w/a[i][i])*sum;
				} else {
					xi = 0.0;
				}
				x[i] = xi;
			}
			double totDiff = 0.0;
			for(int i=0;i<dim;++i) {
				final double xi = x[i];
				totDiff += Math.abs(xP[i]-xi);
				xP[i] = xi;
			}
			if(totDiff<=diffTol) {
				break;
			}
		}
		return x;
	}

}
