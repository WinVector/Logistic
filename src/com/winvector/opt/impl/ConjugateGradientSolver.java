package com.winvector.opt.impl;


import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.LinearSolver;


public final class ConjugateGradientSolver implements LinearSolver {
	public double movRelAbsTol = 1.0e-3;
	
	private static double add(final double r, final double[] a, final double s, final double[] b) {
		final int dim = a.length;
		double movRelAbs = 0.0;
		for(int i=0;i<dim;++i) {
			final double oldai = a[i];
			final double newai = r*a[i] + s*b[i];
			a[i] = newai;
			movRelAbs += Math.abs(newai-oldai)/Math.max(1.0,Math.abs(oldai));
		}
		return movRelAbs;
	}

	/**
	 * a (or its negation) is symmetric positive semi-definite
	 * TODO: more sensitive relative norm conditions
	 */
	@Override
	public double[] solve(final double[][] a, final double[] b) {
		final int dim = b.length;
		final double[] x = new double[dim];
		final double[] ap = new double[dim];
		boolean hadAGoodStep = false;
		outter:
		while(true) {
			final double[] r = LinUtil.copy(b);
			add(1.0,r,-1.0,LinUtil.mult(a,x));
			final double[] p = LinUtil.copy(r);
			inner:
			while(true) {
				final double oldrsq = LinUtil.dot(r,r);
				if(Math.abs(oldrsq)<=1.0e-12) {
					break outter;
				}
				LinUtil.mult(a,p,ap);
				final double apa = LinUtil.dot(p,ap);
				if(Math.abs(apa)<=0.0) {
					if(hadAGoodStep) {
						hadAGoodStep = false;
						break inner;
					} else {
						break outter;
					}
				}
				final double alpha = oldrsq/apa;
				final double movRelAbs = add(1.0,x,alpha,p);
				if(movRelAbs<=movRelAbsTol) {
					break outter;
				}
				add(1.0,r,-alpha,ap);
				final double beta = LinUtil.dot(r,r)/oldrsq;
				add(beta,p,1.0,r);
				hadAGoodStep = true;
			}
		}
		return x;
	}

}
