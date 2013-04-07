package com.winvector.logistic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import com.winvector.logistic.mr.TestRoundTrip;
import com.winvector.opt.def.DModel;
import com.winvector.opt.def.Datum;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;
import com.winvector.opt.impl.DataFn;
import com.winvector.opt.impl.ExampleRowIterable;
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;
import com.winvector.util.BurstMap;
import com.winvector.util.TrivialReader;
import com.winvector.util.TrivialReader.TrivialIterator;
import com.winvector.variables.PrimaVariableInfo;
import com.winvector.variables.VariableEncodings;
import com.winvector.variables.VariableMapping;



public class TestLRPath {
	
	public static void copyResourceToFile(final String resourceName, final File dest) throws IOException {
		final InputStream is = TestRoundTrip.class.getClassLoader().getResourceAsStream(resourceName);
		final FileOutputStream os = new FileOutputStream(dest);
		int c = -1;
		while((c=is.read())>=0) {
			os.write(c);
		}
		os.close();
		is.close(); 
	}
	
	public static ArrayList<BurstMap> readBurstFromResource(final String resourceName) throws IOException {
		final ArrayList<BurstMap> r = new ArrayList<BurstMap>();
		final InputStream is = TestRoundTrip.class.getClassLoader().getResourceAsStream(resourceName);
		final Iterator<BurstMap> it = new TrivialIterator(new LineNumberReader(new InputStreamReader(is)),Pattern.compile("\t"),false,true,"res:"+resourceName);
		while(it.hasNext()) {
			final BurstMap row = it.next();
			r.add(row);
		}
		return r;
	}
	
	private double relDiff(double a, double b) {
		final double diff = Math.abs(a-b);
		if(diff<=0.0) {
			return 0.0;
		} else {
			return 2.0*diff/(Math.abs(a)+Math.abs(b));
		}
	}
	
	/** 
	 * essentially the same loop structure as VariableEncodings.formatSoln.  So double calculating here confirms
	 * the results are coming out correctly.
	 * @param trainSource
	 * @param adapter
	 * @param sigmoidLoss
	 * @param x
	 */
	private <T extends ExampleRow> void confirmEffectCalc(final Iterable<BurstMap> trainSource, final VariableEncodings adapter, 
			final DModel<T> sigmoidLoss, final double[] x) {
		// confirm effects work like we think
		for(final BurstMap row: trainSource) {
			// score the standard way
			final SparseSemiVec vec = adapter.vector(row);
			if(null!=vec) {
				//final String resStr = row.getAsString(adapter.def().resultColumn);
				//final int category = adapter.category(resStr);
				final Datum ei = new SparseExampleRow(vec,1.0,-1);
				final double[] pred = sigmoidLoss.predict(x,ei);
				// score via effects
				final double[] predE = new double[adapter.outcomeCategories.entrySet().size()];
				for(final Map.Entry<String,Integer> mc: adapter.outcomeCategories.entrySet()) {
					//final String outcome = mc.getKey();
					final int cati = mc.getValue();
					final int base = cati*adapter.vdim;
					for(final VariableMapping adaption: adapter.adaptions) {
						final String origName = adaption.origColumn();
						final String level = row.getAsString(origName);
						final double effectT = adaption.effectTest(base,x,level);
						final double effect = adaption.effect(base,x,level);
						final double relDiff = relDiff(effect,effectT);
						assertTrue(relDiff<1.0e-3);
						predE[cati] += effect;
					}
				}
				HelperFns.expScale(predE);
				assertEquals(pred.length,predE.length);
				for(int i=0;i<pred.length;++i) {
					final double relDiff = relDiff(pred[i],predE[i]);
					assertTrue(relDiff<1.0e-3);
				}
			}
		}
	}


	
	
