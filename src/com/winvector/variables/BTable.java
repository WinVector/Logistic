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
import com.winvector.util.BurstMap;
import com.winvector.util.ResevoirSampler;
import com.winvector.util.SerialObserver;
import com.winvector.util.ThreadedReducer;
import com.winvector.variables.BObserver.BLevelRow;
import com.winvector.variables.BObserver.BStat;
import com.winvector.variables.LevelVectors.VectorRow;

/**
 * very low dimensional re-encoding of levels
 * @author johnmount
 *
 */
public final class BTable {
	public VariableEncodings oldAdapter;
	public VariableEncodings newAdapter;
	public double[] warmStart = null;
	public Iterable<BurstMap> sample = null;
	
	private static final class BSampler implements SerialObserver<BurstMap> {
		public final VariableEncodings oldAdapter;
		public final ResevoirSampler<BurstMap> sampler;

		public BSampler(final VariableEncodings oldAdapter, final long randSeed) {
			this.oldAdapter = oldAdapter;
			sampler = new ResevoirSampler<BurstMap>(100000,randSeed);
		}
		
		@Override
		public void observe(final BurstMap row) {
			final double weight = oldAdapter.weight(row);
			if(weight>0.0) {
				final String resStr = row.getAsString(oldAdapter.def().resultColumn);
				final int category = oldAdapter.category(resStr);
				if(category>=0) {
					sampler.observe(row);
				}
			}
		}
	}
	
	

	private static final class GeneralIndicator {		
		@SuppressWarnings("unused")
		public final String var;
		public final String name;
		public final Map<String,Double> levelEncodings = new HashMap<String,Double>(); // indexed by levels of var
		public final int warmStartOutcome;
		
		public GeneralIndicator(final String var, final String name, final int nlevels, final int warmStartOutcome) {
			this.var = var;
			this.name = name;
			this.warmStartOutcome = warmStartOutcome;
		}
				
		public double normSq() {
			double nsq = 0.0;
			for(final double v: levelEncodings.values()) {
				nsq += v*v;
			}
			return nsq;
		}
	}
	

	public static ArrayList<GeneralIndicator> encode(final BStat stat, final String variable, final VariableEncodings oldAdapter, final double[] oldX) {
		final ArrayList<GeneralIndicator> res = new ArrayList<GeneralIndicator>();
		final double smooth = 0.5;
		final double sumAll = stat.sumTotal + smooth;
		final Set<String> levels = oldAdapter.def().catLevels.get(variable).keySet();
		final int nlevels = levels.size();
		for(final Map.Entry<String,Integer> me: oldAdapter.outcomeCategories.entrySet()) {
			final String outcome = me.getKey();
			final int category = me.getValue();
			final double sumOutcome = stat.totalByCategory[category] + smooth;
			final GeneralIndicator logBayesI = new GeneralIndicator(variable,"logbayes_" + outcome,nlevels,-1);
			final GeneralIndicator runI= new GeneralIndicator(variable,"runTerm_" + outcome,nlevels,-1);
			final GeneralIndicator logRunI = new GeneralIndicator(variable,"logRunTerm_" + outcome,nlevels,-1);
			final GeneralIndicator runFI= new GeneralIndicator(variable,"runTermF_" + outcome,nlevels,-1);
			final GeneralIndicator logRunFI = new GeneralIndicator(variable,"logRunTermF_" + outcome,nlevels,-1);
			final GeneralIndicator balanceI = new GeneralIndicator(variable,"balance_" + outcome,nlevels,-1);
			final GeneralIndicator balanceLR = new GeneralIndicator(variable,"balanceLR_" + outcome,nlevels,-1);
			final GeneralIndicator superBalanceI = new GeneralIndicator(variable,"superBalance_" + outcome,nlevels,-1);
			final GeneralIndicator superBalanceLR = new GeneralIndicator(variable,"superBalanceLR_" + outcome,nlevels,-1);
			final GeneralIndicator effectI;
			if(oldX!=null) {
				effectI = new GeneralIndicator(variable,"effectTerm_" + outcome,nlevels,category);
			} else {
				effectI = null;
			}
			for(final String level: levels) {
				final BLevelRow blevelRow = stat.levelStats.get(level);
				if(blevelRow!=null) {
					final double sumLevel = blevelRow.totalForLevel + smooth;
					{
						final double sumLevelOutcome = blevelRow.totalByCorrectCategory[category] + smooth;
						final double bayesTerm = (sumAll*sumLevelOutcome)/(sumOutcome*sumLevel); // initial Bayesian utility
						logBayesI.levelEncodings.put(level,Math.log(bayesTerm));
					}
					{
						final double runTerm = (blevelRow.sumRunCorrectCategory[category]+smooth)/(blevelRow.totalByCorrectCategory[category]+smooth);
						runI.levelEncodings.put(level,runTerm);
						logRunI.levelEncodings.put(level,Math.log(runTerm));
						final double runTermF = (blevelRow.sumRunFixedCategory[category]+smooth)/(blevelRow.totalForLevel+smooth);
						runFI.levelEncodings.put(level,runTermF);
						logRunFI.levelEncodings.put(level,Math.log(runTermF));
					}
					{
						final double balanceTerm = (blevelRow.totalByCorrectCategory[category] - blevelRow.sumPFixedCategory[category])/sumLevel;
						balanceI.levelEncodings.put(level,balanceTerm);
						final double balanceRTerm = (blevelRow.totalByCorrectCategory[category]+smooth)/(blevelRow.sumPFixedCategory[category]+smooth);
						balanceLR.levelEncodings.put(level,Math.log(balanceRTerm));
					}
					{
						final double superBalanceTerm = (blevelRow.totalByCorrectCategory[category] - blevelRow.sumPCorrectCategory[category])/sumLevel;
						superBalanceI.levelEncodings.put(level,superBalanceTerm);
						final double superBalanceRTerm = (blevelRow.totalByCorrectCategory[category]+smooth)/(blevelRow.sumPCorrectCategory[category]+smooth);
						superBalanceLR.levelEncodings.put(level,Math.log(superBalanceRTerm));
					}
				}
				if(oldX!=null) {
					final int base = category*oldAdapter.vdim;
					final double cumulativeEffect = stat.oldAdaption.effect(base,oldX,level); // cumulative wisdom to date
					effectI.levelEncodings.put(level,cumulativeEffect);
				}
			}
			// finish encode
			for( final GeneralIndicator indI : new GeneralIndicator[] {
					effectI,
					logBayesI, 
					runI, logRunI, runFI, logRunFI, 
					balanceI, balanceLR,
					superBalanceI, superBalanceLR, 
					}) {
				if(null!=indI) {
					if(indI.normSq()>1.0e-12) {
						res.add(indI);
					}
				}
			}
		}
		return res;
	}
	
