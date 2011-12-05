package com.winvector.logistic.mr;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Writable;
//import org.apache.hadoop.mapreduce.Cluster;
import org.apache.hadoop.mapreduce.Job;

import com.winvector.util.SerialUtils;


public final class WritableUtils {
	
	public static final Job newJob(final Configuration mrConfig) throws IOException {
		//// Hadoop 0.20.2 way of doing this (works in 0.21.0, used in their examples but depreciated)
		/// @SuppressWarnings("deprecation")
		final Job job = new Job(mrConfig);
		//// Hadoop 0.21.0 way of doing this
		// final Cluster clus = new Cluster(mrConfig);
		//final Job job = Job.getInstance(clus,mrConfig);
		return job;
	}
	
	public static String readFirstLine(final Configuration mrConfig, final Path pathIn) throws IOException {
		final FSDataInputStream fdi = pathIn.getFileSystem(mrConfig).open(pathIn);
		final BufferedReader d = new BufferedReader(new InputStreamReader(fdi));
		final String line = d.readLine();
		d.close();
		return line;
	}
	
	public static String writableToString(final Writable o) throws IOException {
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		final DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(bos));
		o.write(dos);
		dos.close();
		final byte[] bytes = bos.toByteArray();
		return SerialUtils.bytesToEncodedString(bytes);
	}
	
	public static void readWritableFieldsFromString(final String s, final Writable o) throws IOException {
		final byte[] bytes = SerialUtils.bytesFromEncodedString(s);
		final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		final DataInputStream dis = new DataInputStream(new GZIPInputStream(bis));
		o.readFields(dis);
		dis.close();
	}

	public static double[] readVec(final DataInput in) throws IOException {
		final int n = in.readInt();
		final double[] r = new double[n];
		for(int i=0;i<n;++i) {
			r[i] = in.readDouble();
		}
		return r;		
	}

	public static void writeVec(final double[] v, final DataOutput out) throws IOException {
		final int n = v.length;
		out.writeInt(n);
		for(int i=0;i<n;++i) {
			out.writeDouble(v[i]);
		}
	}

	public static String hostDescr() {
		try {
			final InetAddress ia = InetAddress.getLocalHost();
			final byte[] ipaddr = ia.getAddress();
			final String hostname = ia.getHostName();
			final StringBuilder b = new StringBuilder();
			b.append("" + hostname + " (");
			boolean first = true;
			for(final byte bi: ipaddr) {
				if(first) {
					first = false;
				} else {
					b.append(".");
				}
				b.append("" + (0x0ff&(int)bi));
			}
			b.append(" )");
			return b.toString();
		} catch (Exception ex) {
			return "host descr caught: " + ex;
		}		
	}
}
