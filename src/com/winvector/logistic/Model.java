package com.winvector.logistic;

import java.io.Serializable;

import com.winvector.variables.VariableEncodings;


public class Model implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public Formula origFormula = null;
	public VariableEncodings config = null;
	public double[] coefs = null;
}
