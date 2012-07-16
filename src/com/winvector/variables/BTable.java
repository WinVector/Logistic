package com.winvector.variables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.winvector.logistic.SigmoidLossMultinomial;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.util.BurstMap;
import com.winvector.util.CountMap;
import com.winvector.util.CountMapV;

/**
 * very low dimensional re-encoding of levels
 * better would be to add some training versions of variables (gives us a symbolic dynamics type idea)
 * and some other stats.
 * some variables to add are powerseries or indicators or ranges of other variables (gives optimizer more knobs).
 * the warm-start on last effect trick should be kept (lets us changing encodings a lot)
 * @author johnmount
 *
 */
public final class BTable {

	private static final class BRes {
		public final Map<String,double[]> codesByLevel = new HashMap<String,double[]>();
		public final Map<String,String[]> codesNamesByLevel = new HashMap<String,String[]>();
		public final Map<String,double[]> warmStartByOutcome = new HashMap<String,double[]>();
	}

	/*
	 * per-variable statistics
	 */
	private static final class BStat {
		// def
		public final VariableMapping oldAdaption;
		// raw stats
		public double sumTotal = 0.0;
		public final CountMap<String> totalByOutcome = new CountMap<String>(CountMap.strCmp);
		public final CountMapV<String> totalByLevelByCategory;
		public final CountMap<String> totalByLevel = new CountMap<String>(CountMap.strCmp);
		public final CountMapV<String> sumRunByLevelCategory;
		public final CountMapV<String> sumPByLevelCorrectCategory;
		public final CountMapV<String> sumPByLevelCategory;

		public BStat(final VariableMapping oldAdaption, final int noutcomes) {
			this.oldAdaption = oldAdaption;
			sumRunByLevelCategory = new CountMapV<String>(CountMap.strCmp,noutcomes);
			sumPByLevelCorrectCategory = new CountMapV<String>(CountMap.strCmp,noutcomes);
			sumPByLevelCategory = new CountMapV<String>(CountMap.strCmp,noutcomes);
			totalByLevelByCategory = new CountMapV<String>(CountMap.strCmp,noutcomes);
		}
		
		/**
		 * 
		 * @param outcome training outcome
		 * @param level level of variable
		 * @param pred current model predictions
		 * @param correct category
		 */
		public void observe(final String outcome, final String level, final double[] pred, final int category) {
			sumTotal += 1.0;
			totalByLevel.observe(level,1.0);
			totalByOutcome.observe(outcome,1.0);
			totalByLevelByCategory.observe(level,category,1.0);
			final int nc = pred.length;
			for(int i=0;i<nc;++i) {
				sumPByLevelCategory.observe(level,category,pred[i]);
			}
			final double smallValue = 0.1/(double)nc;
			sumRunByLevelCategory.observe(level,category,1.0/Math.max(pred[category],smallValue));
			sumPByLevelCorrectCategory.observe(level,category,pred[category]);
		}

