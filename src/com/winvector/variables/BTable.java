package com.winvector.variables;

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
import com.winvector.variables.BObserver.BRes;
import com.winvector.variables.BObserver.BStat;

/**
 * very low dimensional re-encoding of levels
 * @author johnmount
 *
 */
public final class BTable {
	public final Map<String,Map<String,double[]>> levelEncodings = new HashMap<String,Map<String,double[]>>();
	public final Map<String,Map<String,String[]>> levelEncodingNames = new HashMap<String,Map<String,String[]>>();
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
		final ThreadedReducer<BurstMap,BSampler,BObserver> reducer = new ThreadedReducer<BurstMap,BSampler,BObserver>(5,log);
		reducer.reduce(trainSource,bsampler,bobs);
		log.info("done variable re-encoding scan");
		final BTable res = new BTable();
		res.sample = bsampler.sampler.data();
		log.info("start new adapter construction");
		// convert stats into encodings
		final Map<String,BRes> bData = new HashMap<String,BRes>();
		res.oldAdapter = oldAdapter;
		for(final Map.Entry<String,BStat> me: bobs.stats.entrySet()) {
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
