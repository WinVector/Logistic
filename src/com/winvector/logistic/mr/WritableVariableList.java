package com.winvector.logistic.mr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.hadoop.io.Writable;

import com.winvector.util.CountMap;
import com.winvector.util.StatMap;
import com.winvector.util.StatMap.SimpleStat;
import com.winvector.variables.PrimaVariableInfo;

public final class WritableVariableList extends PrimaVariableInfo implements Writable {
	private static final long serialVersionUID = 1L;
	

	private static void writeSet(final Set<String> s, final DataOutput dout) throws IOException {
		dout.writeInt(s.size());
		for(final String ki: s) {
			dout.writeUTF(ki);
		}
	}
	
	private static SortedSet<String> readSet(final DataInput din) throws IOException {
		final SortedSet<String> r = new TreeSet<String>();
		final int n = din.readInt();
		for(int i=0;i<n;++i) {
			r.add(din.readUTF());
		}
		return r;
	}
	
	private static void writeCountMap(final CountMap<String> s, final DataOutput dout) throws IOException {
		final SortedSet<String> keys = new TreeSet<String>(s.keySet());
		dout.writeInt(keys.size());
		for(final String ki: keys) {
			dout.writeUTF(ki);
			dout.writeDouble(s.get(ki));
		}
	}
	
	private static CountMap<String> readCountMap(final DataInput din) throws IOException {
		final CountMap<String> r = new CountMap<String>(CountMap.strCmp);
		final int n = din.readInt();
		for(int i=0;i<n;++i) {
			final String k = din.readUTF();
			final double v = din.readDouble();
			r.observe(k,v);
		}
		return r;
	}

	private static void writeStatMap(final StatMap s, final DataOutput dout) throws IOException {
		final SortedSet<String> keys = new TreeSet<String>(s.keySet());
		dout.writeInt(keys.size());
		for(final String ki: keys) {
			final SimpleStat vi = s.get(ki);
			dout.writeUTF(ki);
			dout.writeDouble(vi.sumW);
			dout.writeDouble(vi.sumWX);
		}
	}
	
	private static StatMap readStatMap(final DataInput din) throws IOException {
		final StatMap r = new StatMap();
		final int n = din.readInt();
		final SimpleStat v = new SimpleStat();
		for(int i=0;i<n;++i) {
			final String k = din.readUTF();
			v.sumW = din.readDouble();
			v.sumWX = din.readDouble();
			r.observe(k,v);
		}
		return r;
	}
	
	@Override
	public void readFields(final DataInput din) throws IOException {
		resultColumn = null;
		variables = null;
		forcedNumeric = null;
		forcedCategorical = null;
		outcomes = null;
		numericColumnSet = null;
		catLevels = new TreeMap<String,CountMap<String>>();
		resultColumn = din.readUTF();
		variables = readSet(din);
		forcedNumeric = readSet(din);
		forcedCategorical = readSet(din);
		outcomes = readCountMap(din);
		numericColumnSet = readStatMap(din);
		final int nc = din.readInt();
		for(int i=0;i<nc;++i) {
			final String key = din.readUTF();
			final CountMap<String> values = readCountMap(din); 
			catLevels.put(key,values);
		}
	}

	@Override
	public void write(final DataOutput dout) throws IOException {
		dout.writeUTF(resultColumn);
		writeSet(variables,dout);
		writeSet(forcedNumeric,dout);
		writeSet(forcedCategorical,dout);
		writeCountMap(outcomes,dout);
		writeStatMap(numericColumnSet,dout);
		dout.writeInt(catLevels.size());
		for(final Map.Entry<String, CountMap<String>> me: catLevels.entrySet()) {
			dout.writeUTF(me.getKey());
			writeCountMap(me.getValue(),dout);
		}
	}
	
	public static WritableVariableList copy(final PrimaVariableInfo o) {
		final WritableVariableList r = new WritableVariableList();
		r.resultColumn = o.resultColumn;
		r.variables = new TreeSet<String>(o.variables);
		r.forcedNumeric = new TreeSet<String>(o.forcedNumeric);
		r.forcedCategorical = new TreeSet<String>(o.forcedCategorical);
		r.readyForDefTracking();
		r.mergeVariableDefs(o);
		r.mergeVariableLevels(o);
		return r;
	}
	
	@Override
	public String toString() {
		try {
			return WritableUtils.writableToString(this);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static WritableVariableList fromString(final String s) throws IOException {
		final WritableVariableList r = new WritableVariableList();
		WritableUtils.readWritableFieldsFromString(s, r);
		return r;
	}
}
