package com.winvector.opt.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import com.winvector.opt.def.ScalarFn;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;

/**
 * caches recent evals (de-bounce fn evals so other code can be written simply)
 * keeps best eval (maximal)
 * @author johnmount
 *
 */
public final class SFun implements ScalarFn {
	public final VectorFn f;
	public final double[] x0;
	public final double[] dir;
	public final double boxBound;
	public VEval min = null;
	public VEval max = null;
	private final Map<Double,VEval> cache = new LinkedHashMap<Double,VEval>() {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(final Map.Entry<Double,VEval> eldest) {
			return size()>10;
		}
	};
	
	/**
	 * 
	 * @param f funciton
	 * @param x0 point to evaluate around
	 * @param dir direction to move
	 * @param boxBound boundingBox on coords
	 * @param fx0  if not null f(x0)
	 */
	public SFun(final VectorFn f, final double[] x0, final double[] dir, final double boxBound, final VEval fx0) {
		this.f = f;
		this.x0 = x0;
		this.dir = dir;
		this.boxBound = boxBound;
		if(fx0!=null) {
			if((!Double.isInfinite(fx0.fx))&&(!Double.isNaN(fx0.fx))) {
				min = fx0;
				max = fx0;
			}
			cache.put(0.0,fx0);
		}
	}
	
	@Override
	public double eval(final double s) {
		VEval fx = cache.get(s);
		if(null==fx) {
			final double[] newX = newX(x0,dir,s,boxBound);
			fx = f.eval(newX,false,false);
			if((!Double.isInfinite(fx.fx))&&(!Double.isNaN(fx.fx))) {
				if((min==null)||(fx.fx<min.fx)) {
					min = fx;
				}
				if((max==null)||(fx.fx>max.fx)) {
					max = fx;
				}
			}
			cache.put(s,fx);
		}
		return fx.fx;
	}

	static double[] newX(final double[] oldX, final double[] delta, final double scale, final double boxBound) {
		final int dim = oldX.length;
		final double[] newX = new double[dim];
		for(int i=0;i<dim;++i) {
			final double oxi = oldX[i];
			if((!Double.isInfinite(oxi))&&(!Double.isNaN(oxi))) {
				final double di = scale*delta[i];
				final double nxi;
				if(Double.isInfinite(di)||Double.isNaN(di)) {
					nxi = oxi;
				} else {
					nxi = oxi + di;
				}
				newX[i] = Math.min(boxBound,Math.max(-boxBound,nxi));
			}
		}
		return newX;
	}
}