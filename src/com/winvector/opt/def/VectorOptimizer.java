package com.winvector.opt.def;

public interface VectorOptimizer {
	/**
	 * run complete optimization
	 * @param f vector function to maximize
	 * @param x initial start
	 * @param maxRounds maximum number of rounds to try (approximate)
	 * @return
	 */
	VEval maximize(final VectorFn f, double[] x, final int maxRounds);
}
