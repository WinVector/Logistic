package com.winvector.util;

public interface ErrorPolicy<I,O> {
	// return null for "skip"
	O adjudicate(int lineNumber, I input, O hdr, O output) throws Exception;
}
