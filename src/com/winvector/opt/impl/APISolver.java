package com.winvector.opt.impl;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

import com.winvector.opt.def.LinearSolver;

/**
 * approximate pseudo inverse solver (see: http://en.wikipedia.org/wiki/Moore–Penrose_pseudoinverse )
 * solves ( t(a) a + epsilon I ) x =  t(a) y
 * (not if a x = y, then x nearly solves the above )
 * note: this does square the matrix condition number (bad)
 * @author johnmount
 *
 */
public class APISolver implements LinearSolver {
	private final Algebra algebra = Algebra.ZERO;
	private final double epsilon = 1.0e-5;

	@Override
	public double[] solve(final double[][] a, final double[] b) {
		final int dim = b.length;
		final DoubleMatrix2D mb = new DenseDoubleMatrix2D(dim,1);
		for(int i=0;i<dim;++i) {
			final double bi = b[i];
			mb.set(i,0,bi);
		}
		final DoubleMatrix2D ab = new DenseDoubleMatrix2D(a);
		final DoubleMatrix2D ta = algebra.transpose(ab);
		final DoubleMatrix2D taa = algebra.mult(ta,ab);
		double sumAbs = 0.0;
		for(int i=0;i<dim;++i) {
			for(int j=0;j<dim;++j) {
				sumAbs += Math.abs(taa.get(i,j));
			}
		}
		final double escale = epsilon*sumAbs/(1.0+((double)dim)*((double)dim));
		for(int i=0;i<dim;++i) {
			taa.set(i, i, taa.get(i,i) + escale);
		}
		final DoubleMatrix2D mx = algebra.solve(taa,algebra.mult(ta,mb));
		final double[] x = new double[dim];
		for(int i=0;i<dim;++i) {
			x[i] = mx.get(i,0);
		}
		return x;
	}

}
