package com.winvector.opt.impl;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.util.Ticker;


public final class DataFn<T extends ExampleRow,X extends T> implements VectorFn {
	private final Iterable<X> dat;
	private final LinearContribution<T> underlying;
	private final int gulpSize = 1000;
	private final int parallelism = 10;
	
	public DataFn(final LinearContribution<T> underlying, final Iterable<X> dat) {
		this.underlying = underlying;
		this.dat = dat;
	}

	@Override
	public int dim() {
		return underlying.dim();
	}
	
	private final class EJob implements Runnable {
		public final VEval baseRes;
		private final Iterable<X> sourcei;
		
		public EJob(final VEval baseRes,
				final Iterable<X> sourcei) {
			this.baseRes = baseRes;
			this.sourcei = sourcei;
		}

		@Override
		public void run() {
			final VEval r = new VEval(baseRes.x,baseRes.gx!=null,baseRes.hx!=null); 
			final double[] pscratch = new double[underlying.noutcomes()];
			for(final X di: sourcei) {
				if((di.category()>=0)&&(di.weight()>0.0)) {
					underlying.addTerm(r.x,r.gx!=null,r.hx!=null,di,r,pscratch);
				}
			}
			synchronized (baseRes) {
				baseRes.add(r);
			}
		}
		
	}

	@SuppressWarnings("unused")
	@Override
	public VEval eval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
		final VEval r = new VEval(x,wantGrad,wantHessian);
		final ThreadPoolExecutor executor;
		if(parallelism>1) {
			final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(2*parallelism + 10);
			executor = new ThreadPoolExecutor(parallelism-1,parallelism-1,1000L,TimeUnit.SECONDS,workQueue);
		} else {
			executor = null;
		}
		final Ticker ticker = new Ticker();
		ArrayList<X> al = new ArrayList<X>(gulpSize);
		for(final X di: dat) {
			ticker.tick();
			al.add(di);
			if(al.size()>=gulpSize) {
				if((executor==null)||(executor.getTaskCount()-executor.getCompletedTaskCount()>=parallelism)) {
					new EJob(r,al).run();
				} else {
					executor.execute(new EJob(r,al));
				}
				al = new ArrayList<X>(gulpSize);
			}
		}
		if(!al.isEmpty()) {
			new EJob(r,al).run();
		}
		al = null;
		executor.shutdown();
		while(!executor.isTerminated()) {
			try {
				Thread.sleep(200L);
			} catch (InterruptedException e) {
			}
		}
		return r;
	}
}
