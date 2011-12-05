package com.winvector.logistic;

import java.io.Serializable;
import java.text.ParseException;
import java.util.SortedSet;
import java.util.TreeSet;

public final class Formula implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public final String f;
	public final String resultColumn;
	public final SortedSet<String> variables = new TreeSet<String>();
	public final SortedSet<String> forcedNumeric = new TreeSet<String>();
	public final SortedSet<String> forcedCategorical = new TreeSet<String>();
	
	public Formula(final String f) throws ParseException {
		this.f = f;
		final String[] sides = f.split("~");
		if(sides.length!=2) {
			throw new ParseException("not a well formed formula (needs exactly one ~): '" + f + "'", 0);
		}
		resultColumn = sides[0].trim();
		final String[] terms = sides[1].split("\\+");
		if(terms.length<=0) {
			throw new ParseException("not a well formed formula (needs terms): '" + f + "'", 0);
		}
		for(final String ti: terms) {
			final int hashIndex = ti.indexOf('#');
			final int caratIndex = ti.indexOf('^');
			if((hashIndex>=0)&&(caratIndex>=0)) {
				throw new ParseException("not a well formed formula (too many ^/#): '" + f + "'", 0);
			}
			if(hashIndex>=0) {
				final String name = ti.substring(hashIndex+1).trim();
				if(name.length()<=0) {
					throw new ParseException("not a well formed formula (empty name): '" + f + "'", 0);
				}
				forcedNumeric.add(name);
			} if(caratIndex>=0) {
				final String name = ti.substring(caratIndex+1).trim();
				if(name.length()<=0) {
					throw new ParseException("not a well formed formula (empty name): '" + f + "'", 0);
				}
				forcedCategorical.add(name);
			} else {
				final String name = ti.trim();
				if(name.length()<=0) {
					throw new ParseException("not a well formed formula (empty name): '" + f + "'", 0);
				}
				variables.add(name);
			}
		}
		variables.addAll(forcedCategorical);
		variables.addAll(forcedNumeric);
	}
	
	public SortedSet<String> allTerms() {
		final SortedSet<String> allTerms = new TreeSet<String>();
		allTerms.add(resultColumn);
		allTerms.addAll(variables);
		return allTerms;
	}
}
