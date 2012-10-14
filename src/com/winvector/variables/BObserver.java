package com.winvector.variables;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.winvector.opt.def.DModel;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;
import com.winvector.util.BurstMap;
import com.winvector.util.ReducibleObserver;

/**
 * statistics accumulator used by BTable
 * builds stats on categorical variables with a very large number of levels
 * @author johnmount
 *
 */
final class BObserver implements ReducibleObserver<BurstMap,BObserver> {
	// def
	public final int noutcomes;
	public final double smallValue;
	
	private static void sumLeft(final double[] x, final double[] delta) {
		final int n = x.length;
		for(int i=0;i<n;++i) {
			x[i] += delta[i];
		}
	}
		
	public final class BLevelRow {
		public double totalForLevel = 0.0;
		public final double[] totalByCorrectCategory;
		public final double[] sumRunCorrectCategory;
		public final double[] sumRunFixedCategory;
		public final double[] sumPCorrectCategory;
		public final double[] sumPFixedCategory;
		
		public BLevelRow() {
			totalByCorrectCategory = new double[noutcomes];
			sumRunCorrectCategory = new double[noutcomes];
			sumRunFixedCategory = new double[noutcomes];
			sumPCorrectCategory = new double[noutcomes];
			sumPFixedCategory = new double[noutcomes];
		}

		public void observe(final BLevelRow o) {
			totalForLevel += o.totalForLevel;
			sumLeft(totalByCorrectCategory,o.totalByCorrectCategory);
			sumLeft(sumRunCorrectCategory,o.sumRunCorrectCategory);
			sumLeft(sumRunFixedCategory,o.sumRunFixedCategory);
			sumLeft(sumPCorrectCategory,o.sumPCorrectCategory);
			sumLeft(sumPFixedCategory,o.sumPFixedCategory);
		}
	}
	
	/*
	 * per-variable statistics
	 */
	public final class BStat {
		// def
		public final VariableMapping oldAdaption;
		// raw stats
		public double sumTotal = 0.0;
		public final double[] totalByCategory;
		public final Map<String,BLevelRow> levelStats = new HashMap<String,BLevelRow>(1000);

		public BStat(final VariableMapping oldAdaption) {
			this.oldAdaption = oldAdaption;
			totalByCategory = new double[noutcomes];
		}
		
		public BLevelRow newRow() {
			return new BLevelRow();
		}
		
		/**
		 * 
		 * @param outcome training outcome
		 * @param level level of variable
		 * @param pred current model predictions
		 * @param correct category
		 * @paream weight>0.0
		 */
		public void observe(final String outcome, final String level, final double[] pred, final int category, final double weight) {
			sumTotal += weight;
			totalByCategory[category] += weight;
			BLevelRow blevelRow = levelStats.get(level);
			if(null==blevelRow) {
				blevelRow = new BLevelRow();
				levelStats.put(level,blevelRow);
			}
			blevelRow.totalForLevel += weight;
			blevelRow.totalByCorrectCategory[category] += weight;
			for(int i=0;i<noutcomes;++i) {
				blevelRow.sumPFixedCategory[i] += weight*pred[i];
				if(category==i) {
					blevelRow.sumRunFixedCategory[i] += weight*1.0/Math.max(pred[i],smallValue);
				} else {
					blevelRow.sumRunFixedCategory[i] += weight*-1.0/Math.max(1.0-pred[i],smallValue);
				}
			}
			blevelRow.sumRunCorrectCategory[category] += weight*1.0/Math.max(pred[category],smallValue);
			blevelRow.sumPCorrectCategory[category] += weight*pred[category];
		}

		public void observe(final BStat o) {
			sumTotal += o.sumTotal;
			sumLeft(totalByCategory,o.totalByCategory);
			for(final Map.Entry<String,BLevelRow> op: o.levelStats.entrySet()) {
				final String key = op.getKey();
				final BLevelRow ov = op.getValue();
				BLevelRow val = levelStats.get(key);
				if(val==null) {
					val = new BLevelRow();
					levelStats.put(key,val);
				}
				val.observe(ov);
			}
		}


	}
	
	// def
	private final VariableEncodings oldAdapter;
	private  final DModel<ExampleRow> sigmoidLoss;
	private final double[] oldX;
	// scratch
	private final double[] pred;
	// data
	public final Map<String,BStat> stats = new HashMap<String,BStat>();
	
	
	public BObserver(final Set<String> varsToEncode, final VariableEncodings oldAdapter, 
			final DModel<ExampleRow> sigmoidLoss, final double[] oldX) {
		this.oldAdapter = oldAdapter;
		this.sigmoidLoss = sigmoidLoss;
		this.oldX = oldX;
		this.noutcomes = oldAdapter.noutcomes();
		smallValue = 0.1/(double)noutcomes;
		// build a quick list to access stats we are interested in
		final Map<String,VariableMapping> oldAdaptions = new HashMap<String,VariableMapping>();
		for(final VariableMapping oldAdaption: oldAdapter.adaptions) {
			oldAdaptions.put(oldAdaption.origColumn(),oldAdaption);
		}
		for(final String variable: varsToEncode) {
			stats.put(variable,new BStat(oldAdaptions.get(variable)));
		}
		pred = new double[noutcomes];
		if(null==sigmoidLoss) {
			Arrays.fill(pred,1.0/(double)noutcomes);			
		}
	}
	
	@Override
	public void observe(final BurstMap row) {
		// score the standard way
		final double weight = oldAdapter.weight(row);
		if(weight>0.0) {
			final String resStr = row.getAsString(oldAdapter.def().resultColumn);
			final int category = oldAdapter.category(resStr);
			if(category>=0) {
				final SparseSemiVec vec = oldAdapter.vector(row);
				if(null!=vec) {
					if(sigmoidLoss!=null) {
						final ExampleRow ei = new SparseExampleRow(vec,weight,category);
						sigmoidLoss.predict(oldX,ei,pred);
					}
					for(final String variable: stats.keySet()) {
						final BStat btable = stats.get(variable);
						final String level = row.getAsString(variable);
						btable.observe(resStr,level,pred,category,weight);
					}
				}
			}
		}
	}

	@Override
	public void observe(final BObserver o) {
		for(final Map.Entry<String,BStat> me: stats.entrySet()) {
			final String key = me.getKey();
			final BStat val = me.getValue();
			final BStat ov = o.stats.get(key);
			val.observe(ov);
		}
	}

	@Override
	public BObserver newObserver() {
		return new BObserver(stats.keySet(),oldAdapter,sigmoidLoss,oldX);
	}
}