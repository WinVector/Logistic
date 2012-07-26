package com.winvector.opt.impl;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;
import com.winvector.opt.impl.LinMax;

/**
 * not as good as general conjugate gradient
 * @author johnmount
 *
 */
public final class GradientDescent implements VectorOptimizer {
	private final Log log = LogFactory.getLog(GradientDescent.class);
	private final double minGNormSQ = 1.0e-12;
	private final double boxBound = 10000.0; // TODO: set this
	private final double relImprovementTarget = 1.0e-3;
	
	public enum StepStatus {
		goodGradientDescentStep,
		linMinFailure,
		noImprovement,
		smallGNorm,
	}
	
	
	public StepStatus gradientPolish(final VectorFn f, final VEval lastEval, final VEval[] bestEval) {
		// try for a partial steepest descent step (gradient)- usually not reached
		final int dim = f.dim();
		double normGsq = 0.0;
		double maxAbsG = 0.0;
		for(int i=0;i<dim;++i) {
			maxAbsG = Math.max(maxAbsG,Math.abs(lastEval.gx[i]));
			normGsq += lastEval.gx[i]*lastEval.gx[i];
		}
		if(normGsq<minGNormSQ) {
			return StepStatus.smallGNorm;
		}
		final double unitScale = 1.0/Math.max(1.0,maxAbsG);
		final SFun g = new SFun(f,lastEval.x,lastEval.gx,boxBound,lastEval);
		final LinMax lmax = new LinMax();
		lmax.maximize(g, lastEval.fx, unitScale,20);
		if(g.max==null) {
			return StepStatus.linMinFailure;
		}
		if((bestEval[0]==null)||(g.max.fx>bestEval[0].fx)) {
			bestEval[0] = g.max;
		}
		if((g.max!=null)&&(g.max.fx>lastEval.fx)) {
			return StepStatus.goodGradientDescentStep;
		} else {
			return StepStatus.noImprovement;
		}
	}


	@Override
	public VEval maximize(final VectorFn f, final double[] x, final int maxRounds) {
		final VEval[] bestEval = new VEval[] { f.eval(x,true,false) };
		log.info("GDstart: " + bestEval[0].fx);
		for(int round=0;round<maxRounds;++round) {
			final VEval lastEval;
			if(bestEval[0].gx!=null) {
				lastEval = bestEval[0];
			} else {
				lastEval = f.eval(bestEval[0].x,true,false);
			}
			final double lastRecord = bestEval[0].fx;
			final StepStatus ri = gradientPolish(f,lastEval,bestEval);
			log.info("GDstatus: " + ri + "\t" + bestEval[0].fx);
			if(!StepStatus.goodGradientDescentStep.equals(ri)) {
				break;
			}
			if(!(bestEval[0].fx>lastRecord+Math.max(1.0,Math.abs(lastRecord))*relImprovementTarget)) {
				break;
			}
		}
		return bestEval[0];
	}
}
