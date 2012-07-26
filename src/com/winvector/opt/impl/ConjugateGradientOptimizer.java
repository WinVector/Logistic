package com.winvector.opt.impl;



import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;

// TODO: test more
public final class ConjugateGradientOptimizer implements VectorOptimizer {
	private int debug = 0;
	private final Log log = LogFactory.getLog(ConjugateGradientOptimizer.class);
	private final double minGNormSq = 1.0e-20;
	private final double minMotionSq = 1.0e-14;
	private final double minHNormSq = 1.0e-12;
	private final double boxBound = 1.0e+10;
	private final double relImprovementTarget = 1.0e-3;


	private static String toString(final double[] x) {
		final StringBuilder b = new StringBuilder();
		if(null!=x) {
			b.append("[");
			b.append("\t");
			for(final double xi: x) {
				b.append(xi);
				b.append("\t");
			}
			b.append("]");
		} else {
			b.append("null");
		}
		return b.toString();
	}
	
	private static final double normSq(final double[] x) {
		double normSq = 0.0;
		for(final double xi: x) {
			normSq += xi*xi;
		}
		return normSq;
	}

	private static final double dot(final double[] x, final double[] y) {
		double dot = 0.0;
		final int n = x.length;
		for(int i=0;i<n;++i) {
			dot += x[i]*y[i];
		}
		return dot;
	}

	private static double[] copy(final double[] x, final boolean negate) {
		final int n = x.length;
		final double[] c = new double[n];
		if(!negate) {
			for(int i=0;i<n;++i) {
				c[i] = x[i];
			}
		} else {
			for(int i=0;i<n;++i) {
				c[i] = -x[i];
			}			
		}
		return c;
	}

	private static void add(final double[] x, final double[] delta, final double scale) {
		final int n = x.length;
		for(int i=0;i<n;++i) {
			x[i] += scale*delta[i];
		}
	}

	public enum StepStatus {
		goodCGStep,
		linFailure,
		noImprovement,
		smallGNorm,
	}
	
	@Override
	public VEval maximize(final VectorFn f, final double[] x0, final int maxRounds) {
		final VectorFn fNeg = new VectorFn() {
			@Override
			public int dim() {
				return f.dim();
			}

			@Override
			public VEval eval(double[] x, boolean wantGrad, boolean wantHessian) {
				final VEval eval = f.eval(x,wantGrad,wantHessian);
				eval.fx = -eval.fx;
				if(null!=eval.gx) {
					final int n = f.dim();
					for(int i=0;i<n;++i) {
						eval.gx[i] = -eval.gx[i];
					}
				}
				if(null!=eval.hx) {
					final int n = f.dim();
					for(int i=0;i<n;++i) {
						for(int j=0;j<n;++j) {
							eval.hx[i][j] = -eval.hx[i][j];
						}
					}
				}
				return eval;
			}
			
		};
		return minimize(fNeg,x0,maxRounds);
	}
	
	public VEval minimize(final VectorFn f, final double[] x0, final int maxRounds) {
		if (debug > 0) {
			log.info("start ConjugateGradient");
		}
		if (debug > 1) {
			log.info("supplied start: " + toString(x0));
		}
		final int n = f.dim();
		// init
		VEval cur = f.eval((null==x0)?new double[n]:x0,true,false);
		VEval best = cur;
		// build initial direction and history
		double[] gPrev = null;
		double[] hPrev = null;
		for (int outerStep = 0; outerStep < maxRounds; ++outerStep) {
			// get gradient on current eval if we need it
			if (cur.gx == null) {
				cur = f.eval(cur.x,true,false);
			}
			if (debug > 1) {
				log.info("cur: " + cur);
			}
			final double gradSq = normSq(cur.gx);
			if (gradSq<minGNormSq) {
				break;
			}
			final double[] g = copy(cur.gx,true); // g = opposite of gradient
			double[] h = copy(g,false);
			if (gPrev != null) {
				final double prevGSq = normSq(gPrev);
				final double[] tmp = copy(cur.gx,false);
				add(tmp,gPrev,1.0);
				final double gamma = dot(tmp,cur.gx)/prevGSq;
				add(h,hPrev,gamma);
				final double hSq = normSq(h);
				if (hSq < minHNormSq) {
					// collapse to start conditions
					h = copy(g,false);
				}
			}
			// save for next round (and h is linmin direction)
			gPrev = g;
			hPrev = h;
			final VEval roundStart = cur;
			try {
				final SFun f1 = new SFun(f,roundStart.x,h,boxBound,roundStart);
				final LinMax lmax = new LinMax();
				lmax.minimize(f1,roundStart.fx,1.0e-3,20);
				if(null!=f1.min) {
					cur = f1.min;
				}
			} catch (Exception e) {
				log.info("caught " + e);
				e.printStackTrace();
			}
			if (debug > 1) {
				log.info("back from linmin");
			}
			if((null==best)||(cur.fx<best.fx)) {
				best = cur;
			}
			if(!(cur.fx<roundStart.fx-Math.max(1.0,Math.abs(roundStart.fx))*relImprovementTarget)) {
				break;
			}
			double msq = 0.0;
			for(int i=0;i<n;++i) {
				final double diff = cur.x[i] - roundStart.x[i];
				msq += diff*diff;
			}
			if(msq<minMotionSq) {
				break;
			}
		}
		return best;
	}

}
