package com.winvector.db;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class DBUtil {
	public static final String DRIVERKEY = "driver";
	public static final String URLKEY = "url";
	public static final String PASSWORDKEY = "password";
	public static final String USERKEY = "user";

	public static final class DBHandle {
		public final String comment;
		public final String dbUserName;
		public final String dbURL;
		public final Connection conn;
		
		public DBHandle(final String comment, final String dbURL, final String dbUserName, final Connection conn) { 
			this.comment = comment;
			this.dbUserName = dbUserName;
			this.dbURL = dbURL;
			this.conn = conn;
		}
		
		public String toString() {
			return "dbHandle( " + dbURL +" , " + dbUserName + " , " + comment + " )";
		}
		
		public Statement createReadStatement() throws SQLException {
			Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
			//stmt.setFetchSize(Integer.MIN_VALUE);  // from: http://benjchristensen.com/2008/05/27/mysql-jdbc-memory-usage-on-large-resultset/ prevent pre-fetch (runs out of memory)
			stmt.setFetchSize(0);  // from: http://benjchristensen.com/2008/05/27/mysql-jdbc-memory-usage-on-large-resultset/ prevent pre-fetch (runs out of memory)
			return stmt;
		}
	}
	
	public static DBHandle buildConnection(final String comment,
			final String dbUserName,
			final String dbPassword,
			final String dbURL,
			final String driver,
			final boolean readOnly) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		if(driver!=null) {
			Class.forName (driver).newInstance(); // force driver in		
		}
		final Connection conn = DriverManager.getConnection(dbURL, dbUserName, dbPassword);
		final DBHandle dbHandle = new DBHandle(comment,dbURL,dbUserName,conn);
		if(readOnly) {
			try {
				dbHandle.conn.setReadOnly(true);
			} catch (Exception ex) {
				System.out.println("caught: " + ex);
			}
		}
		return dbHandle;				
	}
	
	public static DBHandle buildConnection(final String comment, final Properties props, final boolean readOnly) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		final String dbUserName = props.getProperty(USERKEY);
		final String dbPassword = props.getProperty(PASSWORDKEY);
		final String dbURL = props.getProperty(URLKEY);
		final String driver = props.getProperty(DRIVERKEY); // ex: com.mysql.jdbc.Driver or org.apache.derby.jdbc.EmbeddedDriver
		return buildConnection(comment,
				dbUserName,
				dbPassword,
				dbURL,
				driver,
				readOnly) ;
	}
	
	/**
	example:
	<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
	<properties>
	 <comment>testdb</comment>
	 <entry key="user">miner_demo</entry>
	 <entry key="url">jdbc:postgresql://localhost:5432/miner_demo</entry>
	 <entry key="password">miner_demo</entry>
	 <entry key="driver">org.postgresql.Driver</entry>
	</properties>
	**/
	public static DBHandle buildConnection(final URI propsURI, final boolean readOnly) throws IOException {
		try {
			final InputStream is = propsURI.toURL().openStream();
			final Properties props = new Properties();
			props.loadFromXML(is);
			is.close();
			return buildConnection(propsURI.toString(),props,readOnly);
		} catch (Exception ex) {
			throw new IOException("problem with resource: " + propsURI.toString()
					+ "\tcaught: " + ex);
		}
	}
}
