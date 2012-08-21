package com.winvector.variables;

import java.io.Serializable;
import java.util.SortedMap;

import com.winvector.util.BurstMap;

public interface VariableMapping extends Serializable {
	
	/**
	 * 
	 * @return name of original column
	 */
	String origColumn();
	
	/**
	 * 
	 * @return left index (inclusive) of interval encoding to
	 */
	int indexL();
	
	
	/**
	 * 
	 * @return right index (exclusive) of interval encoding to
	 */
	int indexR();


	/**
	 * 
	 * @return true if want regularization (all but const terms should set this)
	 */
	boolean wantRegularization();

	/**
	 * Take data from row and paste into vec
	 * @param row
	 * @param vec
	 */
	void process(BurstMap row, double[] vec);

	/**
	 * type of adaption
	 * @return
	 */
	String name();

	/**
	 * 
	 * @param base how many indices to skip (use to get per-outcome data, so base will be a multiple of encoding dimension)
	 * @param x vector soln.
	 * @return effect this variable has at each level ("" for unique level) 
	 */
	SortedMap<String,Double> effects(int base, double[] x);
	
	/**
	 * 
	 * @param base how many indices to skip (use to get per-outcome data, so base will be a multiple of encoding dimension)
	 * @param x vector soln
	 * @return map similar to effects, possibly with additional entries for pseudo-levels (to carry more detail)
	 */
	SortedMap<String, Double> detailedEffects(int base, double[] x);
	
	/**
	 * for test/debugging, allowed to be slow
	 * 
	 * @param base how many indices to skip (use to get per-outcome data, so base will be a multiple of encoding dimension)
	 * @param x vector soln.
	 * @param level level we are interested (value seen in datum)
	 * @return effect for given level (or effect if effect is not a function of levels)
	 */
	double effectTest(final int base, final double[] x, final String level);

	/**
	 * 
	 * @param base how many indices to skip (use to get per-outcome data, so base will be a multiple of encoding dimension)
	 * @param x vector soln.
	 * @param level level we are interested (value seen in datum)
	 * @return effect for given level (or effect if effect is not a function of levels)
	 */
	double effect(final int base, final double[] x, final String level);
}
