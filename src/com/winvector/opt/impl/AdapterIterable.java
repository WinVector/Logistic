package com.winvector.opt.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedMap;

import com.winvector.opt.def.ExampleRow;
import com.winvector.util.BurstMap;
import com.winvector.variables.VariableEncodings;

public final class AdapterIterable implements Iterable<ExampleRow> {

	
	private final Iterable<BurstMap> rawSource;
	public final VariableEncodings adapter;
	
	public AdapterIterable(final VariableEncodings adapter, final Iterable<BurstMap> rawSource) {
		this.rawSource = rawSource;
		this.adapter = adapter;
	}
		
	private class AI implements Iterator<ExampleRow> {
		private Iterator<BurstMap> raw = rawSource.iterator();
		private ExampleRow next = null;

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
				final String resStr = row.getAsString(adapter.def().resultColumn);
				if((resStr==null)||(resStr.length()<=0)) {
					continue;
				}
				final SortedMap<Integer, Double> vec = adapter.vector(row);
				if(vec==null) {
					continue;
				}
				final Integer category = adapter.category(resStr);
				final int catInt;
				if(category!=null) {
					catInt = category;
				} else {
					catInt = -1;
				}
				next = new SparseExampleRow(vec,catInt);
			}
		}

		@Override
		public boolean hasNext() {
			return next!=null;
		}

		@Override
		public ExampleRow next() {
			if(!hasNext()) {
				throw new NoSuchElementException();
			}
			final ExampleRow ret = next;
			advance();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public Iterator<ExampleRow> iterator() {
		return new AI();
	}
}
