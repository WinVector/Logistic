package com.winvector.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;



/**
 * This reader assumes no quoting issues and that the data is on a single line.
 * Suppresses (with warning) mal-formed lines.
 * @author johnmount
 *
 */
public class TrivialReader implements Iterable<BurstMap> {
	public final URI srcURI;
	public final String encoding;
	public final ErrorPolicy<String,String[]> errorPolicy;
	private final boolean intern;
	private String escapedSep;

	
	
	public static String safeStr(final String origS) {
		if(origS==null) {
			return "";
		} else {
			String s = origS.replaceAll("\\s+"," ").trim();
			s = s.replace('"','\''); // for Excell
			return s;
		}
	}
	
	public static void printRow(final PrintStream p, final String[] row) {
		final int n = row.length;
		for(int i=0;i<n;++i) {
			if(i>0) {
				p.print('\t');
			}
			final String si = safeStr(row[i]);
			p.print(si);
		}
		p.println();
	}
	
	public TrivialReader(final URI srcURI, final char sep, final String encoding, final ErrorPolicy<String,String[]> errorPolicy, 
			final boolean intern) {
		this.srcURI = srcURI;
		this.encoding = encoding;
		this.errorPolicy = errorPolicy;
		this.intern = intern;
		Map<Character,String> escapes = new TreeMap<Character,String>();
		escapes.put('|',"\\|"); // TODO: add more of these
		escapes.put('t',"\\t"); // TODO: add more of these
		escapedSep = escapes.get(sep);
		if(escapedSep==null) {
			escapedSep = "" + sep;
		}
	}

	public static final String GZSUFFIX = ".gz";
	public static LineNumberReader openBufferedReader(final URI uriSrc, final String encoding) throws IOException {
		// open file with proper treatment
		final InputStream in;
		if(uriSrc.toString().toLowerCase().endsWith(GZSUFFIX)) {
			in = new GZIPInputStream(new BufferedInputStream(uriSrc.toURL().openStream()));
		} else {
			in = uriSrc.toURL().openStream();
		}
		if(encoding==null) {
			return new LineNumberReader(new InputStreamReader(in));
		} else {
			return new LineNumberReader(new InputStreamReader(in,encoding));
		}
	}
	
	public static PrintStream openPrintStream(final File f) throws IOException {
		if(f.getName().toLowerCase().endsWith(GZSUFFIX)) {
			return new PrintStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(f))));
		} else {
			return new PrintStream(new BufferedOutputStream(new FileOutputStream(f)));
		}
	}

	public static final class TrivialIterator implements Iterator<BurstMap> {
		private LineNumberReader reader;
		final String header;
		@SuppressWarnings("unused")
		private int lineNum = 0;
		private String rawLine = null;
		private BurstMap next = null;
		private final LineBurster burster;
		private final String comment;
		
		public TrivialIterator(final LineNumberReader reader, final String escapedSep, final boolean intern, final String comment) throws IOException {
			this.reader = reader;
			this.comment = comment;
			header = getLine();
			if(header==null) {
				burster = null;
			} else {
				burster = new HBurster(escapedSep,header,intern);
				advance();  // get first row into next
			}
		}

		/**
		 * causes reader to be null on close
		 * @throws IOException
		 */
		public void close() throws IOException {
			next = null;
			if(reader!=null) {
				final Reader rdr = reader;
				reader = null;
				rdr.close();
			}
		}

		/**
		 * 
		 * @return null or standard row (non-zero length)
		 * @throws IOException
		 */
		private String getLine() throws IOException {
			rawLine = null;
			if(reader!=null) {
				rawLine = reader.readLine();
				lineNum = reader.getLineNumber();
				if(rawLine==null) {
					close(); // reader null as side effect
					return null;
				} else {
					if(rawLine.trim().length()>0) {
						return rawLine;
					}
				}
			}
			return null;
		}
		
		private void advance() throws IOException {
			next = null;
			while((next==null)&&(reader!=null)) {
				final String line = getLine();
				if(line!=null) {
					next = burster.parse(line);
					if(next!=null) {
						if(next.isEmpty()||(!burster.haveAllFields(next))) {
							next = null;
						}
					}
				}
			}
		}
		
		public boolean hasNext() {
			return next!=null;
		}
		
		public BurstMap next() {
			if(!hasNext()) {
				throw new NoSuchElementException("TrivialIterator");
			}
			final BurstMap ret = next;
			try {
				advance();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}	
			return ret;
		}

		public void remove() {
			throw new UnsupportedOperationException("TrivialIterator");
		}
		
		@Override
		public String toString() {
			return "TrivialIterator(" +  comment + ")";
		}
	}
	
	@Override
	public TrivialIterator iterator() {
		try {
			return new TrivialIterator(openBufferedReader(srcURI,encoding),escapedSep,intern,srcURI.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return "TrivialIterable(" + srcURI + ")";
	}
	
	public static class WarnPolicy implements ErrorPolicy<String,String[]> {
		public final PrintStream p;
		
		public WarnPolicy(final PrintStream p) {
			this.p = p;
		}
		
		public String[] adjudicate(final int lineNumber, final String orig, final String[] hdr, final String[] flds) { 
			p.println("warning skipping line " + lineNumber + ": " + orig);
			return null;
		} 
	}

	public static class PadPolicy implements ErrorPolicy<String,String[]> {
		public String[] adjudicate(final int lineNumber, final String orig, final String[] hdr, final String[] flds) { 
			final int n = hdr.length;
			String[] res = new String[n];
			for(int i=0;(i<flds.length)&&(i<n);++i) {
				res[i] = flds[i];
			}
			for(int i=flds.length;i<n;++i) {
				res[i] = "";
			}
			return res;
		} 
	}
}
