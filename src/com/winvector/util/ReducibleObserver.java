package com.winvector.util;

/**
 * For classes implementing this interface the following are true:
 *    1) a.observe(T t1,t2, ...,tk) is equivalent to a.observe(T ta,tb,...,tz) where a,b,...z is any permutation of 1...k
 *    2) a.observe(T t1,t2,...,tk) is equivalent to a.observe(b.observe(T t1,t2,...,tk)) for any k (including the empty list)
 * @author johnmount
 *
 * @param <T>
 */
public interface ReducibleObserver<T,Z extends ReducibleObserver<T,Z>> extends SerialObserver<T> {
	public void observe(final Z o);
	public Z newObserver();
}
