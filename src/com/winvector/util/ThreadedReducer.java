package com.winvector.util;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public final class ThreadedReducer<T,Z extends ReducibleObserver<T,Z>> {
	private final int gulpSize = 1000;
	private final int parallelism;
	
	public ThreadedReducer(final int parallelism) {
		this.parallelism = parallelism;
	}
	
	private final class EJob implements Runnable {
		public final Z baseRes;
		private final Iterable<? extends T> sourcei;
		
		public EJob(final Z baseRes,
				final Iterable<? extends T> sourcei) {
			this.baseRes = baseRes;
			this.sourcei = sourcei;
		}

		@Override
		public void run() {
			final Z r;
			synchronized (baseRes) {
				r = baseRes.newObserver();
			}
			for(final T di: sourcei) {
				r.observe(di);
			}
			synchronized (baseRes) {
				baseRes.observe(r);
			}
		}
		
	}

	
	public void reduce(final Iterable<? extends T> dat, final Z observer) {
		final Ticker ticker = new Ticker();		
		if(parallelism>1) {
			final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(2*parallelism + 10);
			final ThreadPoolExecutor executor = new ThreadPoolExecutor(parallelism,parallelism,1000L,TimeUnit.SECONDS,workQueue);
			ArrayList<T> al = new ArrayList<T>(gulpSize);
			for(final T di: dat) {
				ticker.tick();
				al.add(di);
				if(al.size()>=gulpSize) {
					if((executor==null)||(executor.getTaskCount()-executor.getCompletedTaskCount()>parallelism)) {
						new EJob(observer,al).run();
					} else {
						executor.execute(new EJob(observer,al));
					}
					al = new ArrayList<T>(gulpSize);
				}
			}
			if(!al.isEmpty()) {
				new EJob(observer,al).run();
			}
			al = null;
			executor.shutdown();
			while(!executor.isTerminated()) {
				try {
					Thread.sleep(200L);
				} catch (InterruptedException e) {
				}
			}
		} else {
			new EJob(observer,dat).run();
		}
	}
}
