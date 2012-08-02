package com.winvector.opt.imp;

import junit.framework.TestCase;

import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.LinearSolver;
import com.winvector.opt.impl.CholeskySolver;
import com.winvector.opt.impl.ConjugateGradientSolver;
import com.winvector.opt.impl.DirectSolver;



public class TestLinearSolver extends TestCase {
	
	public void checkSoln(final double[][] a, final double[] b, final double[] x, double tol) {
		assertNotNull(x);
		assertEquals(a[0].length,x.length);
		final double[] ax = LinUtil.mult(a,x);
		final int m = b.length;
		for(int i=0;i<m;++i) {
			assertTrue(Math.abs(b[i]-ax[i])<=tol);
		}
	}
	

	public void testCGSolver() {
		final double[][] a = new double[][] { {4, 1}, {1, 3} };
		final double[] b = new double[] {1, 2};
		final LinearSolver solver = new ConjugateGradientSolver();
		final double[] x = solver.solve(a, b);
		checkSoln(a,b,x, 1.0e-6);

	}

	public void testDirectSolver() {
		final double[][] a = new double[][] { {4, 1}, {1, 3} };
		final double[] b = new double[] {1, 2};
		final LinearSolver solver = new DirectSolver();
		final double[] x = solver.solve(a, b);
		checkSoln(a,b,x, 1.0e-6);
	}


	public void testCholeskySolver() {
		final double[][] a = new double[][] { {4, 1}, {1, 3} };
		final double[] b = new double[] {1, 2};
		final LinearSolver solver = new CholeskySolver();
		final double[] x = solver.solve(a, b);
		checkSoln(a,b,x, 1.0e-4);
	}
}
