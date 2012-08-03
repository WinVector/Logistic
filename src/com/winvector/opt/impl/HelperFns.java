package com.winvector.opt.impl;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.opt.def.DModel;
import com.winvector.opt.def.Datum;
import com.winvector.opt.def.ExampleRow;
import com.winvector.util.ReducibleObserver;
import com.winvector.util.ThreadedReducer;

public class HelperFns {


	public static int argmax(final double[] pred) {
		int choice = 0;
		final int n = pred.length;
		for(int i=1;i<n;++i) {
			if(pred[i]>pred[choice]) {
				choice = i;
			}
		}
		return choice;
	}
	
	public static boolean isGoodPrediction(final double[] pred, final ExampleRow ei) {
		final int predi = argmax(pred);
		final boolean good = predi==ei.category();
		return good;
	}
	
	private static final class AccuracyCounter<T extends ExampleRow> implements ReducibleObserver<T,AccuracyCounter<T>> {
		public long n = 0;
		public long nGood = 0;
		private final DModel<T> fn;
		private final double[] x;
		private final double[] pred;
		
		public AccuracyCounter(final DModel<T> fn, final double[] x) {
			this.fn = fn;
			this.x = x;
			pred = new double[fn.noutcomes()];
		}
		
		@Override
		public void observe(final T ei) {
			if(ei.category()>=0) {
				fn.predict(x,ei,pred);
				final boolean good = (pred!=null)&&isGoodPrediction(pred,ei);
				if(good) {
					++nGood;
				}
				++n;
			}
		}

		@Override
		public void observe(final AccuracyCounter<T> o) {
			n += o.n;
			nGood += o.nGood;
		}

		@Override
		public AccuracyCounter<T> newObserver() {
			return new AccuracyCounter<T>(fn,x);
		}
	}
	
	public static <T extends ExampleRow> double accuracy(final DModel<T> fn, final Iterable<T> as, final double[] x) {
		final AccuracyCounter<T> counter = new AccuracyCounter<T>(fn,x);
		final Log log = LogFactory.getLog(fn.getClass());
		final String logString = fn.getClass().getName() + " accuracy scan";
		final ThreadedReducer<T,AccuracyCounter<T>,AccuracyCounter<T>> reducer = new ThreadedReducer<T,AccuracyCounter<T>,AccuracyCounter<T>>(5,logString);
		reducer.reduce(as,counter);
		final double accuracy = counter.nGood/(double)counter.n; 
		log.info("accuarcy: " + counter.nGood + "/" + counter.n + " = " + accuracy);
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
