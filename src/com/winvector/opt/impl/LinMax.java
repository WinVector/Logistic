package com.winvector.opt.impl;

import com.winvector.opt.def.ScalarFn;

public final class LinMax {
	public int maxInitialProbeLevels = 5;
	public int maxGrowLevels = 5;
	private static double golden = 2.0/(1.0+Math.sqrt(5.0)); // 0.618
	
	public double maximize(final ScalarFn f, double f0, final double initialScale, final int maxSteps) {
		int stepNum = 0;
		if(Double.isNaN(f0)||Double.isInfinite(f0)) {
			f0 = f.eval(0.0);
		}
		// start preparing a bracket around a maximum (aX,midX,bX) a three point ordered interval aX<midX<bX and max(f(aX),f(bX))<=f(midX)
		double midX = 0;
		double fmid = f0;
		double aX = midX - initialScale;
		double fA = f.eval(aX);
		double bX = midX + initialScale;
		double fB = f.eval(bX);
		// establish bracket
		while(fA>=fmid) {
			final double step = midX-aX;
			midX = aX;
			fmid = fA;
			aX -= 2*step;
			fA = f.eval(aX);
			if((++stepNum)>maxSteps) {
				return midX;
			}
		}
		while(fB>=fmid) {
			final double step = bX - midX;
			midX = bX;
			fmid = fB;
			bX += 2*step;
			fB = f.eval(bX);
			if((++stepNum)>maxSteps) {
				return midX;
			}
		}
		while(true) {
			// now have f(midX) >= max(f(aX),f(bX)), maintain this and sub-divide larger interval
			/*
			System.out.println("\tf(" + aX + ")=" + fA
					+ "\tf(" + midX + ")=" + fmid
					+ "\tf(" + bX + ")=" + fB
					+ "\t\tgood: " + ((fmid>=fA)&&(fmid>=fB) && ((aX<midX && midX<bX)||(aX>midX && midX>bX))));
			*/
			if(Math.abs(midX-aX)>=Math.abs(bX-midX)) {
				final double pX = (1-golden)*aX+golden*midX;
				final double fx = f.eval(pX);
				if(fx>fmid) {
					bX = midX;
					fB = fmid;
					midX = pX;
					fmid = fx;
				} else {
					aX = pX;
					fA = fx;
				}
			} else {
				final double pX = golden*midX+(1-golden)*bX;
				final double fx = f.eval(pX);
				if(fx>fmid) {
					aX = midX;
					fA = fmid;
					midX = pX;
					fmid = fx;
				} else {
					bX = pX;
					fB = fx;
				}
			}
			if(Math.abs(bX-aX)<=1.0e-2*initialScale) {
				break; // interval is too small
			}
			if((++stepNum)>maxSteps) {
				break;
			}
		}
		return midX;
	}
	
	// TODO: test
	public double minimize(final ScalarFn f, final double f0, final double initialScale, final int maxSteps) {
		final ScalarFn fNeg = new ScalarFn() {
			@Override
			public double eval(final double x) {
				return -f.eval(x);
			}
		};
		return maximize(fNeg,-f0,initialScale,maxSteps);
	}
}
