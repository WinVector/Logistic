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
	private final LinearSolver lSolver = new APISolver(); // new ConjugateGradientSolver();
	private final double ridgeTerm = 0;
	private final double minGNormSQ = 1.0e-8;
	private final double boxBound = 2000.0; // TODO: set this
	private final double relImprovementTarget = 1.0e-4;
	
	
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
		@SuppressWarnings("unused")
		public final StepStatus status;
		public final double[] newX;
		
		public NewtonReturn(final StepStatus status, final double[] newX) {
			this.status = status;
			this.newX = newX;
		}
	}
	
	@SuppressWarnings("unused")
	private NewtonReturn newtonStep(final int dim, final VEval lastEval) {
		final double normsq = LinUtil.dot(lastEval.gx,lastEval.gx);
		if(normsq<=minGNormSQ) {
			return new NewtonReturn(StepStatus.smallGNorm,null);
		}
		double[][] hx = lastEval.hx;
		if(ridgeTerm>0.0) { // Tikhonov regularization on the linear algebra (indep of any regularization on the overall fn).
			hx = LinUtil.copy(hx);
			for(int i=0;i<dim;++i) {
				hx[i][i] += ridgeTerm;
			}
		}
		try {  // try a Newton step
			final double[] delta = lSolver.solve(hx,lastEval.gx);
			final double[] newX = newX(lastEval.x,delta,-1);
			return new NewtonReturn(StepStatus.goodNewtonStep,newX);
		} catch (Exception ex) {
			log.info("solve caught: " + ex);
		}
		// sub in gradient as a usable direction
		final double[] newX = newX(lastEval.x,lastEval.gx,-1);
		return new NewtonReturn(StepStatus.linFailure,newX);
	}


	public VEval maximizeStep(final VectorFn f, final double[] x0,
			final VEval cachedEval,
			final boolean wantGrad, final boolean wantHessian) {
		log.info("start Newton step: " + ((cachedEval!=null)?""+cachedEval.fx:"")); 
		final VEval[] bestEval = new VEval[1];  // vector so gradient polish can alter value
		final int dim = f.dim();
		if(cachedEval!=null && cachedEval.gx!=null && cachedEval.hx!=null) {
			bestEval[0] = cachedEval;
		} else {
			if(null==x0) {
				bestEval[0] = f.eval(new double[dim],true,true);
			} else {
				bestEval[0] = f.eval(x0,true,true);
			}
		}
		log.info("initial Newton v: " + bestEval[0].fx);
		final double lastRecord = bestEval[0].fx;
		final NewtonReturn nr = newtonStep(dim,bestEval[0]);
		if(nr.newX!=null) {
			final VEval newEval = f.eval(nr.newX,wantGrad,wantHessian);
			if(newEval.fx>lastRecord) {
				bestEval[0] = newEval;
			} else {
				// probe more before giving up
				double lambda = 0.5;
				double[] trial = new double[dim];
				while(true) {
					boolean didPosProbe = false;
					{
						double maxDiff = 0.0;
						for(int i=0;i<dim;++i) {
							final double nxi = (1-lambda)*x0[i] + lambda*(newEval.x[i]); 
							trial[i] = Math.min(boxBound,Math.max(-boxBound,nxi));
							maxDiff = Math.max(maxDiff,Math.abs(trial[i]-x0[i]));
						}
						if(maxDiff>relImprovementTarget) {
							didPosProbe = true;
							final VEval cEval = f.eval(trial,false,false);
							if(cEval.fx>lastRecord) {
								bestEval[0] = cEval;
								break;
							}
						}
					}
					{
						double maxDiff = 0.0;
						for(int i=0;i<dim;++i) {
							final double nxi = (1-lambda)*x0[i] - lambda*(newEval.x[i]); 
							trial[i] = Math.min(boxBound,Math.max(-boxBound,nxi));
							maxDiff = Math.max(maxDiff,Math.abs(trial[i]-x0[i]));
						}
						if(maxDiff>relImprovementTarget) {
							final VEval cEval = f.eval(trial,false,false);
							if(cEval.fx>lastRecord) {
								bestEval[0] = cEval;
								break;
							}
						}
					}
					if(!didPosProbe) {
						break;
					}
					lambda = 0.1*lambda;
				}
			}
		}
		log.info("done Newton step: " + ((bestEval[0]!=null)?""+bestEval[0].fx:"")); 
		return bestEval[0];
	}

	@Override
	public VEval maximize(final VectorFn f, double[] x0, final int maxRounds) {
		if(null==x0) {
			x0 = new double[f.dim()];
		}
		final VEval[] bestEval = new VEval[1];  // vector so gradient polish can alter value
		bestEval[0] = maximizeStep(f,x0,null,true,true);
		for(int stepNum=1;stepNum<maxRounds;++stepNum) {
			final double lastRecord = bestEval[0].fx;
			final VEval newEval = maximizeStep(f,bestEval[0].x,bestEval[0],true,true);
			boolean goodStep = false;
			if(newEval.fx>lastRecord) {
				if(newEval.fx>lastRecord + Math.max(1.0,Math.abs(lastRecord))*relImprovementTarget) {
					goodStep = true;
				}
				bestEval[0] = newEval;
			}
			if(!goodStep) {
				break;
			}
		}
		return bestEval[0];
	}
}