	public static BTable buildStatBasedEncodings(final Set<String> varsToEncode,
			final Iterable<BurstMap> trainSource, final VariableEncodings oldAdapter, 
			final double[] oldX, final Random rand) {
		final Log log = LogFactory.getLog(BTable.class);
		final DModel<ExampleRow> sigmoidLoss;
		if(oldX!=null) {
			sigmoidLoss = new SigmoidLossMultinomial(oldAdapter.dim(),oldAdapter.noutcomes());
		} else {
			sigmoidLoss = null;
		}
		// go through data to get stats
		log.info("start variable re-encoding scan");
		final BSampler bsampler = new BSampler(oldAdapter,rand.nextLong());
		final BObserver bobs = new BObserver(varsToEncode,oldAdapter,sigmoidLoss,oldX);
		final ThreadedReducer<BurstMap,BSampler,BObserver> reducer = new ThreadedReducer<BurstMap,BSampler,BObserver>(5,"BTable encoding");
		reducer.reduce(trainSource,bsampler,bobs);
		log.info("done variable re-encoding scan");
		final BTable res = new BTable();
		res.sample = bsampler.sampler.data();
		log.info("start new adapter construction");
		// convert stats into encodings
		res.oldAdapter = oldAdapter;
		final Map<String,Map<String,VectorRow>> levelEncodings = new HashMap<String,Map<String,VectorRow>>(); // variable to level to vector
		for(final Map.Entry<String,BStat> me: bobs.stats.entrySet()) {
			final String variable = me.getKey();
			final BStat si = me.getValue();
			final ArrayList<GeneralIndicator> newCodes = encode(si,variable,oldAdapter,oldX);
			if((null!=newCodes)&&(newCodes.size()>0)) {
				final int edim = newCodes.size();
				final Map<String,VectorRow> enc = new HashMap<String,VectorRow>();  // level to vector
				final String[] names = new String[edim];
				{
					int giIndex = 0;
					for(final GeneralIndicator gi: newCodes) {
						names[giIndex] = gi.name;
						++giIndex;
					}
				}
				for(final String level: si.levelStats.keySet()) {
					final VectorRow row = new VectorRow(names,new double[edim],new int[edim]);
					Arrays.fill(row.warmStartOutcome,-1);
					enc.put(level,row);
					int giIndex = 0;
					for(final GeneralIndicator gi: newCodes) {
						final Double lv = gi.levelEncodings.get(level);
						row.levelEncodings[giIndex] = lv!=null?lv:0.0;
						row.warmStartOutcome[giIndex] = gi.warmStartOutcome;  // warm start assignment (not value) independent of level
						++giIndex;
					}
				}
				levelEncodings.put(variable,enc);
			}
		}
		// build new adapter
		res.newAdapter = new VariableEncodings(oldAdapter.def(),oldAdapter.useIntercept(),oldAdapter.weightKey,levelEncodings);
		// build warmstart vector
		if(oldX!=null) {
			final Map<String,VariableMapping> newAdaptions = new HashMap<String,VariableMapping>();
			for(final VariableMapping newAdaption: res.newAdapter.adaptions) {
				newAdaptions.put(newAdaption.origColumn(),newAdaption);
			}
			res.warmStart = new double[res.newAdapter.dim()*res.newAdapter.noutcomes()];
			for(final Map.Entry<String,Integer> mc: oldAdapter.outcomeCategories.entrySet()) {
				//final String outcome = mc.getKey();
				final int cati = mc.getValue();
				final int baseOld = cati*oldAdapter.vdim;
				final int baseNew = cati*res.newAdapter.vdim;
				for(final VariableMapping oldAdaption: oldAdapter.adaptions) {
					final String variable = oldAdaption.origColumn();
					final VariableMapping newAdaption = newAdaptions.get(variable);
					if(levelEncodings.containsKey(variable)) {
						final Map<String, VectorRow> newCodes = levelEncodings.get(variable);
						if((null!=newCodes)&&(!newCodes.isEmpty())) {
							final VectorRow warmStart = newCodes.values().iterator().next(); // warm start assignment (not value) independent of level
							for(int i=newAdaption.indexL();i<newAdaption.indexR();++i) {
								res.warmStart[baseNew+i] = warmStart.warmStartOutcome[i-newAdaption.indexL()]==cati?1.0:0.0;
							}
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
