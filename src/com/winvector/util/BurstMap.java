package com.winvector.util;

import java.util.Map;
import java.util.Set;

public final class BurstMap {
	public final String origString;
	private final Map<String,Object> burst;
	
	public BurstMap(final String origString, final Map<String,Object> burst) {
		this.origString = origString;
		this.burst = burst;
	}
	
	public boolean isEmpty() {
		return burst.isEmpty();
	}
	
	public Set<String> keySet() {
		return burst.keySet();
	}

	public String getAsString(final String key) {
		final Object v = burst.get(key);
		if(v==null) {
			return null;
		}
		if(v instanceof String) {
			return (String)v;
		}
		return v.toString();
	}
	
	public Double getAsDouble(final String key) {
		final Object v = burst.get(key);
		if(v==null) {
			return null;
		}
		if(v instanceof Number) {
			return ((Number)v).doubleValue();
		}
		try {
			return Double.parseDouble(v.toString());
		} catch (Exception ex) {
			return null;
		}
	}
	
	public Long getAsLong(final String key) {
		final Object v = burst.get(key);
		if(v==null) {
			return null;
		}
		if(v instanceof Number) {
			return ((Number)v).longValue();
		}
		try {
			return Long.parseLong(v.toString());
		} catch (Exception ex) {
			return null;
		}
	}
	
	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("\"");
		b.append(origString);
		b.append("\"\t->");
		for(final Map.Entry<String,Object> me: burst.entrySet()) {
			final String key = me.getKey();
			final Object value = me.getValue();
			if(value!=null) {
				final String typeStr = value.getClass().getName();
				b.append("\t" + key + "=" + typeStr + ":" + value);
			} else {
				b.append("\t" + key + "=" + value);
			}
		}
		return b.toString();
	}
}
