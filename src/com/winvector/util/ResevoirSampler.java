package com.winvector.util;

import java.util.ArrayList;
import java.util.Random;

/**
 * keep a uniform sample of items seen
 * @author johnmount
 *
 * @param <T>
 */
public final class ResevoirSampler<T> {
	private final int maxSize;
	private long nSeen = 0;
	private final ArrayList<T> store;
	private final Random rand;
	
	public ResevoirSampler(final int maxSize, final long randSeed) {
		this.maxSize = maxSize;
		store = new ArrayList<T>(maxSize);
		rand = new Random(randSeed);
	}
	
	public void observe(final T t) {
		++nSeen;
		if(store.size()<maxSize) {
			store.add(t);
		} else {
			// Invariant: if sampling were to stop right after this observation then this observation should be in the sample
			//            with odds exactly maxSize out of nSeen.  Also using a uniform random selection from 0 through nSeen is also
			//            a uniform random selection from 0 through maxSize-1 when it happens to be less than maxSize.
			long position = rand.nextLong()%nSeen;
			if(position<0) {
				position += nSeen;
			}
			if(position<store.size()) {
				store.set((int)position,t);
			}
		}
	}
	
	public Iterable<T> data() {
		return store;
	}
}
