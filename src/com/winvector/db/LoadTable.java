package com.winvector.db;

import java.io.File;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.db.DBUtil.DBHandle;
import com.winvector.util.BurstMap;
import com.winvector.util.RowCritique;
import com.winvector.util.TrivialReader;


public class LoadTable {
	public static void main(final String[] args) throws Exception {
		final Log log = LogFactory.getLog(LoadTable.class);
		final URI propsURI = new URI(args[0]);
		final char sep = args[1].charAt(0);
		final URI inURI = new URI(args[2]);
		final String tableName = args[3];
		
		log.info("start LoadTable\t" + new Date());
		log.info("\tcwd: " + (new File(".")).getAbsolutePath());
		log.info("\tDBProperties XML:\t" + propsURI.toString());
		log.info("\tsep: " + sep);
		log.info("\tSource URI:\t" + inURI);
		log.info("\ttableName:\t" + tableName);
		final DBHandle handle = DBUtil.buildConnection(propsURI,false);
		log.info("\tdb:\t" + handle);
		
		final Iterable<BurstMap> source = new TrivialReader(inURI,sep,null,null, false);
		loadTable(source, null, tableName, handle);
		handle.conn.close();
		
		log.info("done LoadTable\t" + new Date());
	}

	public static final Set<String> invalidColumnNames = new TreeSet<String>();
	public static final String columnPrefix = "x";
	static {
		final String[] keywords = {
				"ABS", "ABSOLUTE", "ACOS", "ACTION", "ADA", "ADD", "ADMIN",
				"AFTER", "AGGREGATE", "ALIAS", "ALL", "ALLOCATE", "ALTER", "AND",
				"ANY", "APP", "ARE", "ARRAY", "AS", "ASC", "ASIN", "ASSERTION", "AT",
				"ATAN", "ATAN2", "AUTHORIZATION", "AVG", "BACKUP", "BEFORE", "BEGIN",
				"BETWEEN", "BIGINT", "BINARY", "BIT", "BIT_LENGTH", "BLOB", "BOOLEAN",
				"BOTH", "BREADTH", "BREAK", "BROWSE", "BULK", "BY", "CALL", "CASCADE",
				"CASCADED", "CASE", "CAST", "CATALOG", "CEILING", "CHAR", "CHARACTER",
				"CHARACTER_LENGTH", "CHAR_LENGTH", "CHECK", "CHECKPOINT", "CLASS",
				"CLOB", "CLOSE", "CLUSTERED", "COALESCE", "COLLATE", "COLLATION",
				"COLUMN", "COMMIT", "COMPLETION", "COMPUTE", "CONCAT", "CONNECT",
				"CONNECTION", "CONSTRAINT", "CONSTRAINTS", "CONSTRUCTOR", "CONTAINS",
				"CONTAINSTABLE", "CONTINUE", "CONVERT", "COPY", "CORRESPONDING",
				"COS", "COT", "COUNT", "CREATE", "CROSS", "CUBE", "CURRENT",
				"CURRENT_DATE", "CURRENT_PATH", "CURRENT_ROLE", "CURRENT_TIME",
				"CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR", "CYCLE", "DATA",
				"DATABASE", "DATE", "DAY", "DB2J_DEBUG", "DBCC", "DEALLOCATE", "DEC",
				"DECIMAL", "DECLARE", "DEFAULT", "DEFERRABLE", "DEFERRED", "DEGREES",
				"DELETE", "DENY", "DEPTH", "DEREF", "DESC", "DESCRIBE", "DESCRIPTOR",
				"DESTROY", "DESTRUCTOR", "DETERMINISTIC", "DIAGNOSTICS", "DICTIONARY",
				"DISCONNECT", "DISK", "DISTINCT", "DISTRIBUTED", "DOMAIN", "DOUBLE",
				"DROP", "DUMMY", "DUMP", "DYNAMIC", "EACH", "ELSE", "END", "END-EXEC",
				"EQUALS", "ERRLVL", "ESCAPE", "EVERY", "EXCEPT", "EXCEPTION", "EXEC",
				"EXECUTE", "EXISTS", "EXIT", "EXP", "EXPLAIN", "EXTERNAL", "EXTRACT",
				"FALSE", "FETCH", "FILE", "FILLFACTOR", "FILTER", "FIRST", "FLOAT",
				"FLOOR", "FOR", "FOREIGN", "FORTRAN", "FOUND", "FREE", "FREETEXT",
				"FREETEXTTABLE", "FROM", "FULL", "FUNCTION", "GENERAL", "GET",
				"GETCURRENTCONNECTION", "GLOBAL", "GO", "GOTO", "GRANT", "GROUP",
				"GROUPING", "HAVING", "HOLDLOCK", "HOST", "HOUR", "IDENTITY",
				"IDENTITYCOL", "IDENTITY_INSERT", "IF", "IGNORE", "IMMEDIATE", "IN",
				"INCLUDE", "INDEX", "INDICATOR", "INITIALIZE", "INITIALLY", "INNER",
				"INOUT", "INPUT", "INSENSITIVE", "INSERT", "INSTANCEOF", "INT",
				"INTEGER", "INTERSECT", "INTERVAL", "INTO", "IS", "ISOLATION",
				"ITERATE", "JOIN", "KEY", "KILL", "LANGUAGE", "LARGE", "LAST",
				"LATERAL", "LCASE", "LEADING", "LEFT", "LENGTH", "LESS", /*"LEVEL",*/
				"LIKE", "LIMIT", "LINENO", "LOAD", "LOCAL", "LOCALTIME",
				"LOCALTIMESTAMP", "LOCATE", "LOCATOR", "LOG", "LOG10", "LONG",
				"LOWER", "LTRIM", "MAP", "MATCH", "MAX", "METHOD", "MIN", "MINUTE",
				"MOD", "MODIFIES", "MODIFY", "MODULE", "MONTH", "NAMES", "NATIONAL",
				"NATURAL", "NCHAR", "NCLOB", "NEW", "NEXT", "NO", "NOCHECK",
				"NONCLUSTERED", "NONE", "NOT", "NULL", "NULLID", "NULLIF", "NUMERIC",
				"OBJECT", "OCTET_LENGTH", "OF", "OFF", "OFFSETS", "OLD", "ON", "ONLY",
				"OPEN", "OPENDATASOURCE", "OPENQUERY", "OPENROWSET", "OPENXML",
				"OPERATION", "OPTION", "OR", "ORDER", "ORDINALITY", "OUT", "OUTER",
				"OUTPUT", "OVER", "OVERLAPS", "PAD", "PARAMETER", "PARAMETERS",
				"PARTIAL", "PASCAL", "PATH", "PERCENT", "PI", "PLAN", "POSITION",
				"POSTFIX", "PRECISION", "PREFIX", "PREORDER", "PREPARE", "PRESERVE",
				"PRIMARY", "PRINT", "PRIOR", "PRIVILEGES", "PROC", "PROCEDURE",
				"PROPERTIES", "PUBLIC", "RADIANS", "RAISERROR", "RAND", "READ",
				"READS", "READTEXT", "REAL", "RECOMPILE", "RECONFIGURE", "RECURSIVE",
				"REF", "REFERENCES", "REFERENCING", "RELATIVE", "RENAME",
				"REPLICATION", "RESTORE", "RESTRICT", "RESULT", "RETURN", "RETURNS",
				"REVOKE", "RIGHT", "ROLE", "ROLLBACK", "ROLLUP", "ROUTINE", "ROW",
				"ROWCOUNT", "ROWGUIDCOL", "ROWS", "RTRIM", "RULE",
				"RUNTIMESTATISTICS", "SAVE", "SAVEPOINT", "SCHEMA", "SCOPE", "SCROLL",
				"SEARCH", "SECOND", "SECTION", "SELECT", "SEQUENCE", "SESSION",
				"SESSION_USER", "SET", "SETS", "SETUSER", "SHUTDOWN", "SIGN", "SIN",
				"SIZE", "SMALLINT", "SOME", "SPACE", "SPECIFIC", "SPECIFICTYPE",
				"SQL", "SQLCA", "SQLCODE", "SQLERROR", "SQLEXCEPTION", "SQLJ",
				"SQLSTATE", "SQLWARNING", "SQRT", "START", "STATE", "STATEMENT",
				"STATIC", "STATISTICS", "STRUCTURE", "SUBSTRING", "SUM", "SYNONYM",
				"SYS", "SYSCAT", "SYSCS_DIAG", "SYSCS_UTIL", "SYSFUN", "SYSIBM",
				"SYSPROC", "SYSSTAT", "SYSTEM", "SYSTEM_USER", "TABLE", "TAN",
				"TEMPORARY", "TERMINATE", "TEXTSIZE", "THAN", "THEN", "TIME",
				"TIMESTAMP", "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TIMING", "TO",
				"TOP", "TRAILING", "TRAN", "TRANSACTION", "TRANSLATE", "TRANSLATION",
				"TREAT", "TRIGGER", "TRIM", "TRUE", "TRUNCATE", "TSEQUAL", "UCASE",
				"UNDER", "UNION", "UNIQUE", "UNKNOWN", "UNNEST", "UPDATE",
				"UPDATETEXT", "UPPER", "USAGE", "USE", "USER", "USING", /*"VALUE",*/
				"VALUES", "VARCHAR", /*"VARIABLE",*/ "VARYING", "VIEW", "WAIT", "WAITFOR",
				"WHEN", "WHENEVER", "WHERE", "WHILE", "WITH", "WITHOUT", "WORK",
				"WRITE", "WRITETEXT", "XML", "YEAR", "ZONE", ""
		};
		for(final String kw: keywords) {
			invalidColumnNames.add(kw.toLowerCase());
		}
	}
	
