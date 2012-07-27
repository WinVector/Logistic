package com.winvector.util;

/**
 * for testing, force both reducers into serial op
 * @author johnmount
 *
 * @param <T>
 * @param <S>
 * @param <Z>
 */
public final class FullSerialReducer<T, S extends SerialObserver<T>, Z extends ReducibleObserver<T,Z>>  {
	public void reduce(final Iterable<? extends T> dat, final S serialObserver, final Z parallelObserver) {
		for(final T t: dat) {
			if(null!=serialObserver) {
				serialObserver.observe(t);
			}
			parallelObserver.observe(t);
		}
	}
}
