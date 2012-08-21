package com.winvector.variables;

import java.io.Serializable;
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
	private final Map<String,VectorRow> levelCodes; // level to vector of codes
	private final int targetDim;

	public static final class VectorRow implements Serializable {
		private static final long serialVersionUID = 1L;
		public final String[] names;
		public final double[] levelEncodings;
		public final int[] warmStartOutcome;
		
		public VectorRow(final String[] name, final double[] levelEncodings, final int[] warmStartOutcome) {
			this.names = name;
			this.levelEncodings = levelEncodings;
			this.warmStartOutcome = warmStartOutcome;
		}
	}
	
	public LevelVectors(final String origColumn, final int index, 
			final Map<String,VectorRow> levelCodes) { 
		this.origColumn = origColumn;
		this.index = index;
		this.levelCodes = levelCodes;
		if(!levelCodes.isEmpty()) {
			targetDim = levelCodes.values().iterator().next().levelEncodings.length;
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
	public boolean wantRegularization() {
		return false;
	}

	@Override
	public void process(final BurstMap row, final double[] vec) {
		final String level = row.getAsString(origColumn);
		if(level!=null) {
			final VectorRow code = levelCodes.get(level);
			if(code!=null) {
				for(int i=0;i<targetDim;++i) {
					vec[index+i] = code.levelEncodings[i];
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
		for(final Entry<String, VectorRow> me: levelCodes.entrySet()) {
			final String level = me.getKey();
			final VectorRow code = me.getValue();
			final double v = dot(base,x,code.levelEncodings);
			r.put(level,v);
		}
		return r;
	}
	
	@Override
	public SortedMap<String,Double> detailedEffects(final int base, final double[] x) {
		final SortedMap<String,Double> r = new TreeMap<String,Double>();
		for(final Entry<String, VectorRow> me: levelCodes.entrySet()) {
			final String level = me.getKey();
			final VectorRow code = me.getValue();
			final String[] names = code.names;
			final double v = dot(base,x,code.levelEncodings);
			r.put(level + "_effect",v);
			for(int i=0;i<targetDim;++i) {
				r.put(level + "_" + names[i],dot1coord(base,x,code.levelEncodings,i));
			}
		}
		return r;
	}

	
	@Override
	public double effect(final int base, final double[] x, final String level) {
		final VectorRow vectorRow = levelCodes.get(level);
		if(null!=vectorRow) {
			return dot(base,x,vectorRow.levelEncodings);
		} else {
			return 0.0;
		}
	}
	
	@Override
	public double effectTest(final int base, final double[] x, final String level) {
		return effects(base,x).get(level);
	}


	@Override
	public String toString() {
		return "'" + origColumn + "'->[" + indexL() + "," + indexR() + "](vector)";
	}
}
