package com.winvector.logistic;



import java.util.Arrays;

import com.winvector.opt.def.Datum;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.def.DModel;
import com.winvector.opt.def.VEval;
import com.winvector.opt.impl.HelperFns;

/**
 * Sigmoid loss function (maximizing this yields logistic regression) for multinomial classifier (all classes have variables)
 * 
 * f(x) = sum_{data pos} log(p(data)) + sum_{data neg} log(1-p(data))
 * 
 * @author johnmount
 *
 */
public final class SigmoidLossMultinomial implements LinearContribution<ExampleRow>, DModel<ExampleRow> {
	private static final long serialVersionUID = 1L;
	
	public final int vdim;
	public final int noutcomes;
	public boolean useFastExp = false;
	/**
	 * when marjorize=true don't compute Hessian, but instead Hessian of a majorizing or minorizing function as appropriate.
	 * Gaurantees Newton step is a contraction (but loses rate of convergence guarnatees).
	 * majorize=true should work in theory see:
	 *  “A Tutorial on MM Algorithms”, David R. Hunter, Kenneth Lange; 
	 *  “Monotonicity of Quadratic-Approximation Algorithms”, Dankmar Bohning, Bruce G. Lindsay, Ann. Inst. Statist. Math, Vol. 40, No. 4, pp 641-664, 1988
	 * 
	 * but, dreadfully slow convergence (at least when combined with crazy adapter test).  ideally could mix majorized and non-majorized steps
	 */
	public boolean majorize = false;
	
	public SigmoidLossMultinomial(final int vdim, final int noutcomes) {
		this.vdim = vdim;
		this.noutcomes = noutcomes;
	}
	
	@Override
	public void predict(final double[] x, final Datum ei, final double[] r) {
		Arrays.fill(r,0.0);
		for(int i=0;i<noutcomes;++i) {
			r[i] = HelperFns.dot(ei,x,i*vdim);
		}
		if(useFastExp) {
			HelperFns.expScaleFast(r);
		} else {
			HelperFns.expScale(r);
		}
	}

	@Override
	public double[] predict(final double[] x, final Datum ei) {
		final double[] r = new double[noutcomes];
		predict(x,ei,r);
		return r;
	}

	@Override
	public int dim() {
		return vdim*noutcomes;
	}

	@Override
	public int noutcomes() {
		return noutcomes;
	}
	
	@Override
	public void addTerm(final double[] x, final boolean wantGrad, final boolean wantHessian, final ExampleRow di, final VEval r, final double[] pred) {
		final double wt = di.weight();
		if(wt>0.0) {
			predict(x,di,pred);
			r.fx += wt*Math.log(pred[di.category()]);
			final int nindices;
			if(wantGrad||wantHessian) {
				nindices = di.getNIndices();
			} else {
				nindices = 0;
			}
			if(wantGrad) {
				final double[] grad = r.gx;
				for(int cati=0;cati<noutcomes;++cati) {
					final double pc = pred[cati];
					final double t = (di.category()==cati?1.0:0.0) - pc;
					for(int ii=0;ii<nindices;++ii) {
						final int i = di.getKthIndex(ii) + cati*vdim;
						final double vi = di.getKthValue(ii);
						final double oij = grad[i];
						final double nij = oij + wt*t*vi;
						grad[i] = nij;
					}
				}
			}
			if(wantHessian) {
				final double[][] hess = r.hx;
				for(int cati=0;cati<noutcomes;++cati) {
					for(int catj=0;catj<noutcomes;++catj) {
						final double t;
						if(!majorize) {
							if(cati==catj) {
								t = -pred[cati]*(1.0-pred[cati]);
							} else {
								t = pred[cati]*pred[catj];
							}
						} else {
							if(cati==catj) {
								t = -0.25; // greatest global lower bound
							} else {
								t = 0.0;   // greatest global lower bound
							}
						}
						for(int ii=0;ii<nindices;++ii) {
							final int i = di.getKthIndex(ii) + cati*vdim;
							final double vi = di.getKthValue(ii);
							for(int jj=0;jj<nindices;++jj) {
								final int j = di.getKthIndex(jj) + catj*vdim;
								final double vj = di.getKthValue(jj);
								final double oij = hess[i][j];
								final double nij = oij + wt*t*vi*vj;
								hess[i][j] = nij;
							}
						}
					}
				}
			}
		}
	}
}


