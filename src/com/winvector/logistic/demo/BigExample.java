package com.winvector.logistic.demo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.Random;

public class BigExample {
	public static void main(String[] args) throws IOException {
		final File outFile = new File(args[0]);
		final Random rand = new Random(52325);
		final int kVars = 10;
		final int nLevels = 10;
		final int nRows = 1000000;
		
		System.out.println("writing:\t" + outFile.getAbsolutePath() + "\t" + new Date());
		
		final double dcTerm = rand.nextDouble();
		final double[][] levelValues = new double[kVars][nLevels];
		for(int vi=0;vi<kVars;++vi) {
			for(int li=0;li<nLevels;++li) {
				levelValues[vi][li] = rand.nextGaussian();
			}
		}
		final PrintStream p = new PrintStream(new FileOutputStream(outFile));
		p.print("Result");
		for(int vi=0;vi<kVars;++vi) {
			p.print("\tV" + vi);
		}
		p.println();
		int nCorrect = 0;
		for(int row=0;row<nRows;++row) {
			int[] choices = new int[kVars];
			double tot = dcTerm;
			for(int vi=0;vi<kVars;++vi) {
				choices[vi] = rand.nextInt(nLevels);
				tot += levelValues[vi][choices[vi]];
			}
			final boolean calc = tot>0.0;
			tot += rand.nextDouble(); // noise term
			final boolean pos = tot>0.0;
			if(calc==pos) {
				++nCorrect;
			}
			p.print(pos?"1":"0");
			for(int vi=0;vi<kVars;++vi) {
				p.print("\tV" + vi + "L" + choices[vi]);
			}			
			p.println();
		}
		p.close();
		System.out.println("\ttarget accuracy: " + nCorrect + "/" + nRows + " = " + (nCorrect/(double)nRows));
	}
}