	@Test
	public void testTrainAccuracy() throws Exception {
		final Iterable<BurstMap> trainSource = TestLRPath.readBurstFromResource("com/winvector/logistic/uciCarTrain.tsv");
		final String formulaStr = "rating ~ buying + maintenance + doors + persons + lug_boot + safety";
		final Formula f = new Formula(formulaStr);
		final boolean useIntercept = true;
		final PrimaVariableInfo def = LogisticTrain.buildVariableDefs(f,trainSource);
		final VariableEncodings adapter = new VariableEncodings(def,useIntercept,null);
		final Iterable<ExampleRow> asTrain = new ExampleRowIterable(adapter,trainSource);
		final SigmoidLossMultinomial sigmoidLoss = new SigmoidLossMultinomial(adapter.dim(),adapter.noutcomes());
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow>(sigmoidLoss,asTrain),1.0e-5,adapter.adaptions);
		final VectorOptimizer nwt = new Newton();
		final VEval opt = nwt.maximize(sl,null,10);
		System.out.println("done training\t" + new Date());
		System.out.println("soln vector: " + LinUtil.toString(opt.x));
		System.out.println("soln details:\n" + adapter.formatSoln(opt.x));
		final double trainAccuracy = HelperFns.accuracy(sigmoidLoss,asTrain,opt.x);
		assertTrue(Math.abs(trainAccuracy-0.9693)<1.0e-2);
		confirmEffectCalc(trainSource,adapter,sigmoidLoss,opt.x);
	}

	
	
	@Test
	public void testTrainScore() throws Exception {
		final File tmpDir = File.createTempFile("FJunit_",".dir");
		tmpDir.delete();
		tmpDir.mkdirs();
		final File trainFile = new File(tmpDir,"uciCarTrain.tsv");
		final File modelFile = new File(tmpDir,"model.ser");
		final File resultFile = new File(tmpDir,"scored.tsv");
		TestLRPath.copyResourceToFile("com/winvector/logistic/uciCarTrain.tsv",trainFile);
		final TrivialReader trainSource = new TrivialReader(trainFile.toURI(),'\t',null,false,null, false);
		(new LogisticTrain()).run(trainSource,new Formula("rating ~ buying + maintenance + doors + persons + lug_boot + safety"),null,
				modelFile,null);
		final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
		final Model model = (Model)ois.readObject();		
		ois.close();
		final double accuracy = LogisticScore.score(model,trainSource,resultFile);
		// clean up
		modelFile.delete();
		trainFile.delete();
		resultFile.delete();
		tmpDir.delete();
		// test
		assertTrue(Math.abs(accuracy-0.9693)<1.0e-2);
	}
	

	/** 
	 * make sure we can score rows without a result already present
	 * @throws Exception
	 */
	@Test
	public void testTestScore() throws Exception {
		final File tmpDir = File.createTempFile("FJunit_",".dir");
		tmpDir.delete();
		tmpDir.mkdirs();
		final File trainFile = new File(tmpDir,"uciCarTrain.tsv");
		final File testFile = new File(tmpDir,"uciCarTest2.tsv");
		final File modelFile = new File(tmpDir,"model.ser");
		final File resultFile = new File(tmpDir,"scored.tsv");
		TestLRPath.copyResourceToFile("com/winvector/logistic/uciCarTrain.tsv",trainFile);
		TestLRPath.copyResourceToFile("com/winvector/logistic/uciCarTest2.tsv",testFile);
		final TrivialReader trainSource = new TrivialReader(trainFile.toURI(),'\t',null,false,null, false);
		final TrivialReader testSource = new TrivialReader(testFile.toURI(),'\t',null,false,null, false);
		(new LogisticTrain()).run(trainSource,new Formula("rating ~ buying + maintenance + doors + persons + lug_boot + safety"),null,
				modelFile,null);
		final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
		final Model model = (Model)ois.readObject();		
		ois.close();
		LogisticScore.score(model,testSource,resultFile);
		assertTrue(resultFile.length()>=50000);  // confirm we wrote some rows
		// clean up
		modelFile.delete();
		trainFile.delete();
		testFile.delete();
		resultFile.delete();
		tmpDir.delete();
	}
}
