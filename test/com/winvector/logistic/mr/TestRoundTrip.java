package com.winvector.logistic.mr;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import com.winvector.logistic.Formula;
import com.winvector.logistic.LogisticScore;
import com.winvector.logistic.LogisticTrain;
import com.winvector.logistic.Model;
import com.winvector.logistic.TestLRPath;
import com.winvector.logistic.demo.MapReduceLogisticTrain;
import com.winvector.logistic.demo.MapReduceScore;
import com.winvector.util.TrivialReader;


public class TestRoundTrip {
	

	@Test
	public void testMRScore() throws Exception {
		final File tmpDir = File.createTempFile("MRJunit_",".dir");
		tmpDir.delete();
		tmpDir.mkdirs();
		final File trainFile = new File(tmpDir,"uciCarTrain.tsv");
		final File modelFile = new File(tmpDir,"model.ser");
		final File resDir = new File(tmpDir,"mrRes");
		TestLRPath.copyResourceToFile("com/winvector/logistic/uciCarTrain.tsv",trainFile);
		(new LogisticTrain()).run(new TrivialReader(trainFile.toURI(),'\t',null,false,null, false),new Formula("rating ~ buying + maintenance + doors + persons + lug_boot + safety"),null,
				modelFile,null);
		final MapReduceScore mrs = new MapReduceScore();
		mrs.setConf(new Configuration());
		final double accuracy = mrs.run(modelFile.getAbsolutePath(),trainFile.getAbsolutePath(),resDir.getAbsolutePath());
		// clean up
		modelFile.delete();
		trainFile.delete();
		for(final File f: resDir.listFiles()) {
			f.delete();
		}
		resDir.delete();
		tmpDir.delete();
		// test
		assertTrue(Math.abs(accuracy-0.9693)<1.0e-2);
	}
	
	@Test
	public void testMRTrain() throws Exception {
		final File tmpDir = File.createTempFile("MRJunit_",".dir");
		tmpDir.delete();
		tmpDir.mkdirs();
		final File trainFile = new File(tmpDir,"exB.txt");
		final File modelFile = new File(tmpDir,"model.ser");
		final File resultFile = new File(tmpDir,"scored.tsv");
		TestLRPath.copyResourceToFile("com/winvector/logistic/exB.txt",trainFile);
		final MapReduceLogisticTrain mrt = new MapReduceLogisticTrain();
		mrt.setConf(new Configuration());
		final double accuracy1 = mrt.run(trainFile.getAbsolutePath(),"y ~ x1 + x2", null,
				modelFile.getAbsolutePath(), 5);
		final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
		final Model model = (Model)ois.readObject();		
		ois.close();
		final double accuracy2 = LogisticScore.score(model,new TrivialReader(trainFile.toURI(),'\t',null,false,null, false),resultFile);
		// clean up
		modelFile.delete();
		trainFile.delete();
		resultFile.delete();
		tmpDir.delete();
		// test
		assertTrue(Math.abs(accuracy1-1.0)<1.0e-3);
		assertTrue(Math.abs(accuracy2-1.0)<1.0e-3);
	}
}
