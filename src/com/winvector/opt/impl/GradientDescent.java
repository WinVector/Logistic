package com.winvector.opt.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.opt.def.ScalarFn;
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
	private final double minImprovement = 1.0e-6;
	private final double boxBound = 10.0; // TODO: set this
	
	public enum StepStatus {
		goodGradientDescentStep,
		linMinFailure,
		noImprovement,
		smallGNorm,
	}
	
	
	private static double[] newX(final double[] oldX, final double[] delta, final double scale, final double boxBound) {
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

	/**
	 * caches recent evals (de-bound fn evals so other code can be written simply)
	 * keeps best eval (maximal)
	 * @author johnmount
	 *
	 */
	public static class SFun implements ScalarFn {
		public final VectorFn f;
		public final double[] x0;
		public final double[] dir;
		public final double boxBound;
		public VEval min = null;
		public VEval max = null;
		public final Map<Double,VEval> cache = new LinkedHashMap<Double,VEval>() {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(final Map.Entry<Double,VEval> eldest) {
				return size()>5;
			}
		};
		
		public SFun(final VectorFn f, final VEval x0, final double[] dir, final double boxBound) {
			this.f = f;
			this.x0 = x0.x;
			this.dir = dir;
			this.boxBound = boxBound;
			if((!Double.isInfinite(x0.fx))&&(!Double.isNaN(x0.fx))) {
				if((min==null)||(x0.fx<min.fx)) {
					min = x0;
				}
				if((max==null)||(x0.fx>max.fx)) {
					max = x0;
				}
				cache.put(0.0,x0);
			}
		}
		
		@Override
		public double eval(final double s) {
			VEval fx = cache.get(s);
			if(null==fx) {
				final double[] newX = newX(x0,dir,s,boxBound);
				fx = f.eval(newX,false,false);
				if((!Double.isInfinite(fx.fx))&&(!Double.isNaN(fx.fx))) {
					if((min==null)||(fx.fx<min.fx)) {
						min = fx;
					}
					if((max==null)||(fx.fx>max.fx)) {
						max = fx;
					}
				}
				cache.put(s,fx);
			}
			return fx.fx;
		}
	}
	
	public StepStatus gradientPolish(final VectorFn f, final VEval lastEval, final VEval[] bestEval) {
		// try for a partial steepest descent step (gradient)- usually not reached
		final double goal = Math.max(lastEval.fx + minImprovement,lastEval.fx + minImprovement*Math.abs(lastEval.fx));
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
		final SFun g = new SFun(f,lastEval,lastEval.gx,boxBound);
		final LinMax lmax = new LinMax();
		lmax.maximize(g, lastEval.fx, unitScale, goal,20);
		if(g.max==null) {
			return StepStatus.linMinFailure;
		}
		if((bestEval[0]==null)||(g.max.fx>bestEval[0].fx)) {
			bestEval[0] = g.max;
		}
		if((g.max!=null)&&(g.max.fx>lastEval.fx)&&(g.max.fx>=goal)) {
			return StepStatus.goodGradientDescentStep;
		} else {
			return StepStatus.noImprovement;
		}
	}


	@Override
	public VEval maximize(VectorFn f, double[] x, int maxRounds) {
		final VEval[] bestEval = new VEval[] { f.eval(x,true,false) };
		log.info("GDstart: " + bestEval[0].fx);
		for(int round=0;round<maxRounds;++round) {
			final VEval lastEval;
			if(bestEval[0].gx!=null) {
				lastEval = bestEval[0];
			} else {
				lastEval = f.eval(bestEval[0].x,true,false);
			}
			final StepStatus ri = gradientPolish(f,lastEval,bestEval);
			log.info("GDstatus: " + ri + "\t" + bestEval[0].fx);
			if(!StepStatus.goodGradientDescentStep.equals(ri)) {
				break;
			}
		}
		return bestEval[0];
	}
}
