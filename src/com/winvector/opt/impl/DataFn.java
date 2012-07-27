package com.winvector.opt.impl;

import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.util.ReducibleObserver;
import com.winvector.util.ThreadedReducer;


public final class DataFn<T extends ExampleRow> implements VectorFn {

	private final Iterable<? extends T> dat;
	private final LinearContribution<T> underlying;

	private final class ObsHolder implements ReducibleObserver<T,ObsHolder> {
		private final VEval r;
		private final double[] pscratch = new double[underlying.noutcomes()];
		
		public ObsHolder(final double[] x, final boolean wantGrad, final boolean wantHessian) {
			r = new VEval(x,wantGrad,wantHessian);
		}
		
		@Override
		public void observe(final T t) {
			underlying.addTerm(r.x,r.gx!=null,r.hx!=null,t,r,pscratch);
		}

		@Override
		public void observe(final ObsHolder o) {
			r.add(o.r);
		}

		@Override
		public ObsHolder newObserver() {
			return new ObsHolder(r.x,r.gx!=null,r.hx!=null);
		}
		
	}

	
	
	public DataFn(final LinearContribution<T> underlying, final Iterable<? extends T> dat) {
		this.underlying = underlying;
		this.dat = dat;
	}

	@Override
	public int dim() {
		return underlying.dim();
	}
	

	@Override
	public VEval eval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
		final String logString = "DataFn(" + underlying.getClass().getName() + ")";
		final ThreadedReducer<T,ObsHolder,ObsHolder> reducer = new ThreadedReducer<T,ObsHolder,ObsHolder>(5,logString);
		final ObsHolder base = new ObsHolder(x,wantGrad,wantHessian);
		reducer.reduce(dat,base);
		return base.r;
	}
}