		public BRes encode(final String variable, final VariableEncodings oldAdapter, final double[] oldX) {
			final double smooth = 0.5;
			final BRes res = new BRes();
			final double sumAll = sumTotal + smooth; 
			for(final String level: oldAdapter.def().catLevels.get(variable).keySet()) {
				final ArrayList<Double> codev = new ArrayList<Double>(); 
				final ArrayList<String> coname = new ArrayList<String>(); 
				final Map<String,Integer> effectPositions = new TreeMap<String,Integer>();
				final double sumLevel = totalByLevel.get(level) + smooth;
				for(final Map.Entry<String,Integer> me: oldAdapter.outcomeCategories.entrySet()) {
					final String outcome = me.getKey();
					final int category = me.getValue();
					final double sumOutcome = totalByOutcome.get(outcome) + smooth;
					final double sumLevelOutcome = totalByLevelByCategory.get(level,category) + smooth;
					final double bayesTerm = (sumAll*sumLevelOutcome)/(sumOutcome*sumLevel); // initial Bayesian utility
					codev.add(bayesTerm);
					coname.add("bayes_" + outcome);
					codev.add(Math.log(bayesTerm));
					coname.add("logbayes_" + outcome);
					final double sumRun = sumRunByLevelCategory.get(level,category) + smooth;
					final double runTerm = sumRun/sumLevel;
					codev.add(runTerm);
					coname.add("runTerm_" + outcome);
					codev.add(Math.log(runTerm));
					coname.add("logRunTerm_" + outcome);
					final double superBalanceTerm = (totalByLevelByCategory.get(level,category) - sumPByLevelCorrectCategory.get(level,category))/sumLevel;
					codev.add(superBalanceTerm);
					coname.add("superBalance_" + outcome);
					final double balanceTerm = (totalByLevelByCategory.get(level,category) - sumPByLevelCategory.get(level,category))/sumLevel;
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
	
	public final Map<String,Map<String,double[]>> levelEncodings = new TreeMap<String,Map<String,double[]>>();
	public final Map<String,Map<String,String[]>> levelEncodingNames = new TreeMap<String,Map<String,String[]>>();
	public VariableEncodings oldAdapter;
	public VariableEncodings newAdapter;
	public double[] warmStart = null;

	public static BTable buildStatBasedEncodings(final Set<String> varsToEncode,
			final Iterable<BurstMap> trainSource, final VariableEncodings oldAdapter, 
			final double[] oldX) {
		final Map<String,BStat> stats = new HashMap<String,BStat>();
		{
			// build a quick list to access stats we are interested in
			final Map<String,VariableMapping> oldAdaptions = new HashMap<String,VariableMapping>();
			for(final VariableMapping oldAdaption: oldAdapter.adaptions) {
				oldAdaptions.put(oldAdaption.origColumn(),oldAdaption);
			}
			for(final String variable: varsToEncode) {
				stats.put(variable,new BStat(oldAdaptions.get(variable),oldAdapter.noutcomes()));
			}
		}
		final LinearContribution<ExampleRow> sigmoidLoss;
		if(oldX!=null) {
			sigmoidLoss = new SigmoidLossMultinomial(oldAdapter.dim(),oldAdapter.noutcomes());
		} else {
			sigmoidLoss = null;
		}
		// go through data to get stats
		for(final BurstMap row: trainSource) {
			// score the standard way
			final Map<Integer,Double> vec = oldAdapter.vector(row);
			final String resStr = row.getAsString(oldAdapter.def().resultColumn);
			final int category = oldAdapter.category(resStr);
			final double[] pred;
			if(sigmoidLoss!=null) {
				final ExampleRow ei = new SparseExampleRow(vec,category);
				pred = sigmoidLoss.predict(oldX,ei);
			} else {
				final int no = oldAdapter.noutcomes();
				pred = new double[no];
				final double pi = 1.0/(double)no;
				for(int i=0;i<no;++i) {
					pred[i] = pi;
				}
			}
			for(final String variable: stats.keySet()) {
				final BStat btable = stats.get(variable);
				final String level = row.getAsString(variable);
				btable.observe(resStr,level,pred,category);
			}
		}
		// convert stats into encodings
		final Map<String,BRes> bData = new HashMap<String,BRes>();
		final BTable res = new BTable();
		res.oldAdapter = oldAdapter;
		for(final Map.Entry<String,BStat> me: stats.entrySet()) {
			final String variable = me.getKey();
			final BStat si = me.getValue();
			final BRes newCodes = si.encode(variable,oldAdapter,oldX);
			res.levelEncodings.put(variable,newCodes.codesByLevel);
			res.levelEncodingNames.put(variable,newCodes.codesNamesByLevel);
			bData.put(variable,newCodes);
		}
		// build new adapter
		res.newAdapter = new VariableEncodings(oldAdapter.def(),oldAdapter.useIntercept(),res.levelEncodings,res.levelEncodingNames);
		// build warmstart vector
		if(oldX!=null) {
			final Map<String,VariableMapping> newAdaptions = new HashMap<String,VariableMapping>();
			for(final VariableMapping newAdaption: res.newAdapter.adaptions) {
				newAdaptions.put(newAdaption.origColumn(),newAdaption);
			}
			res.warmStart = new double[res.newAdapter.dim()*res.newAdapter.noutcomes()];
			for(final Map.Entry<String,Integer> mc: oldAdapter.outcomeCategories.entrySet()) {
				final String outcome = mc.getKey();
				final int cati = mc.getValue();
				final int baseOld = cati*oldAdapter.vdim;
				final int baseNew = cati*res.newAdapter.vdim;
				for(final VariableMapping oldAdaption: oldAdapter.adaptions) {
					final String variable = oldAdaption.origColumn();
					final VariableMapping newAdaption = newAdaptions.get(variable);
					if(res.levelEncodings.containsKey(variable)) {
						final BRes newCodes = bData.get(variable);
						final double[] warmStart = newCodes.warmStartByOutcome.get(outcome);
						for(int i=newAdaption.indexL();i<newAdaption.indexR();++i) {
							res.warmStart[baseNew+i] = warmStart[i-newAdaption.indexL()];
						}
					} else {
						for(int i=newAdaption.indexL();i<newAdaption.indexR();++i) {
							res.warmStart[baseNew+i] = oldX[baseOld+i-newAdaption.indexL()+oldAdaption.indexL()];
						}
					}
				}
			}
		}
		return res;
	}

	public static void printTable(final Map<String,Map<String,double[]>> encodings) {
		for(final Map.Entry<String,Map<String,double[]>> me: encodings.entrySet()) {
			final String variable = me.getKey();
			System.out.println(variable);
			for(final Map.Entry<String,double[]> m2: me.getValue().entrySet()) {
				final String level = m2.getKey();
				final double[] code = m2.getValue();
				System.out.print("\t" + level);
				for(final double ci: code) {
					System.out.print("\t" + ci);
				}
				System.out.println();
			}
		}
	}

	/**
	 * translate into notation of other encoding
	 * @param standardEncodings (older encoding that we are adapted from, without any vector->vector translations)
	 * @return
	 */
	public double[] translateTo(final VariableEncodings standardEncodings, final double[] x) {
		final double[] ret = new double[standardEncodings.dim()*standardEncodings.noutcomes()];
		// get quick map of newer encodings
		final Map<String,VariableMapping> newAdaptions = new HashMap<String,VariableMapping>();
		for(final VariableMapping newAdaption: newAdapter.adaptions) {
			newAdaptions.put(newAdaption.origColumn(),newAdaption);
		}
		for(final VariableMapping orig: standardEncodings.adaptions) {
			final VariableMapping newa = newAdaptions.get(orig.origColumn());
			if(null!=newa) {
				for(final Map.Entry<String,Integer> mc: standardEncodings.outcomeCategories.entrySet()) {
					//final String outcome = mc.getKey();
					final int cati = mc.getValue();
					final int baseStandard = cati*standardEncodings.vdim;
					final int baseNew = cati*newAdapter.vdim;
					if((newa instanceof LevelVectors)&&(orig instanceof LevelIndicators)) {
						// vector adaption, assume original is level adaption
						final LevelVectors levv = (LevelVectors)newa;
						final LevelIndicators levi = (LevelIndicators)orig;
						for(final String level: levi.levels()) {
							final double effect = levv.effect(baseNew,x,level);
							final int index = levi.index(level);
							ret[baseStandard + index] = effect; 
						}
					} if((orig.indexR()-orig.indexL())==(newa.indexR()-newa.indexL())) {
						// standard match, assume same type and width
						for(int i=orig.indexL();i<orig.indexR();++i) {
							ret[baseStandard+i] = x[baseNew+newa.indexL()+i-orig.indexL()];
						}
					}
				}
			}
		}
		return ret;
	}
}
