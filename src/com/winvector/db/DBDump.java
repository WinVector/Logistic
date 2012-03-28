package com.winvector.db;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import com.winvector.db.DBIterable.RSIterator;
import com.winvector.db.DBUtil.DBHandle;
import com.winvector.util.BurstMap;
import com.winvector.util.TrivialReader;


public class DBDump {
	
	public static long runQuery(final String query, final PrintStream p, final DBHandle handle) throws SQLException {
		final Statement stmt = handle.createReadStatement();
		final ResultSet rs = stmt.executeQuery(query);
		final RSIterator source = new RSIterator(rs);
		boolean first = true;
		final String sep = "\t";
		long rowNum = 0;
		while(source.hasNext()) {
			final BurstMap row = source.next();
			if(first) {
				boolean firstCol = true;
				for(final String ki: row.keySet()) {
					if(firstCol) {
						firstCol = false;
					} else {
						p.print(sep);
					}
					p.print(TrivialReader.safeStr(ki) + ":" + source.getJavaClassName(ki));
				}
				p.println();
				first = false;
			}
			boolean firstCol = true;
			for(final String ki: row.keySet()) {
				if(firstCol) {
					firstCol = false;
				} else {
					p.print(sep);
				}
				final String vi = row.getAsString(ki);
				p.print(TrivialReader.safeStr(vi));
			}
			p.println();
			++rowNum;
			// System.out.println(row);
		}
		stmt.close();
		return rowNum;
	}

	public static void main(final String[] args) throws Exception {
		final URI propsURI = new URI(args[0]);
		final String query = args[1];
		final File resFile = new File(args[2]);
		
		System.out.println("start DBDump\t" + new Date());
		System.out.println("\tDBProperties XML:\t" + propsURI.toString());
		System.out.println("\tquery:\t" + query);
		System.out.println("\tresultFile:\t" + resFile.getAbsolutePath());
		final DBHandle handle = DBUtil.buildConnection(propsURI,true);
		System.out.println("\tdb:\t" + handle);
		final PrintStream p = new PrintStream(new FileOutputStream(resFile));
		
		final long nRows = runQuery(query,p,handle);
		
		p.close();
		handle.conn.close();
		
		System.out.println("done DBDump, wrote\t" + nRows + " rows\t" + new Date());
	}

}
