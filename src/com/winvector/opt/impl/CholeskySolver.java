package com.winvector.opt.impl;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.CholeskyDecomposition;

import com.winvector.opt.def.LinearSolver;

/**
 * only applicable to symmetric positive definite matrices (or their negations)
 * @author johnmount
 *
 */
public final class CholeskySolver implements LinearSolver {
	@Override
	public double[] solve(final double[][] ain, final double[] b) {
		final int dim = b.length;
		double sign = 1.0;
		for(int i=0;i<dim;++i) {
			if(Math.abs(ain[i][i])>0.0) {
				if(ain[i][i]<0.0) {
					sign = -1.0;
				}
				break;
			}
		}
		final DoubleMatrix2D ma = new DenseDoubleMatrix2D(dim,dim);
		for(int i=0;i<dim;++i) {
			for(int j=0;j<dim;++j) {
				ma.set(i,j,sign*ain[i][j]);
			}
		}
		final DoubleMatrix2D mb = new DenseDoubleMatrix2D(dim,1);
		for(int i=0;i<dim;++i) {
			mb.set(i,0,sign*b[i]);
		}
		final CholeskyDecomposition decomp = new CholeskyDecomposition(ma);
		final DoubleMatrix2D mx;
		if(decomp.isSymmetricPositiveDefinite()) {
			mx = decomp.solve(mb);
		} else {
			// fall back to direct
			mx = Algebra.ZERO.solve(ma,mb);
		}
		final double[] x = new double[dim];
		for(int i=0;i<dim;++i) {
			x[i] = mx.get(i,0);
		}
		return x;
	}
}
