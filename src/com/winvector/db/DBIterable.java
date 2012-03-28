package com.winvector.db;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import com.winvector.db.DBUtil.DBHandle;
import com.winvector.util.BurstMap;
import com.winvector.util.HBurster;


public final class DBIterable implements Iterable<BurstMap> {
	private Statement stmt;
	private final String query;
	
	private static final Comparator<String> ignoreCase = new Comparator<String>() {
		@Override
		public int compare(final String arg0, final String arg1) {
			return arg0.compareToIgnoreCase(arg1);
		}
	};
	
	public DBIterable(final Statement stmt, final String query) {
		this.stmt = stmt;
		this.query = query;
	}

	public static final class RSIterator implements Iterator<BurstMap> {
		private BurstMap next = null;
		private ResultSet rs;
		private final String[] colNames;
		private final int[] colTypes;
		private final Map<String,String> colNameToJavaClassName = new HashMap<String,String>();
		
		public RSIterator(final ResultSet rs) throws SQLException {
			this.rs = rs;
			if(rs.next()) {
				final ResultSetMetaData meta = rs.getMetaData();
				final int n = meta.getColumnCount();
				final String[] origColNames = new String[n];
				colTypes = new int[n];
				final String[] javaClassNames = new String[n];
				for(int i=0;i<n;++i) {
					// could also prepend (when appropriate) meta.getTableName(i+1);
					//origColNames[i] = meta.getColumnName(i+1);
					origColNames[i] = meta.getColumnLabel(i+1);
					colTypes[i] = meta.getColumnType(i+1);
					javaClassNames[i] = meta.getColumnClassName(i+1);
				}
				colNames = HBurster.buildHeaderFlds(origColNames);
				for(int i=0;i<n;++i) {
					colNameToJavaClassName.put(colNames[i],javaClassNames[i]);
				}
			} else {
				rs.close();
				this.rs = null;
				colNames = null;
				colTypes = null;
			}
			advance();
		}

		public String getJavaClassName(final String colName) {
			return colNameToJavaClassName.get(colName);
		}
		
		private void advance() {
			next = null;
			if(rs!=null) {
				try {
					int n = colNames.length;
					final Map<String,Object> mp = new TreeMap<String,Object>(ignoreCase);
					for(int i=0;i<n;++i) {
						switch(colTypes[i]) {
						case java.sql.Types.DATE:
							mp.put(colNames[i],rs.getDate(i+1));
							break;
						case java.sql.Types.BIGINT:
							mp.put(colNames[i],rs.getLong(i+1));
							break;
						case java.sql.Types.DOUBLE:
							mp.put(colNames[i],rs.getDouble(i+1));
							break;
						case java.sql.Types.FLOAT:
							mp.put(colNames[i],rs.getFloat(i+1));
							break;
						case java.sql.Types.INTEGER:
							mp.put(colNames[i],rs.getInt(i+1));
							break;
						case java.sql.Types.SMALLINT:
							mp.put(colNames[i],rs.getShort(i+1));
							break;
						case java.sql.Types.NUMERIC:
							mp.put(colNames[i],rs.getDouble(i+1));
							break;
						default:
							mp.put(colNames[i],rs.getString(i+1));
							break;
						}
					}
					next = new BurstMap("db",mp); 
					if(!rs.next()) {
						rs = null;
					}
				} catch (SQLException ex) {
					if(rs!=null) {
						try {
							rs.close();
						} catch (SQLException cx) {
						}
					}
					rs = null;
					next = null;
					throw new RuntimeException(ex);
				}
			}
		}

		@Override
		public boolean hasNext() {
			return next!=null;
		}

		@Override
		public BurstMap next() {
			if(!hasNext()) {
				throw new NoSuchElementException("RSIterator");
			}
			final BurstMap ret = next;
			advance();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("RSterator");
		}

	}

	@Override
	public Iterator<BurstMap> iterator() {
		try {
			final ResultSet rs = stmt.executeQuery(query);
			return new RSIterator(rs);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public String toString() {
		return query;
	}

	public static Iterable<BurstMap> buildSource(final DBHandle handle,
			final Statement stmt, final String dbTable, final Iterable<String> terms) {
		final StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		{
			boolean first = true;
			for(final String term: terms) {
				if(first) {
					first = false;
				} else {
					query.append(",");
				}
				query.append(term);
			}
		}
		query.append(" FROM ");
		query.append(dbTable);
		return new DBIterable(stmt,query.toString());
	}
}

