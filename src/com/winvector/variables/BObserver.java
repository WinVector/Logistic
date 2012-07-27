package com.winvector.variables;

import java.util.ArrayList;
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
	
	public static final class BRes {
		public final Map<String,double[]> codesByLevel = new HashMap<String,double[]>();
		public final Map<String,String[]> codesNamesByLevel = new HashMap<String,String[]>();
		public final Map<String,double[]> warmStartByOutcome = new HashMap<String,double[]>();
	}
	
	private final class BLevelRow {
		public double total = 0.0;
		public final double[] totalByCategory;
		public final double[] sumRunCategory;
		public final double[] sumPCorrectCategory;
		public final double[] sumPCategory;
		
		public BLevelRow() {
			totalByCategory = new double[noutcomes];
			sumRunCategory = new double[noutcomes];
			sumPCorrectCategory = new double[noutcomes];
			sumPCategory = new double[noutcomes];
		}

		public void observe(final BLevelRow o) {
			total += o.total;
			sumLeft(totalByCategory,o.totalByCategory);
			sumLeft(sumRunCategory,o.sumRunCategory);
			sumLeft(sumPCorrectCategory,o.sumPCorrectCategory);
			sumLeft(sumPCategory,o.sumPCategory);
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
			blevelRow.total += weight;
			blevelRow.totalByCategory[category] += weight;
			for(int i=0;i<noutcomes;++i) {
				blevelRow.sumPCategory[i] += weight*pred[i];
			}
			blevelRow.sumRunCategory[category] += weight*1.0/Math.max(pred[category],smallValue);
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

		public BRes encode(final String variable, final VariableEncodings oldAdapter, final double[] oldX) {
			final double smooth = 0.5;
			final BRes res = new BRes();
			final double sumAll = sumTotal + smooth; 
			for(final String level: oldAdapter.def().catLevels.get(variable).keySet()) {
				final ArrayList<Double> codev = new ArrayList<Double>(); 
				final ArrayList<String> coname = new ArrayList<String>(); 
				final Map<String,Integer> effectPositions = new HashMap<String,Integer>();
				BLevelRow blevelRow = levelStats.get(level);
				if(null==blevelRow) {
					blevelRow = new BLevelRow();
					levelStats.put(level,blevelRow);
				}
				final double sumLevel = blevelRow.total + smooth;
				for(final Map.Entry<String,Integer> me: oldAdapter.outcomeCategories.entrySet()) {
					final String outcome = me.getKey();
					final int category = me.getValue();
					final double sumOutcome = totalByCategory[category] + smooth;
					final double sumLevelOutcome = blevelRow.totalByCategory[category] + smooth;
					final double bayesTerm = (sumAll*sumLevelOutcome)/(sumOutcome*sumLevel); // initial Bayesian utility
					codev.add(bayesTerm);
					coname.add("bayes_" + outcome);
					codev.add(Math.log(bayesTerm));
					coname.add("logbayes_" + outcome);
					final double sumRun = blevelRow.sumRunCategory[category] + smooth;
					final double runTerm = sumRun/sumLevel;
					codev.add(runTerm);
					coname.add("runTerm_" + outcome);
					codev.add(Math.log(runTerm));
					coname.add("logRunTerm_" + outcome);
					final double superBalanceTerm = (blevelRow.totalByCategory[category] - blevelRow.sumPCorrectCategory[category])/sumLevel;
					codev.add(superBalanceTerm);
					coname.add("superBalance_" + outcome);
					final double balanceTerm = (blevelRow.totalByCategory[category] - blevelRow.sumPCategory[category]);
					codev.add(balanceTerm);
					coname.add("balance_" + outcome);
					if(oldX!=null) {
						final int base = category*oldAdapter.vdim;
						final double cumulativeEffect = oldAdaption.effect(base,oldX,level); // cumulative wisdom to date
						effectPositions.put(outcome,codev.size()); // mark where cumulative effect term went  
						codev.add(cumulativeEffect); 
						coname.add("effect_" + outcome);
					}
				}
				// finish encode
				final int width = codev.size();
				final double[] code = new double[width];
				for(int i=0;i<width;++i) {
					code[i] = codev.get(i);
				}
				res.codesByLevel.put(level,code);
				if(!effectPositions.isEmpty()) {
					for(final String outcome: oldAdapter.outcomeCategories.keySet()) {
						final double[] warmStart = new double[width];
						warmStart[effectPositions.get(outcome)] = 1.0;
						res.warmStartByOutcome.put(outcome,warmStart);
					}
				}
				final String[] names = new String[width];
				for(int i=0;i<width;++i) {
					names[i] = coname.get(i);
				}
				res.codesNamesByLevel.put(level,names);
			}
			return res;
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