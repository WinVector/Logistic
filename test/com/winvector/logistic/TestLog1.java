package com.winvector.logistic;

import junit.framework.TestCase;

import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.impl.DataFn;
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;

public class TestLog1 extends TestCase {
	
	
	public void testGH() {
		final double[][] dat = {
				///  x1  x2 y
				 {   1, 0,  1 },
				 {   1, 1,  1 },
				 {   0, 1,  0 }
		};
		final RExample ex = new RExample(dat);
		final VectorFn sl = new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex);
		final double[] x0 = new double[sl.dim()];
		TestOpt.testGradAndHessian(sl,x0,1.0e-5,1.0e-4); // very sensitive to epsilon
	}

	public void testGHR() {
		final double[][] dat = {
				///  x1  x2 y
				 {   1, 0,  1 },
				 {   1, 1,  1 },
				 {   0, 1,  0 }
		};
		final RExample ex = new RExample(dat);
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex),0.1,null);
		final double[] x0 = new double[sl.dim()];
		TestOpt.testGradAndHessian(sl,x0,1.0e-5,1.0e-4); // very sensitive to epsilon
	}

	public void testGH2() {
		final double[][] dat = {
				///  x1  x2 y
				 {   1, 0,  1 },
				 {   1, 1,  1 },
				 {   0, 1,  0 }
		};
		final RExample ex = new RExample(dat);
		final VectorFn sl = new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex);
		final double[] x0 = new double[sl.dim()];
		x0[1] = 1.0;
		TestOpt.testGradAndHessian(sl,x0,1.0e-5,1.0e-4); // very sensitive to epsilon
	}

	public void testGHR2() {
		final double[][] dat = {
				///  x1  x2 y
				 {   1, 0,  1 },
				 {   1, 1,  1 },
				 {   0, 1,  0 }
		};
		final RExample ex = new RExample(dat);
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex),0.1,null);
		final double[] x0 = new double[sl.dim()];
		x0[1] = 1.0;
		TestOpt.testGradAndHessian(sl,x0,1.0e-5,1.0e-4); // very sensitive to epsilon
	}

	/**
	 * random data
	 * 
	 * > dat <- read.table('exdat.txt',header=T,sep='\t')
	 * > model <- glm(y~x1+x2+x3,family=binomial(link='logit'),data=dat)
	 * > predict(model,type='response')
	 */
	public void test1() {
		final double[][] dat = {
				///     x1          x2           x3       y
				 { -0.44976435, -0.78296280, -0.48688853, 0 },
				 { -1.03426815, 0.04612169, 1.47089045, 0 },
				 { -0.43384390, 0.42899808, -0.26193149, 0 },
				 { -0.95033489, -0.27426514, 0.68173371, 1 },
				 { 0.27908890, -0.32059753, -0.70644535, 0 },
				 { 1.00157159, 0.79282132, -0.37996207, 0 },
				 { 0.95845408, -2.53278930, 1.17061997, 1 },
				 { -0.49769246, -1.40173370, 0.85298792, 1 },
				 { 0.49837975, 0.09472328, 0.55434520, 1 },
				 { -0.95468277, 1.20501514, -0.36059224, 0 },
				 { -0.54413233, 1.22795085, 0.40355037, 0 },
				 { 0.06684785, -0.90056936, 0.26543402, 1 },
				 { 0.85603550, 0.21198687, -2.61638078, 0 },
				 { 1.26778309, 1.46421442, -0.41545011, 0 },
				 { -0.08671788, -0.71608390, -1.35539576, 0 },
				 { -0.89845231, 0.53988648, -1.44072650, 0 },
				 { -0.04908144, -2.34300762, -0.04386654, 1 },
				 { 0.61978004, 0.38270863, -0.08020138, 1 },
				 { 0.55258524, 2.06820588, 0.54660427, 0 },
				 { -0.16982628, -0.51338245, 1.28251022, 1 },				
		};
		final RExample ex = new RExample(dat);
		final Newton nwt = new Newton();
		final double[] rsoln = {-0.8438,      5.1539,     -5.0729,      4.6308 };  // glm(y~x1+x2+x3,family=binomial(link='logit'),data=dat)
		for(final double reg: new double[] { 0.0, 1.0e-3, 1.0e-2, 0.1, 1.0 }) {
			final SigmoidLossMultinomial sigmoidLoss = new SigmoidLossMultinomial(ex.dim,2);
			final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow>(new SigmoidLossMultinomial(ex.dim,2),ex),reg,null);
			final double[] x0 = new double[sl.dim()];
			//System.out.println("start: " + x0);
			final VEval opt = nwt.maximize(sl,x0,10);
			//System.out.println(opt);
			//for(final ExampleRow ei: ex) {
			//	double pred = SigmoidLoss.px(opt.x,ei.x);
			//	System.out.println(ei + " -> " + pred);
			//}
			final double accuracy = HelperFns.accuracy(sigmoidLoss,ex,opt.x);
			//System.out.println("accuracy(" + pass + "): " + accuracy);
			if(reg<=0.0) {
				for(int i=0;i<rsoln.length;++i) {
					final double javaSoln = -opt.x[i] + opt.x[i+rsoln.length];
					assertTrue(Math.abs(rsoln[i]-javaSoln)<1.0e-1);
				}
			}
			//System.out.println("done: " + opt.x);
			//System.out.println("x(" + reg + "," + accuracy + "): " + opt.x);
			assertTrue(accuracy>=0.85);
		}
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
		final double accuracy = HelperFns.accuracy(sigmoidLoss,ex,opt.x);
		assertTrue(accuracy>=1.0);
		for(int i=0;i<opt.x.length;++i) {
			assertTrue(Math.abs(opt.x[i])<5.0);
		}
		//System.out.println("x(" + reg + "): " + opt.x);
	}

}
