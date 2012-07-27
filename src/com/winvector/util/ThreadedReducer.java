package com.winvector.util;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * 
 * @author johnmount
 *
 * @param <T> type being observed
 * @param <Z> type of observer that will work in parallel 
 * @param <S> type of observer that will pre-scan serially
 */
public final class ThreadedReducer<T, S extends SerialObserver<T>, Z extends ReducibleObserver<T,Z>> {
	private final String logString;
	private final int gulpSize = 1000;
	private final int parallelism;
	
	public ThreadedReducer(final int parallelism, final String logString) {
		this.parallelism = parallelism;
		this.logString = logString;
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

	
	/**
	 * 
	 * @param dat data source
	 * @param serialObserver (option can be null) a cheap observer that will be applied to all data serially
	 * @param parallelObserver an expensive observer that will be applied in parallel
	 */
	public void reduce(final Iterable<? extends T> dat, final S serialObserver, final Z parallelObserver) {
		final Ticker ticker = new Ticker(logString);		
		if(parallelism>1) {
			final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(2*parallelism + 10);
			final ThreadPoolExecutor executor = new ThreadPoolExecutor(parallelism,parallelism,1000L,TimeUnit.SECONDS,workQueue);
			ArrayList<T> al = new ArrayList<T>(gulpSize);
			for(final T di: dat) {
				ticker.tick();
				if(serialObserver!=null) {
					serialObserver.observe(di);
				}
				al.add(di);
				if(al.size()>=gulpSize) {
					if((executor==null)||(executor.getTaskCount()-executor.getCompletedTaskCount()>parallelism)) {
						new EJob(parallelObserver,al).run();
					} else {
						executor.execute(new EJob(parallelObserver,al));
					}
					al = new ArrayList<T>(gulpSize);
				}
			}
			if(!al.isEmpty()) {
				new EJob(parallelObserver,al).run();
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
			for(final T di: dat) {
				if(serialObserver!=null) {
					serialObserver.observe(di);
				}
				parallelObserver.observe(di);
			}
		}
	}
	
	/**
	 * 
	 * @param dat data source
	 * @param parallelObserver an expensive observer that will be applied in parallel
	 */
	public void reduce(final Iterable<? extends T> dat, final Z parallelObserver) {
		reduce(dat,null,parallelObserver);
	}
}
