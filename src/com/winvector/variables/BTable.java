package com.winvector.variables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.logistic.SigmoidLossMultinomial;
import com.winvector.opt.def.DModel;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;
import com.winvector.util.BurstMap;
import com.winvector.util.ResevoirSampler;
import com.winvector.util.Ticker;

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

	private static final class BLevelRow {
		public double total = 0.0;
		public final double[] totalByCategory;
		public final double[] sumRunCategory;
		public final double[] sumPCorrectCategory;
		public final double[] sumPCategory;
		
		public BLevelRow(final int noutcomes) {
			totalByCategory = new double[noutcomes];
			sumRunCategory = new double[noutcomes];
			sumPCorrectCategory = new double[noutcomes];
			sumPCategory = new double[noutcomes];
		}
	}
	
	/*
	 * per-variable statistics
	 */
	private static final class BStat {
		// def
		public final int noutcomes;
		public final VariableMapping oldAdaption;
		public final double smallValue;
		// raw stats
		public double sumTotal = 0.0;
		public final double[] totalByCategory;
		public final Map<String,BLevelRow> levelStats = new HashMap<String,BLevelRow>(1000);

		public BStat(final VariableMapping oldAdaption, final int noutcomes) {
			this.oldAdaption = oldAdaption;
			this.noutcomes = noutcomes;
			smallValue = 0.1/(double)noutcomes;
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
				blevelRow = new BLevelRow(noutcomes);
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
					blevelRow = new BLevelRow(noutcomes);
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
	
	public final Map<String,Map<String,double[]>> levelEncodings = new HashMap<String,Map<String,double[]>>();
	public final Map<String,Map<String,String[]>> levelEncodingNames = new HashMap<String,Map<String,String[]>>();
	public VariableEncodings oldAdapter;
	public VariableEncodings newAdapter;
	public double[] warmStart = null;
	public Iterable<BurstMap> sample = null;

	public static BTable buildStatBasedEncodings(final Set<String> varsToEncode,
			final Iterable<BurstMap> trainSource, final VariableEncodings oldAdapter, 
			final double[] oldX, final Random rand) {
		final Log log = LogFactory.getLog(BTable.class);
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
		final DModel<ExampleRow> sigmoidLoss;
		if(oldX!=null) {
			sigmoidLoss = new SigmoidLossMultinomial(oldAdapter.dim(),oldAdapter.noutcomes());
		} else {
			sigmoidLoss = null;
		}
		// go through data to get stats
		log.info("start variable re-encoding scan");
		final Ticker ticker = new Ticker();
		final ResevoirSampler<BurstMap> sampler = new ResevoirSampler<BurstMap>(100000,rand.nextLong());
		final double[] pred = new double[oldAdapter.noutcomes()];
		if(null==sigmoidLoss) {
			final int no = oldAdapter.noutcomes();
			final double pi = 1.0/(double)no;
			Arrays.fill(pred,pi);			
		}
		for(final BurstMap row: trainSource) {
			ticker.tick();
			// score the standard way
			final double weight = oldAdapter.weight(row);
			if(weight>0.0) {
				sampler.observe(row);
				final SparseSemiVec vec = oldAdapter.vector(row);
				final String resStr = row.getAsString(oldAdapter.def().resultColumn);
				final int category = oldAdapter.category(resStr);
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
		log.info("done variable re-encoding scan");
		final BTable res = new BTable();
		res.sample = sampler.data();
		log.info("start new adapter construction");
		// convert stats into encodings
		final Map<String,BRes> bData = new HashMap<String,BRes>();
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
		res.newAdapter = new VariableEncodings(oldAdapter.def(),oldAdapter.useIntercept(),oldAdapter.weightKey,res.levelEncodings,res.levelEncodingNames);
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
		log.info("done new adapter construction");
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
