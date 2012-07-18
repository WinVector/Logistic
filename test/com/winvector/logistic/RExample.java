package com.winvector.logistic;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;

public class RExample implements Iterable<ExampleRow> {
	private final double[][] rawdat;
	public final int dim;

	public RExample(final double[][] rawdat) {
		this.rawdat = rawdat;
		dim = rawdat[0].length;
	}

	private class SI implements Iterator<ExampleRow> {
		private int i = 0;
		private ExampleRow next = null;

		public SI() {
			advance();
		}

		private void advance() {
			next = null;
			while((next==null)&&(i<rawdat.length)) {
				final int category = rawdat[i][dim-1]>0.5?1:0;
				final double[] vec = new double[dim];
				vec[0] = 1.0;
				for(int j=0;j<dim-1;++j) {
					if(rawdat[i][j]!=0.0) {
						vec[j+1] = rawdat[i][j];
					}
				}
				next = new SparseExampleRow(new SparseSemiVec(vec),1.0,category);
				++i;
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
		return new SI();
	}
}

