package com.winvector.logistic;


import junit.framework.TestCase;

import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.impl.DataFn;
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;


public class TestLogM extends TestCase {
	public void testMH() {
		final double[][] dat = {
				///  x1  x2 y
				 {   1, 0,  1 },
				 {   1, 1,  1 },
				 {   0, 1,  0 }
		};
		final RExample ex = new RExample(dat);
		final VectorFn sl = new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex);
		final double[] x0 = new double[sl.dim()];
		for(int i=0;i<sl.dim();++i) {
			x0[i] = i+1;
		}
		TestOpt.testGradAndHessian(sl,x0,1.0e-5,1.0e-2); // very sensitive to epsilon
	}

	public void testMHR() {
		final double[][] dat = {
				///  x1  x2 y
				 {   1, 0,  1 },
				 {   1, 1,  1 },
				 {   0, 1,  0 }
		};
		final RExample ex = new RExample(dat);
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex),0.1,null);
		final double[] x0 = new double[sl.dim()];
		for(int i=0;i<sl.dim();++i) {
			x0[i] = i+1;
		}
		TestOpt.testGradAndHessian(sl,x0,1.0e-5,1.0e-2); // very sensitive to epsilon
	}
	
	/**
	 * chosen to run to infinity
	 * 
	 * > dat <- read.table('exB.txt',header=T,sep='\t')
	 * > model <- glm(y~x1+x2,family=binomial(link='logit'),data=dat)
	 * > predict(model,type='response')
	 * 
	 * R soln: 23.56607  23.56607 -23.56607 
	 * 
	 * reg(0.1) soln: x(0.1): 1 x 3 matrix -0.337079 2.215088 -0.642055
	 *
	 * 
	 */
	public void testB() {
		final double[][] dat = {
				///  x1  x2 y
				 {   1, 0,  1 },
				 {   1, 1,  1 },
				 {   0, 1,  0 }
		};
		final RExample ex = new RExample(dat);
		final Newton nwt = new Newton();
		final double reg = 0.1;
		final SigmoidLossMultinomial sigmoidLoss = new SigmoidLossMultinomial(ex.dim,2);
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex),reg,null);
		final double[] x0 = new double[sl.dim()];
		final VEval opt = nwt.maximize(sl,x0,10);
		//System.out.println("x(" + reg + "): " + opt.x);
		final double accuracy = HelperFns.accuracy(sigmoidLoss,ex,opt.x);
		assertTrue(accuracy>=1.0);
		for(int i=0;i<opt.x.length;++i) {
			assertTrue(Math.abs(opt.x[i])<5.0);
		}
	}

}
