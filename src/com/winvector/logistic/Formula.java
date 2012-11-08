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
	public final boolean useIntercept;
	
	public Formula(final String f) throws ParseException {
		this.f = f;
		final String[] sides = f.split("~");
		if(sides.length!=2) {
			throw new ParseException("not a well formed formula (needs exactly one ~): '" + f + "'", 0);
		}
		resultColumn = sides[0].trim();
		final String[] terms = sides[1].split("\\+");
		boolean willUseIntercept = true;
		for(final String termi: terms) {
			final String ti = termi.trim();
			if(ti.length()<=0) {
				throw new ParseException("not a well-formed expression (doubled plus) '" + f + "'", 0);
			}
			final char c0 = ti.charAt(0);
			final boolean hasHash = c0=='#';
			final boolean hasCarat = c0=='^';
			if(ti.equals("0")) {
				willUseIntercept = false;
			} else if(hasHash) {
				final String name = ti.substring(1).trim();
				if(name.length()<=0) {
					throw new ParseException("not a well formed formula (empty name): '" + f + "'", 0);
				}
				forcedNumeric.add(name);
			} if(hasCarat) {
				final String name = ti.substring(1).trim();
				if(name.length()<=0) {
					throw new ParseException("not a well formed formula (empty name): '" + f + "'", 0);
				}
				forcedCategorical.add(name);
			} else {
				final String name = ti.trim();
				if(name.length()>0) {
					variables.add(name);
				}
			}
		}
		useIntercept = willUseIntercept;
		variables.addAll(forcedCategorical);
		variables.addAll(forcedNumeric);
	}
	
	/**
	 * skip parsing version of the formula, user code alters the variable sets after construction
	 * @param f
	 */
	public Formula(final String fComment, final String resultColumn, final boolean useIntercept) {
		this.f = fComment;
		this.resultColumn = resultColumn;
		this.useIntercept = useIntercept;
	}
	
	public SortedSet<String> allTerms() {
		final SortedSet<String> allTerms = new TreeSet<String>();
		allTerms.add(resultColumn);
		allTerms.addAll(variables);
		return allTerms;
	}
	
	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("formula: " + f + "\n");
		b.append("\tresult:\t" + resultColumn + "\n");
		b.append("\tvars:");
		for(final String vi: variables) {
			b.append("\t" + vi);
			if(forcedNumeric.contains(vi)) {
				b.append(" (forced numeric)");
			}
			if(forcedCategorical.contains(vi)) {
				b.append(" (forced categorical)");
			}
		}
		b.append("\n");
		return b.toString();
	}
}
