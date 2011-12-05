package com.winvector.opt.def;

import java.util.Formatter;

public class LinUtil {
	public static double[] copy(final double[] x) {
		final int dim = x.length;
		final double[] r = new double[dim];
		for(int i=0;i<dim;++i) {
			r[i] = x[i];
		}
		return r;
	}
	
	public static double dot(final double[] a, final double[] b) {
		final int dim = a.length;
		double r = 0.0;
		for(int i=0;i<dim;++i) {
			r += a[i]*b[i];
		}
		return r;
	}

	public static double[] mult(final double[][] a, final double[] x, final double[] r) {
		final int m = a.length;
		final int n = x.length;
		for(int i=0;i<m;++i) {
			double t = 0.0;
			for(int j=0;j<n;++j) {
				t += a[i][j]*x[j];
			}
			r[i] = t;
		}
		return r;
	}

	public static double[] mult(final double[][] a, final double[] x) {
		final int m = a.length;
		final double[] r = new double[m];
		mult(a,x,r);
		return r;
	}

	public static double[][] copy(double[][] a) {
		final int m = a.length;
		final int n = a[0].length;		
		final double[][] r = new double[m][n];
		for(int i=0;i<m;++i) {
			for(int j=0;j<n;++j) {
				r[i][j] = a[i][j];
			}
		}
		return r;
	}
	
	public static String toString(final double[] x) {
		final StringBuilder b = new StringBuilder();
		if(x!=null) {
			final Formatter formatter = new Formatter(b);
			for(final double xi: x) {
				if(b.length()>0) {
					b.append("\t");
				}
				formatter.format("%g",xi);
				formatter.flush();
			}
		} else {
			b.append("null");
		}
		return b.toString();
	}
	
	public static String toString(final double[][] x) {
		final StringBuilder b = new StringBuilder();
		if(x!=null) {
			for(final double xi[]: x) {
				b.append(" " + toString(xi) + "\n");
			}
		} else {
			b.append(" null");
		}
		return b.toString();
	}
}
