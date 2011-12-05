package com.winvector.opt.def;




/**
 * data carrier, weaker than a sparse vector in that you can not jump to an arbitrary coordinate
 * @author johnmount
 *
 */
public interface ExampleRow extends Datum {
	int category();
	double weight();
}
