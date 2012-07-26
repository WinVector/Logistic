package com.winvector.opt.def;


/**
 * Result of a VectorFn eval or the accumulation structure for a LinearContribution 
 * @author johnmount
 *
 */
public class VEval {
	public double[] x;
	public double fx;
	public double[] gx;
	public double[][] hx;
	
	public VEval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
		final int dim = x.length;
		this.x = new double[dim];
		fx = 0.0;
		if(wantGrad) {
			gx = new double[dim];
		} else {
			gx = null;
		}
		if(wantHessian) {
			hx = new double[dim][dim]; 
		} else {
			hx = null;
		}
		for(int i=0;i<dim;++i) {
			this.x[i] = x[i];
		}
	}
	
	protected VEval() {
		x = null;
		fx = 0.0;
		gx = null;
		hx = null;
	}
	
	public String toString() {
		return "VEval:\n\tx\t" + LinUtil.toString(x) + "\n\tfx\t" + fx + "\n\tgx\t" + LinUtil.toString(gx) + "\n\thx\t" + LinUtil.toString(hx) + "\n";
	}
	
	
	/**
	 * slow operation
	 * assumes the two x's are the same
	 * @param o
	 */
	public void add(final VEval o) {
		final int dim = x.length;
		fx += o.fx;
		if(gx!=null) {
			for(int i=0;i<dim;++i) {
				gx[i] += o.gx[i];
			}
		}
		if(hx!=null) {
			for(int i=0;i<dim;++i) {
				for(int j=0;j<dim;++j) {
					hx[i][j] += o.hx[i][j];
				}
			}
		}
	}
}
