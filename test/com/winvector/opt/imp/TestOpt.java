package com.winvector.opt.imp;

import junit.framework.TestCase;

import com.winvector.opt.def.ScalarFn;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;
import com.winvector.opt.impl.ConjugateGradientOptimizer;
import com.winvector.opt.impl.GradientDescent;
import com.winvector.opt.impl.LinMax;
import com.winvector.opt.impl.Newton;

public class TestOpt extends TestCase {
	
	private static final class LinFun implements ScalarFn {
		public final double sign;
		public final double x0;

		public LinFun(final double x0, final double sign) {
			this.sign = sign;
			this.x0 = x0;
		}
		
		@Override
		public double eval(final double x) {
			final double diff = x - x0;
			return sign*diff*diff;
		}
	}
	
	public void testLinMax() {
		final LinFun f = new LinFun(3.2,-1.0);
		final LinMax solver = new LinMax();
		final double soln = solver.maximize(f,Double.NaN, 1.0,100);
		assertTrue(Math.abs(f.x0-soln)<1.0e-2);
	}
	
	public void testLinMin() {
		final LinFun f = new LinFun(3.2,01.0);
		final LinMax solver = new LinMax();
		final double soln = solver.minimize(f,Double.NaN, 1.0,100);
		assertTrue(Math.abs(f.x0-soln)<1.0e-2);
	}
	
	private static final class QuadFun implements VectorFn {
		public final double sign;
		public final double[] x0;
		
		public QuadFun(final double[] x0, final double sign) {
			this.x0 = x0;
			this.sign = sign;
		}
		
		@Override
		public int dim() {
			return x0.length;
		}

		@Override
		public VEval eval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
			final int n = x0.length;
			final VEval r = new VEval(x,wantGrad,wantHessian);
			for(int i=0;i<n;++i) {
				final double diff = x[i]-x0[i];
				r.fx += sign*diff*diff;
				if(wantGrad) {
					r.gx[i] = sign*2.0*diff;
				}
				if(wantHessian) {
					r.hx[i][i] = sign*2.0;
				}
			}
			return r;
		}
	}
	
	public void testMax() {
		final QuadFun f = new QuadFun(new double[] {0.2, -.62, 110.1, 11.1},-1.0);
		final VectorOptimizer[] opts = { new Newton(), new ConjugateGradientOptimizer(), new GradientDescent() };
		for(final VectorOptimizer opt: opts) {
			final VEval r = opt.maximize(f, new double[f.x0.length], 10);
			System.out.println(r);
			for(int i=0;i<f.x0.length;++i) {
				assertTrue(Math.abs(r.x[i]-f.x0[i])<0.1);
			}
		}
	}

}
