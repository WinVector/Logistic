package com.winvector.variables;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import com.winvector.util.BurstMap;

/**
 * The standard way to encode a categorical variable- build one indicator function per value (we don't supress one level).
 * @author johnmount
 *
 */
public final class LevelIndicators implements VariableMapping {
	private static final long serialVersionUID = 1L;
	
	private final String origColumn;
	private final int index;
	private final Map<String,Integer> levelCodes = new HashMap<String,Integer>();
	
	public LevelIndicators(final String origColumn, final int index, final Collection<String> levels) {
		this.origColumn = origColumn;
		this.index = index;
		for(final String li: new TreeSet<String>(levels)) {
			levelCodes.put(li,levelCodes.size());
		}
	}
	
	public Set<String> levels() {
		return levelCodes.keySet();
	}
	
	public int index(final String level) {
		return index + levelCodes.get(level);
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
		return index + levelCodes.size();
	}
	
	@Override
	public boolean wantRegularization() {
		return false;
	}

	@Override
	public void process(final BurstMap row, final double[] vec) {
		final String level = row.getAsString(origColumn);
		if(level!=null) {
			final Integer code = levelCodes.get(level);
			if(code!=null) {
				vec[index+code] = 1.0;	
			}
		}
	}

	@Override
	public String name() {
		return "CategoricalLevels";
	}
	
	@Override
	public SortedMap<String,Double> effects(final int base, final double[] x) {
		final SortedMap<String,Double> r = new TreeMap<String,Double>();
		for(final Entry<String, Integer> me: levelCodes.entrySet()) {
			r.put(me.getKey(),x[base+index+me.getValue()]);
		}
		return r;
	}
	
	@Override
	public SortedMap<String,Double> detailedEffects(final int base, final double[] x) {
		return effects(base,x);
	}
	
	@Override
	public double effect(final int base, final double[] x, final String level) {
		return x[base+index+levelCodes.get(level)];
	}
	
	@Override
	public double effectTest(final int base, final double[] x, final String level) {
		return effects(base,x).get(level);
	}

	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("'" + origColumn + "'->[" + indexL() + "," + indexR() + "](");
		boolean first = true;
		for(final String li: new TreeSet<String>(levelCodes.keySet())) {
			if(first) {
				first = false;
			} else {
				b.append(",");
			}
			b.append(li);
		}
		b.append(")");
		return b.toString();
	}
}
