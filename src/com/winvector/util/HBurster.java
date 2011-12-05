package com.winvector.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads lines given a header hint. Skips lines that look like the header.
 * Interns all strings (for lifetime of burster)
 * @author johnmount
 *
 */
public final class HBurster implements LineBurster {
	private static final long serialVersionUID = 1L;
	
	private final Map<String,String> interner;
	private final String sep;
	private final String origHeader;
	private final String[] headerFlds;
	
	public HBurster(final String sep, final String origHeader,  final boolean intern) {
		this.sep = sep;
		this.origHeader = origHeader;
		if(intern) {
			interner = new HashMap<String,String>();
		} else {
			interner = null;
		}
		headerFlds = buildHeaderFlds(origHeader.split(sep),interner);
	}
	
	private static String intern(final String s,  final Map<String,String> interner) {
		if(interner==null) {
			return s;
		}
		String got = interner.get(s);
		if(got==null) {
			// break any references
			StringBuilder b = new StringBuilder();
			b.append(s);
			got = b.toString();
			interner.put(got,got);
		}
		return got;
	}
	
	public static String[] buildHeaderFlds(final String[] rawHeader,  final Map<String,String> interner) {
		final Set<String> seen = new TreeSet<String>();
		final String[] headerFlds = new String[rawHeader.length];
		// make sure header fields are unambiguous (even ignoring case)
		for(int i=0;i<rawHeader.length;++i) {
			int tryNum = 1;
			String candidate = rawHeader[i];
			while(seen.contains(candidate.toLowerCase())) {
				++tryNum;
				candidate = rawHeader[i]  + "_" + tryNum;
			}
			seen.add(candidate.toLowerCase());
			headerFlds[i] = intern(candidate,interner);
		}
		return headerFlds;
	}
	
	public static String[] buildHeaderFlds(final String[] rawHeader) {
		return buildHeaderFlds(rawHeader,null);
	}
	
	@Override
	public BurstMap parse(final String s) {
		final Map<String,Object> mp = new LinkedHashMap<String,Object>();
		if((s!=null)&&(!s.equalsIgnoreCase(origHeader))) {
			final String[] flds = s.split(sep);
			final int n = Math.min(headerFlds.length,flds.length);
			for(int i=0;i<n;++i) {
				mp.put(headerFlds[i],intern(flds[i],interner));
			}
		}
		return new BurstMap(s,mp);
	}

	@Override
	public boolean haveAllFields(final BurstMap next) {
		if(next==null) {
			return false;
		}
		final Set<String> keys = next.keySet();
		if((keys==null)||(keys.isEmpty())) {
			return false;
		}
		for(final String k: headerFlds) {
			if(!keys.contains(k)) {
				return false;
			}
		}
		return true;
	}

}
