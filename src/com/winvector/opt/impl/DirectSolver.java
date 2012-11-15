package com.winvector.opt.impl;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

import com.winvector.opt.def.LinearSolver;

/**
  * @author johnmount
 *
 */
public final class DirectSolver implements LinearSolver {
	private final Algebra algebra = Algebra.ZERO;

	@Override
	public double[] solve(final double[][] a, final double[] b) {
		final int dim = b.length;
		final DoubleMatrix2D mb = new DenseDoubleMatrix2D(dim,1);
		for(int i=0;i<dim;++i) {
			final double bi = b[i];
			mb.set(i,0,bi);
		}
		final DoubleMatrix2D ab = new DenseDoubleMatrix2D(a);
		final DoubleMatrix2D mx = algebra.solve(ab,mb);
		final double[] x = new double[dim];
		for(int i=0;i<dim;++i) {
			x[i] = mx.get(i,0);
		}
		return x;
	}

}
