package com.winvector.variables;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.winvector.logistic.Formula;
import com.winvector.util.BurstMap;
import com.winvector.util.CountMap;
import com.winvector.util.StatMap;

public class PrimaVariableInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	
	// definitional
	public String resultColumn;
	public SortedSet<String> variables = new TreeSet<String>();
	public SortedSet<String> forcedNumeric = new TreeSet<String>();
	public SortedSet<String> forcedCategorical = new TreeSet<String>();
	// calculated
	public CountMap<String> outcomes = new CountMap<String>(CountMap.strCmp);
	public StatMap numericColumnSet = new StatMap();
	public SortedMap<String,CountMap<String>> catLevels = new TreeMap<String,CountMap<String>>();

	protected void readyForDefTracking() {
		outcomes = new CountMap<String>(CountMap.strCmp);
		numericColumnSet = new StatMap();
		catLevels = new TreeMap<String,CountMap<String>>();
		for(final String nc: forcedNumeric) {
			numericColumnSet.observe(nc,0.0,0.0);
		}
		for(final String key: forcedCategorical) {
			catLevels.put(key,new CountMap<String>(CountMap.strCmp));
		}		
	}
	
	public void readyForDefTracking(final Formula formula) {
		resultColumn = formula.resultColumn;
		variables = new TreeSet<String>(formula.variables);
		forcedNumeric = new TreeSet<String>(formula.forcedNumeric);
		forcedCategorical = new TreeSet<String>(formula.forcedCategorical);
		readyForDefTracking();
	}

	/**
	 * confirm row has complete set of non-empty independent variables
	 * @param row
	 * @return
	 */
	public boolean completeSetOfVars(final BurstMap row) {
		for(final String key: variables) {
			final String sValue = row.getAsString(key);
			if((sValue==null)||(sValue.trim().length()<=0)) {
				return false;
			}
		}
		return true;
	}
		
	/**
	 * track: sets of levels, mean of numeric variable and distribution of outcomes
	 * @param row
	 */
	public void trackVariableDefsFromRow(final BurstMap row) {
		if(!completeSetOfVars(row)) {
			return;
		}
		for(final String key: variables) {
			if(catLevels.containsKey(key)||forcedNumeric.contains(key)) {
				continue;
			}
			final String sValue = row.getAsString(key);
			if((sValue!=null)&&(sValue.trim().length()>0)) {
				final double nValue = row.getAsDouble(key);
				if((!Double.isInfinite(nValue))&&(!Double.isNaN(nValue))) {
					if (!numericColumnSet.contains(key)) {
						numericColumnSet.observe(key,nValue,1.0);
					}
				} else {
					numericColumnSet.remove(key);
					catLevels.put(key,new CountMap<String>(CountMap.strCmp));
				}
			}
		}
		final String resultValue = row.getAsString(resultColumn);
		if((resultValue!=null)&&(resultValue.length()>0)&&(!outcomes.contains(resultValue))) {
			outcomes.observe(resultValue,1.0);
		}
	}
	
	public void mergeVariableDefs(final PrimaVariableInfo o) {
		for(final String key: o.catLevels.keySet()) {
			if(!catLevels.containsKey(key)) {
				if(numericColumnSet.contains(key)) {
					numericColumnSet.remove(key);
				}
				catLevels.put(key,new CountMap<String>(CountMap.strCmp));
			}
		}
		for(final String key: o.numericColumnSet.keySet()) {
			if((!catLevels.containsKey(key))&&(!numericColumnSet.contains(key))) {
				numericColumnSet.observe(key,o.numericColumnSet.get(key));
			}
		}
		outcomes.observe(o.outcomes);
	}
	
	
	
	public void trackVariableLevelsFromRow(final BurstMap row) {
		if(!completeSetOfVars(row)) {
			return;
		}
		for(final Map.Entry<String,CountMap<String>> me: catLevels.entrySet()) {
			final String key = me.getKey();
			final CountMap<String> levels = me.getValue();
			final String value = row.getAsString(key);
			if((value!=null)&&(value.length()>0)) {
				levels.observe(value,1.0);
			}
		}
	}
	
	/**
	 * sets should have identical category key set at this point (not levels)
	 * @param o
	 */
	public void mergeVariableLevels(final PrimaVariableInfo o) {
		for(final Map.Entry<String,CountMap<String>> me: catLevels.entrySet()) {
			final String key = me.getKey();
			final CountMap<String> values = me.getValue();
			final CountMap<String> ovalues = o.catLevels.get(key);
			if(ovalues!=null) {
				values.observe(ovalues);
			}
		}
	}
	
	public void trimStuckLevels() {
		 // remove levels that are not varying
		final SortedSet<String> victims = new TreeSet<String>();
		for(final Map.Entry<String,CountMap<String>> me: catLevels.entrySet()) {
			if(me.getValue().keySet().size()<=1) {
				victims.add(me.getKey());
			}
		}
		for(final String key: victims) {
			catLevels.remove(key);
		}
	}
	
	private static String formatC(final String name, final String what, final Collection<String> levels) {
		final String sep = "\t";
		final StringBuilder b = new StringBuilder();
		b.append("variable" + sep + name + sep + "is" + sep + what + sep + "with " + levels.size() + " levels:");
		final int maxPrint = 10;
		int nPrint = 0;
		for(final String li: new TreeSet<String>(levels)) {
			if(nPrint>=maxPrint) {
				b.append(" ...");
				break;
			}
			b.append(sep + li);
			++nPrint;
		}
		return b.toString();
	}
	
	public String formatState() {
		final String sep = "\t";
		final StringBuilder b = new StringBuilder();
		b.append(formatC(resultColumn,"outcome",outcomes.keySet()) + "\n");
		for(final Map.Entry<String,CountMap<String>> me: catLevels.entrySet()) {
			final String key = me.getKey();
			final CountMap<String> value = me.getValue();
			b.append(formatC(key,"categoric variable",value.keySet()) + "\n");
		}
		for(final String nc: numericColumnSet.keySet()) {
			b.append("variable" + sep + nc + sep + "is" + sep + "numeric variable" + "\n");
		}
		return b.toString();
	}
}
