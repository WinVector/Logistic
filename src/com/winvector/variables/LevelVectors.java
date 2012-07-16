package com.winvector.variables;

import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.winvector.util.BurstMap;

/**
 * @author johnmount
 *
 */
public final class LevelVectors implements VariableMapping {
	private static final long serialVersionUID = 1L;
	
	private final String origColumn;
	private final int index;
	private final Map<String,double[]> levelCodes;
	private final Map<String,String[]> levelNames;
	private final int targetDim;


	
	public LevelVectors(final String origColumn, final int index, 
			final Map<String,double[]> levelCodes, final Map<String,String[]> levelNames) { 
		this.origColumn = origColumn;
		this.index = index;
		this.levelCodes = levelCodes;
		this.levelNames = levelNames;
		if(!levelCodes.isEmpty()) {
			targetDim = levelCodes.values().iterator().next().length;
		} else {
			targetDim = 0;
		}
	}
	
	@Override
	public String origColumn() {
		return origColumn;
	}

	@Override
	public int indexL() {
		return index;
	}

	@Override
	public int indexR() {
		return index + targetDim;
	}
	

	@Override
	public void process(final BurstMap row, final Map<Integer, Double> vec) {
		final String level = row.getAsString(origColumn);
		if(level!=null) {
			final double[] code = levelCodes.get(level);
			if(code!=null) {
				for(int i=0;i<targetDim;++i) {
					vec.put(index+i,code[i]);
				}
			}
		}
	}

	@Override
	public String name() {
		return "CategoricalVectorEnc";
	}
	
	private double dot1coord(final int base, final double[] x, final double[] code, final int i) {
		return x[base+index+i]*code[i];
	}
	
	
	private double dot(final int base, final double[] x, final double[] code) {
		double v = 0.0;
		for(int i=0;i<targetDim;++i) {
			v += x[base+index+i]*code[i];
		}
		return v;
	}
	
	@Override
	public SortedMap<String,Double> effects(final int base, final double[] x) {
		final SortedMap<String,Double> r = new TreeMap<String,Double>();
		for(final Entry<String, double[]> me: levelCodes.entrySet()) {
			final String level = me.getKey();
			final double[] code = me.getValue();
			final double v = dot(base,x,code);
			r.put(level,v);
		}
		return r;
	}
	
	@Override
	public SortedMap<String,Double> detailedEffects(final int base, final double[] x) {
		final SortedMap<String,Double> r = new TreeMap<String,Double>();
		for(final Entry<String, double[]> me: levelCodes.entrySet()) {
			final String level = me.getKey();
			final double[] code = me.getValue();
			final String[] names = levelNames.get(level);
			final double v = dot(base,x,code);
			r.put(level,v);
			for(int i=0;i<targetDim;++i) {
				r.put(level + "_" + names[i],dot1coord(base,x,code,i));
			}
		}
		return r;
	}

	
	@Override
	public double effect(final int base, final double[] x, final String level) {
		return dot(base,x,levelCodes.get(level));
	}
	
	@Override
	public double effectTest(final int base, final double[] x, final String level) {
		return effects(base,x).get(level);
	}


	@Override
	public String toString() {
		return "'" + origColumn + "'->[" + indexL() + "," + indexR() + "](vector)";
	}
	
	public double value(final String level, final int index) {
		return levelCodes.get(level)[index];
	}
}
