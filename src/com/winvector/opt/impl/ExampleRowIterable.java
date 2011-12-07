package com.winvector.opt.impl;

import com.winvector.opt.def.ExampleRow;
import com.winvector.util.BurstMap;
import com.winvector.variables.VariableEncodings;

public final class ExampleRowIterable extends AdapterIterableBase<ExampleRow> {

	public ExampleRowIterable(final VariableEncodings adapter, final Iterable<BurstMap> rawSource) {
		super(adapter,rawSource);
	}
	
	@Override
	protected ExampleRow buildRow(final BurstMap row) {
		return buildSparseRow(row);
	}
}
