package com.winvector.util;

/**
 * for testing, force calls of the merging code to test intermediate paths.
 * Deliberately inefficient
 * @author johnmount
 *
 */
public class ForcedIntermediateObserver<T, S extends SerialObserver<T>, Z extends ReducibleObserver<T,Z>>  {
	public void reduce(final Iterable<? extends T> dat, final S serialObserver, final Z parallelObserver) {
		for(final T t: dat) {
			if(null!=serialObserver) {
				serialObserver.observe(t);
			}
			final Z other = parallelObserver.newObserver();
			other.observe(t);
			parallelObserver.observe(other);
		}
	}
}
