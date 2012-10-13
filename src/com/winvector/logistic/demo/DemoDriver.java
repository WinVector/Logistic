package com.winvector.logistic.demo;

import org.apache.hadoop.util.ProgramDriver;

import com.winvector.Licenses;



/// @SuppressWarnings("deprecation") //  Tool depreciated 0.21.0 a known issue: https://issues.apache.org/jira/browse/MAPREDUCE-2084
public class DemoDriver {
	public static void main(final String args[]) throws Exception {
		try {
			System.out.println("starting DemoDriver");
			for(final String ai: args) {
				System.out.print("\t'" + ai + "'");
			}
			System.out.println();
			final ProgramDriver pgd = new ProgramDriver();
			pgd.addClass("logistictrain", MapReduceLogisticTrain.class, "Train a logistic regression model. (trainFile.tsv formula modelOut.ser)");
			// export executable jar file using DemoDriver run configuration (may need to add memory options and such) packed in dependent libraries
			// in ~/Downloads with a model1.ser in that directory: ~/Documents/workspace/hadoop-0.21.0/bin/hadoop jar ~/Documents/workspace/Logistic/logistic.jar logisticscore model1.ser ~/Documents/workspace/Logistic/test/com/winvector/logistic/uciCarTest.tsv scoredDir
			pgd.addClass("logisticscore", MapReduceScore.class, "Apply a logistic regression model to new data. (modelIn.ser testFile.tsv resultDir)");
			pgd.driver(args);
		} catch (Throwable t) {
			System.out.println(Licenses.licenses);
			t.printStackTrace();
			throw new Exception(t.toString());
		}
	}
}

