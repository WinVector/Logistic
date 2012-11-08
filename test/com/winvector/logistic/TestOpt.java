package com.winvector.logistic;

import junit.framework.TestCase;

import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;

public class TestOpt extends TestCase {
	
	private static VEval numericlyBuildGradientAndHessian(final VectorFn f, final double[] x, final double epsilon) {
		final VEval ret = new VEval(x,true,true);
		final VEval fx = f.eval(x,false,false);
		ret.fx = fx.fx;
		final int dim = f.dim();
		final double[] xp = LinUtil.copy(x);
		final double[] fxp = new double[dim];
		for(int i=0;i<dim;++i) {
			xp[i] = x[i]+epsilon;
			final VEval fxpi = f.eval(xp,false,false);			
			xp[i] = x[i];
			fxp[i] = fxpi.fx;
			final double gi = (fxpi.fx - fx.fx)/epsilon;
			ret.gx[i] = gi;
		}
		for(int i=0;i<dim;++i) {
			final double fxpi = fxp[i];
			for(int j=0;j<dim;++j) {
				final double fxpj = fxp[j];
				if(i==j) {
					xp[i] = x[i]+2.0*epsilon;
				} else {
					xp[i] = x[i]+epsilon;
					xp[j] = x[j]+epsilon;
				}
				final VEval fxpij = f.eval(xp,false,false);
				xp[i] = x[i];
				xp[j] = x[j];
				final double hij = (fxpij.fx + fx.fx - (fxpi + fxpj))/(epsilon*epsilon); 
				ret.hx[i][j] = hij;
			}
		}
		return ret;
	}
	
	public static void testGradAndHessian(final VectorFn f, final double[] x, final double epsilon, final double tol) {
		final VEval fx = f.eval(x,true,true);
		//System.out.println("fx: " + fx);
		final VEval fn = numericlyBuildGradientAndHessian(f,x,epsilon);
		//System.out.println("fn: " + fn);
		final int dim = f.dim();
		for(int i=0;i<dim;++i) {
			if(Math.abs(fx.gx[i]-fn.gx[i])>=tol) { //extra if lets us set a breakpoint
				assertTrue(Math.abs(fx.gx[i]-fn.gx[i])<tol);
			}
			for(int j=0;j<dim;++j) {
				if(Math.abs(fx.hx[i][j]-fn.hx[i][j])>=tol) { //extra if lets us set a breakpoint
					assertTrue(Math.abs(fx.hx[i][j]-fn.hx[i][j])<tol);
				}
			}
		}
	}

	
	public void testNormV() {
		// norm penalty should put max at 0 (and max value should be 0)
		final int dim = 3;
		final VectorFn f = new NormPenalty(dim,1.0e-2);
		final double[] x = new double[dim];
		final VEval f0 = f.eval(x,false,false);
		assertTrue(Math.abs(f0.fx)<1.0e-12);
	}

	public void testNormV2() {
		// norm penalty should put max at 0 (and max value should be 0)
		final int dim = 3;
		final VectorFn f = new NormPenalty(dim,1.0e-2);
		final double[] x = new double[dim];
		x[1] = 0.1;
		x[2] = -0.1;
		final VEval f2 = f.eval(x,false,false);
		assertTrue(f2.fx<0.0);
	}

	public void testNormGH() {
		// norm penalty should put max at 0 (and max value should be 0)
		final int dim = 3;
		final VectorFn f = new NormPenalty(dim,1.0e-2);
		final double[] x = new double[dim];
		x[1] = 0.1;
		x[2] = -0.1;
		testGradAndHessian(f,x,1.0e-6,1.0e-5);
	}

	public void testNormOpt() {
		// norm penalty should put max at 0 (and max value should be 0)
		final int dim = 3;
		final VectorFn f = new NormPenalty(dim,1.0e-2);
		final double[] x = new double[dim];
		x[1] = 0.1;
		x[2] = -0.1;
		final Newton opt = new Newton();
		final VEval r = opt.maximize(f,x,10);
		assertNotNull(r);
		assertNotNull(r.x);
		assertEquals(dim,r.x.length);
		for(int i=0;i<dim;++i) {
			assertTrue(Math.abs(r.x[i])<1.0e-3);
		}
	}

}