	private static String stompMarks(final String s) {
		return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+",""); // from: http://stackoverflow.com/questions/285228/how-to-convert-utf-8-to-us-ascii-in-java
	}
	
	public static String plumpColumnName(final String kin, final Set<String> seen) {
		String k = stompMarks(kin).replaceAll("\\W+"," ").trim().replaceAll("\\s+","_");
		if((k.length()<=0)||invalidColumnNames.contains(k.toLowerCase())||(!Character.isLetter(k.charAt(0)))) {
			k = columnPrefix + k;
		}
		if(seen.contains(k.toLowerCase())) {
			int i = 2;
			while(true) {
				String kt = k + "_" + i;
				if(!seen.contains(kt.toLowerCase())) {
					k = kt;
					break;
				} else {
					++i;
				}
			}
		}
		seen.add(k.toLowerCase());
		return k;
	}
	
	
	public static void loadTable(final Iterable<BurstMap> source, final RowCritique gateKeeper,
			final String tableName, final DBHandle handle) throws SQLException {
		final Log log = LogFactory.getLog(LoadTable.class);
		// scan once to get field names and sizes
		final ArrayList<String> keys = new ArrayList<String>();
		int[] sizes = null;
		for(final BurstMap row: source) {
			if((gateKeeper==null)||(gateKeeper.accept(row))) {
				if(keys.isEmpty()) {
					keys.addAll(row.keySet());
					sizes = new int[keys.size()];
					for(int i=0;i<sizes.length;++i) {
						sizes[i] = 1;
					}
				}
				int i = 0;
				for(final String k: keys) {
					sizes[i] = Math.max(sizes[i],row.getAsString(k).length()+1);
					++i;
				}
			}
		}
		// build SQL
		final String createStatement;
		final String insertStatement;
		{ 
			final Set<String> seenColNames = new HashSet<String>();
			final StringBuilder createBuilder = new StringBuilder();
			createBuilder.append("CREATE TABLE " + tableName + " (");
			final StringBuilder insertBuilder = new StringBuilder();
			insertBuilder.append("INSERT INTO " + tableName + " (");
			{
				int i = 0;
				for(final String k: keys) {
					if(i>0) {
						createBuilder.append(",");
						insertBuilder.append(",");
					}
					final String colName = plumpColumnName(k,seenColNames);
					createBuilder.append(" " + colName + " VARCHAR(" + sizes[i] + ")");
					insertBuilder.append(" " + colName);
					++i;
				}
			}
			createBuilder.append(" )");
			insertBuilder.append(" ) VALUES (");
			for(int i=0;i<sizes.length;++i) {
				if(i>0) {
					insertBuilder.append(",");
				}
				insertBuilder.append(" ?");
			}
			insertBuilder.append(" )");			
			createStatement = createBuilder.toString();
			insertStatement = insertBuilder.toString();
		}
		// set up table
		{
			final Statement stmt = handle.conn.createStatement();
			try {
				stmt.executeUpdate("DROP TABLE " + tableName);
			} catch (Exception ex) {
			}
			log.info("\texecuting: " + createStatement);
			stmt.executeUpdate(createStatement);
			stmt.close();			
		}
		{ // scan again and populate
			log.info("\texecuting: " + insertStatement);
			final PreparedStatement stmtA = handle.conn.prepareStatement(insertStatement);
			long reportTarget = 100;
			long nInserted = 0;
			for(final BurstMap row: source) {
				if((gateKeeper==null)||(gateKeeper.accept(row))) {
					int i = 0;
					for(final String k: keys) {
						stmtA.setString(i+1,row.getAsString(k));
						++i;
					}
					stmtA.executeUpdate();
					++nInserted;
					if(nInserted>=reportTarget) {
						log.info("\twrote " + nInserted + "\t" + new Date());
						reportTarget *= 2;
					}
				}
			}
			stmtA.close();
		}
	}
}
