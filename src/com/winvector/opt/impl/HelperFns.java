package com.winvector.opt.impl;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.opt.def.Datum;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;

public class HelperFns {
	public static double px(final double x) {
		if(x>0) {
			return 1.0/(1.0 + Math.exp(-x));
		} else if(x<0) {
			final double e = Math.exp(x);
			return e/(1.0 + e);
		} else {
			return 0.5;
		}
	}
	
	public static double logpx(final double x) {
		if(x>0) {
			return -Math.log(1+Math.exp(-x));
		} else if(x<0) {
			return x - Math.log(1.0 + Math.exp(x));
		} else {
			return Math.log(0.5);
		}		
	}
	
	public static double logOMpx(final double x) {
		if(x>0) {
			return -x - Math.log(1 + Math.exp(-x));
		} else if(x<0) {
			return -Math.log(1.0 + Math.exp(x));
		} else {
			return Math.log(0.5);
		}		
	}
	
	
	public static boolean isGoodPrediction(final double[] pred, final ExampleRow ei) {
		double max = Double.NEGATIVE_INFINITY;
		for(int i=0;i<pred.length;++i) {
			if(i!=ei.category()) {
				max = Math.max(max,pred[i]);
			}
		}
		final boolean good =  pred[ei.category()]>max;
		return good;
	}
	
	
	public static <T extends ExampleRow> double accuracy(final LinearContribution<T> fn, final Iterable<ExampleRow> as, final double[] x) {
		int n = 0;
		int nGood = 0;
		for(final ExampleRow ei: as) {
			final double[] pred = fn.predict(x,ei);
			if(ei.category()>=0) {
				final boolean good = (pred!=null)&&isGoodPrediction(pred,ei);
				if(good) {
					++nGood;
				}
				++n;
			}
			//System.out.println(ei + " -> " + pred);
		}
		final double accuracy = nGood/(double)n; 
		final Log log = LogFactory.getLog(fn.getClass());
		log.info("accuarcy: " + nGood + "/" + n + " = " + accuracy);
		return accuracy;
	}
	
	public static double dot(final Datum row, final double[] o, final int startIndex) {
		double r = 0.0;
		final int card = row.getNIndices();
		for(int ii=0;ii<card;++ii) {
			final int i = row.getKthIndex(ii);
			r += row.getKthValue(ii)*o[i+startIndex];
		}
		return r;
	}

	public static void expScale(final double[] r) {
		final int n = r.length;
		if(n>0) {
			double max = r[0];
			for(int i=1;i<n;++i) {
				max = Math.max(max,r[i]);
			}
			double total = 0.0;
			for(int i=0;i<n;++i) {
				r[i] = Math.exp(r[i]-max);
				total += r[i];
			}
			final double scale = 1.0/total;
			for(int i=0;i<n;++i) {
				r[i] *= scale;
			}
		}
	}
}
