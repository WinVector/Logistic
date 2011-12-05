package com.winvector.opt.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.LinearSolver;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;


/**
 * Newton step optimizer (set up to maximize)
 * @author johnmount
 *
 */
public final class Newton implements VectorOptimizer {
	private final Log log = LogFactory.getLog(Newton.class);
	//private final LinearSolver lSolver = new ConjugateGradient();
	private final LinearSolver lSolver = new DirectSolver();
	private final double ridgeTerm = 1.0e-8;
	private final double minGNormSQ = 1.0e-12;
	private final double minImprovement = 1.0e-6;
	private final double boxBound = 10.0; // TODO: set this
	
	
	private double[] newX(final double[] oldX, final double[] delta, final double scale) {
		final int dim = oldX.length;
		final double[] newX = new double[dim];
		for(int i=0;i<dim;++i) {
			final double oxi = oldX[i];
			if((!Double.isInfinite(oxi))&&(!Double.isNaN(oxi))) {
				final double di = scale*delta[i];
				final double nxi;
				if(Double.isInfinite(di)||Double.isNaN(di)) {
					nxi = oxi;
				} else {
					nxi = oxi + di;
				}
				newX[i] = Math.min(boxBound,Math.max(-boxBound,nxi));
			}
		}
		return newX;
	}
	

	
	public enum StepStatus {
		goodNewtonStep,
		linFailure,
		noImprovement,
		smallGNorm,
	}
	
	private static final class NewtonReturn {
		public final StepStatus status;
		public final double[] newX;
		public final long linNanoTime;
		
		public NewtonReturn(final StepStatus status, final double[] newX, final long linNanoTime) {
			this.status = status;
			this.newX = newX;
			this.linNanoTime = linNanoTime;
		}
	}
	
	private NewtonReturn newtonStep(final VectorFn f, final VEval lastEval) {
		final int dim = f.dim();
		final double normsq = LinUtil.dot(lastEval.gx,lastEval.gx);
		if(normsq<=minGNormSQ) {
			return new NewtonReturn(StepStatus.smallGNorm,null,0);
		}
		final long startLinNano;
		final long endLinNano;
		double[][] hx = lastEval.hx;
		if(ridgeTerm>0.0) { // Tikhonov regularization on the linear algebra (indep of any regularization on the overall fn).
			hx = LinUtil.copy(hx);
			for(int i=0;i<dim;++i) {
				hx[i][i] += ridgeTerm;
			}
		}
		startLinNano = System.nanoTime();
		double[] delta = null;
		try {  // try a Newton step
			delta = lSolver.solve(hx,lastEval.gx);
		} catch (Exception ex) {
			log.info("solve caught: " + ex);
		}
		endLinNano = System.nanoTime();
		if(delta!=null) {
			final double[] newX = newX(lastEval.x,delta,-1);
			return new NewtonReturn(StepStatus.goodNewtonStep,newX,endLinNano-startLinNano);
		} else {
			return new NewtonReturn(StepStatus.linFailure,null,endLinNano-startLinNano);
		}
	}
	

	
	public VEval maximize(final VectorFn f, double[] x, final int maxRounds) {
		if(null==x) {
			x = new double[f.dim()];
		}
		final VEval[] bestEval = new VEval[1];  // vector so gradient polish can alter value
		for(int stepNum=0;stepNum<maxRounds;++stepNum) {
			final double goal = bestEval[0]==null?Double.NEGATIVE_INFINITY:Math.max(bestEval[0].fx + minImprovement,bestEval[0].fx + minImprovement*Math.abs(bestEval[0].fx));
			final long nanoTimeStart = System.nanoTime();
			final VEval lastEval = f.eval(x,true,true);
			log.info("NewtonEval: " + stepNum + "\t" + lastEval.fx);
			if((bestEval[0]==null)||(lastEval.fx>bestEval[0].fx)) {
				bestEval[0] = lastEval;
			}
			if(lastEval.fx>=goal) {
				final NewtonReturn nr = newtonStep(f,lastEval);
				final long nanoTimeEnd= System.nanoTime();
				final long totalNano = nanoTimeEnd - nanoTimeStart;
				log.info("NewtonReturn: " + stepNum + "\t" + nr.status);
				log.info("Newton work: "  + totalNano + "NS "
					+ "( linear solver portion: " + nr.linNanoTime + "/" + totalNano + " = " + (nr.linNanoTime/(double)totalNano) + ")");
				if((nr.status==StepStatus.goodNewtonStep)&&(nr.newX!=null)) {
					x = nr.newX;
				} else {
					break;
				}
			} else {
				log.info("Newton no-improve, ending");
				break;
			}
		}
		return bestEval[0];
	}
}

