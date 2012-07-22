package com.winvector.opt.impl;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.opt.def.Datum;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;

public class HelperFns {


	
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
		final double[] pred = new double[fn.noutcomes()];
		for(final ExampleRow ei: as) {
			fn.predict(x,ei,pred);
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
				final double diff = r[i] - max;
				final double vi;
				if(diff<0.0) {
					vi = Math.exp(diff);
				} else {
					vi = 1.0;
				}
				r[i] = vi;
				total += vi;
			}
			final double scale = 1.0/total;
			for(int i=0;i<n;++i) {
				r[i] *= scale;
			}
		}
	}
	
	/**
	 * from: http://martin.ankerl.com/2007/02/11/optimized-exponential-functions-for-java/
	 *  ÒA Fast, Compact Approximation of the Exponential FunctionÓ  Nicol N. Schraudolph 1998
	 * @param x
	 */
	private static double exp(final double val) {
		final long tmp = (long) (1512775 * val + 1072632447);
		return Double.longBitsToDouble(tmp << 32);
	}
	
	private static final double estExp0 = HelperFns.exp(0.0);
	
	
	/**
	 * can use- but approximation adds noise in gradient and such
	 * @param r
	 */
	public static void expScaleFast(final double[] r) {
		final int n = r.length;
		if(n>0) {
			double max = r[0];
			for(int i=1;i<n;++i) {
				max = Math.max(max,r[i]);
			}
			double total = 0.0;
			for(int i=0;i<n;++i) {
				final double diff = r[i] - max;
				final double vi;
				if(diff<0.0) {
					vi = HelperFns.exp(diff);

				} else {
					vi = estExp0;
				}
				r[i] = vi;
				total += vi;
			}
			final double scale = 1.0/total;
			for(int i=0;i<n;++i) {
				r[i] *= scale;
			}
		}
	}
	
	public static void main(String[] args) {
		final double x = -0.3;
		System.out.println("Math.exp(" + x + "): " + Math.exp(x));
		System.out.println("HelperFns.exp(" + x + "): " + HelperFns.exp(x));
	}
}
