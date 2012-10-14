package com.winvector.opt.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.winvector.opt.def.ExampleRow;
import com.winvector.util.BurstMap;
import com.winvector.variables.VariableEncodings;

public abstract class AdapterIterableBase<T extends ExampleRow> implements Iterable<T> {

	private final Iterable<BurstMap> rawSource;
	public final VariableEncodings adapter;
	
	protected AdapterIterableBase(final VariableEncodings adapter, final Iterable<BurstMap> rawSource) {
		this.rawSource = rawSource;
		this.adapter = adapter;
	}
	
	protected SparseExampleRow buildSparseRow(final BurstMap row) {
		final SparseSemiVec vec = adapter.vector(row);
		if(null==vec) {
			return null;
		}
		int catInt = -1; // allowed to be missing
		final String resStr = row.getAsString(adapter.def().resultColumn);
		if((resStr!=null)&&(resStr.length()>0)) {
			final Integer category = adapter.category(resStr);
			if(category!=null) {
				catInt = category;
			}
		}
		return new SparseExampleRow(vec,1.0,catInt);
	}
	
	protected abstract T buildRow(final BurstMap row);
		
	private class AI implements Iterator<T> {
		private Iterator<BurstMap> raw = rawSource.iterator();
		private T next = null;

		public AI() {
			advance();
		}

		private void advance() {
			next = null;
			while((next==null)&&(raw!=null)) {
				if(!raw.hasNext()) {
					raw = null;
					break;
				}
				final BurstMap row = raw.next();
				next = buildRow(row);
			}
		}

		@Override
		public boolean hasNext() {
			return next!=null;
		}

		@Override
		public T next() {
			if(!hasNext()) {
				throw new NoSuchElementException();
			}
			final T ret = next;
			advance();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public Iterator<T> iterator() {
		return new AI();
	}
}

