package com.winvector.logistic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Test;

import com.winvector.db.DBIterable;
import com.winvector.db.DBUtil;
import com.winvector.db.DBUtil.DBHandle;
import com.winvector.db.LoadTable;
import com.winvector.logistic.Formula;
import com.winvector.logistic.LogisticScore;
import com.winvector.logistic.LogisticTrain;
import com.winvector.logistic.LogisticTrainPlus;
import com.winvector.logistic.Model;
import com.winvector.logistic.SigmoidLossMultinomial;
import com.winvector.logistic.mr.TestRoundTrip;
import com.winvector.opt.def.Datum;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;
import com.winvector.opt.impl.ExampleRowIterable;
import com.winvector.opt.impl.DataFn;
import com.winvector.opt.impl.GradientDescent;
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.util.BurstMap;
import com.winvector.util.CountMap;
import com.winvector.util.TrivialReader;
import com.winvector.util.TrivialReader.TrivialIterator;
import com.winvector.variables.BTable;
import com.winvector.variables.PrimaVariableInfo;
import com.winvector.variables.VariableEncodings;
import com.winvector.variables.VariableMapping;


public class TestLRPathPlus {
	
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
		final Iterator<BurstMap> it = new TrivialIterator(new LineNumberReader(new InputStreamReader(is)),"\t",false,true,"res:"+resourceName);
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
	private void confirmEffectCalc(final Iterable<BurstMap> trainSource, final VariableEncodings adapter, 
			final LinearContribution<ExampleRow> sigmoidLoss, final double[] x) {
		// confirm effects work like we think
		for(final BurstMap row: trainSource) {
			// score the standard way
			final Map<Integer,Double> vec = adapter.vector(row);
			//final String resStr = row.getAsString(adapter.def().resultColumn);
			//final int category = adapter.category(resStr);
			final Datum ei = new SparseExampleRow(vec,-1);
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


	
	
	@Test
	public void testTrainAccuracy() throws Exception {
		final Iterable<BurstMap> trainSource = TestLRPathPlus.readBurstFromResource("com/winvector/logistic/uciCarTrain.tsv");
		final String formulaStr = "rating ~ buying + maintenance + doors + persons + lug_boot + safety";
		final Formula f = new Formula(formulaStr);
		final boolean useIntercept = true;
		final PrimaVariableInfo def = LogisticTrainPlus.buildVariableDefs(f,trainSource);
		final VariableEncodings adapter = new VariableEncodings(def,useIntercept);
		final Iterable<ExampleRow> asTrain = new ExampleRowIterable(adapter,trainSource);
		final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(adapter.dim(),adapter.noutcomes());
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow,ExampleRow>(sigmoidLoss,asTrain),0.1);
		final VectorOptimizer nwt = new Newton();
		final VEval opt = nwt.maximize(sl,null,Integer.MAX_VALUE);
		System.out.println("done training\t" + new Date());
		System.out.println("soln vector: " + LinUtil.toString(opt.x));
		System.out.println("soln details:\n" + adapter.formatSoln(opt.x));
		final double trainAccuracy = HelperFns.accuracy(sigmoidLoss,asTrain,opt.x);
		assertTrue(Math.abs(trainAccuracy-0.9693)<1.0e-3);
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
		TestLRPathPlus.copyResourceToFile("com/winvector/logistic/uciCarTrain.tsv",trainFile);
		final TrivialReader trainSource = new TrivialReader(trainFile.toURI(),'\t',null,false,null, false);
		(new LogisticTrain()).run(trainSource,new Formula("rating ~ buying + maintenance + doors + persons + lug_boot + safety"),modelFile,null);
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
		assertTrue(Math.abs(accuracy-0.9693)<1.0e-3);
	}

	/**
	 * generate a unit vector uniformly at random
	 * NOT an effective encoding as under the random projection good and bad levels tend to be intermixed
	 * and inseparable.  Just here to exercise non-identity encoding code path without the pain of adaption.
	 * @param rand
	 * @return
	 */
	private static double[] randVec(final int targetDim, final Random rand) {
		final double[] v = new double[targetDim];
		while(true) {
			double normSq = 0.0;
			for(int i=0;i<targetDim;++i) {
				v[i] = rand.nextGaussian();
				normSq += v[i]*v[i];
			}
			if(normSq>1.0e-2) {
				final double scale = 1.0/Math.sqrt(normSq);
				for(int i=0;i<targetDim;++i) {
					v[i] *= scale;
				}
				return v;
			}
		}
	}
	
	
	/**
	 * not a good encoding- just testing data path witout needing updates
	 * @throws Exception
	 */
	@Test
	public void testRandVectorEncoding() throws Exception {
		final Iterable<BurstMap> trainSource = TestLRPathPlus.readBurstFromResource("com/winvector/logistic/uciCarTrain.tsv");
		final String formulaStr = "rating ~ buying + maintenance + doors + persons + lug_boot + safety";
		final Formula f = new Formula(formulaStr);
		final boolean useIntercept = true;
		final int targetDim = 4;
		final Random rand = new Random(23251);
		final PrimaVariableInfo def = LogisticTrainPlus.buildVariableDefs(f,trainSource);
		final Map<String,Map<String,double[]>> vectorEncodings = new TreeMap<String,Map<String,double[]>>();
		final Map<String,Map<String,String[]>> vectorEncodingNames = new TreeMap<String,Map<String,String[]>>();
		int rc = 0;
		for(final Entry<String, CountMap<String>> me: def.catLevels.entrySet()) {
			final String variable = me.getKey();
			final Map<String, double[]> codes = new TreeMap<String,double[]>();
			final Map<String, String[]> names = new TreeMap<String,String[]>();
			for(final String level: me.getValue().keySet()) {
				codes.put(level,randVec(targetDim,rand));
				final String[] nameV = new String[targetDim];
				for(int i=0;i<nameV.length;++i) {
					nameV[i] = "rand_" + rc + "_" + i;
				}
				++rc;
				names.put(level,nameV);
			}
			vectorEncodings.put(variable,codes);
			vectorEncodingNames.put(variable,names);
		}
		final VariableEncodings adapter = new VariableEncodings(def,useIntercept,vectorEncodings,vectorEncodingNames);
		final Iterable<ExampleRow> asTrain = new ExampleRowIterable(adapter,trainSource);
		final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(adapter.dim(),adapter.noutcomes());
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow,ExampleRow>(sigmoidLoss,asTrain),0.1); 
		final VectorOptimizer nwt = new Newton();
		final VEval opt = nwt.maximize(sl,null,Integer.MAX_VALUE);
		System.out.println("done training\t" + new Date());
		System.out.println("soln vector: " + LinUtil.toString(opt.x));
		System.out.println("soln details:\n" + adapter.formatSoln(opt.x));
		final double trainAccuracy = HelperFns.accuracy(sigmoidLoss,asTrain,opt.x);
		assertTrue(Math.abs(trainAccuracy-0.9693)<1.0e-2);
		confirmEffectCalc(trainSource,adapter,sigmoidLoss,opt.x);
	}
	
	@Test
	public void testVectorEncodingB() throws Exception {
		final Iterable<BurstMap> trainSource = TestLRPathPlus.readBurstFromResource("com/winvector/logistic/uciCarTrain.tsv");
		final String formulaStr = "rating ~ buying + maintenance + doors + persons + lug_boot + safety";
		final Formula f = new Formula(formulaStr);
		final boolean useIntercept = true;
		final PrimaVariableInfo def = LogisticTrainPlus.buildVariableDefs(f,trainSource);
		final Set<String> varsToEncode = new HashSet<String>();
		for(final String variable: def.catLevels.keySet()) {
			final int support = def.catLevels.get(variable).keySet().size();
			if(support>3) {
				varsToEncode.add(variable);
			}
		}
		
		final VariableEncodings standardEncodings = new VariableEncodings(def,useIntercept);
		final double[] newtonX;
		final double newtonDataPortion;
		{
			double[] opt = null;
			double optfx = Double.NEGATIVE_INFINITY;
			BTable vectorEncodings = BTable.buildStatBasedEncodings(varsToEncode,trainSource,standardEncodings,null);
			for(int pass=0;pass<5;++pass) {
				System.out.println("pass: " + pass);
				if(opt!=null) {
					final Map<String[],Double> decodeOld = vectorEncodings.newAdapter.decodeSolution(opt,true);
					final double preScore = (new DataFn<ExampleRow,ExampleRow>(new SigmoidLossMultinomial(vectorEncodings.newAdapter.dim(),vectorEncodings.newAdapter.noutcomes()),new ExampleRowIterable(vectorEncodings.newAdapter,trainSource))).eval(opt,false,false).fx;
					vectorEncodings = BTable.buildStatBasedEncodings(varsToEncode,trainSource,vectorEncodings.newAdapter,opt);
					System.out.println("warmstart vector: " + LinUtil.toString(vectorEncodings.warmStart));
					System.out.println("warmstart details:\n" + vectorEncodings.newAdapter.formatSoln(vectorEncodings.warmStart));
					final Map<String[],Double> decodeNew = vectorEncodings.newAdapter.decodeSolution(vectorEncodings.warmStart,true);
					// confirm warmstart equivalent to old soln
					assertEquals(decodeOld.size(),decodeNew.size());
					for(final Map.Entry<String[],Double> me: decodeOld.entrySet()) {
						final String[] key = me.getKey();
						final double value = me.getValue();
						final Double nv = decodeNew.get(key);
						assertNotNull(nv);
						final double relDiff = relDiff(value,nv);
						assertTrue(relDiff<1.0e-3);
					}
					final double postScore = (new DataFn<ExampleRow,ExampleRow>(new SigmoidLossMultinomial(vectorEncodings.newAdapter.dim(),vectorEncodings.newAdapter.noutcomes()),new ExampleRowIterable(vectorEncodings.newAdapter,trainSource))).eval(vectorEncodings.warmStart,false,false).fx;
					System.out.println("preScore:  " + preScore);
					System.out.println("postScore: " + postScore);
					assertTrue(relDiff(preScore,postScore)<1.0e-3);
					opt = vectorEncodings.warmStart;
				}
				final Iterable<ExampleRow> asTrain = new ExampleRowIterable(vectorEncodings.newAdapter,trainSource);
				final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(vectorEncodings.newAdapter.dim(),vectorEncodings.newAdapter.noutcomes());
				final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow,ExampleRow>(sigmoidLoss,asTrain),0.1);  // should be lower like 1.0e-3 but 0.1 forces more passes
				final VectorOptimizer nwt = new Newton();
				final VEval newOpt = nwt.maximize(sl,vectorEncodings.warmStart,Integer.MAX_VALUE);
				if((null!=opt)&&(optfx+1.0e-3>=newOpt.fx)) {
					break;
				}
				opt = newOpt.x;
				optfx = newOpt.fx;
				System.out.println("done Newton training\t" + new Date());
				System.out.println("soln vector: " + LinUtil.toString(opt));
				System.out.println("soln details:\n" + vectorEncodings.newAdapter.formatSoln(opt));
				final double trainAccuracy = HelperFns.accuracy(sigmoidLoss,asTrain,opt);
				System.out.println("train accuracy:" + trainAccuracy);
			}
			// decode x to standard basis
			newtonX = vectorEncodings.translateTo(standardEncodings,opt);
			// regularization term will be different as it depends directly on the variable encoding
			final double oldScore = (new DataFn<ExampleRow,ExampleRow>(new SigmoidLossMultinomial(vectorEncodings.newAdapter.dim(),vectorEncodings.newAdapter.noutcomes()),new ExampleRowIterable(vectorEncodings.newAdapter,trainSource))).eval(opt,false,false).fx;
			final double newScore = (new DataFn<ExampleRow,ExampleRow>(new SigmoidLossMultinomial(standardEncodings.dim(),standardEncodings.noutcomes()),new ExampleRowIterable(standardEncodings,trainSource))).eval(newtonX,false,false).fx;
			System.out.println("oldScore: " + oldScore);
			System.out.println("newScore: " + newScore);
			assertTrue(relDiff(oldScore,newScore)<1.0e-3);
			// confirm translation equivalent to new soln
			final Map<String[],Double> decodeNew = vectorEncodings.newAdapter.decodeSolution(opt,false);
			final Map<String[],Double> decodeStandard = standardEncodings.decodeSolution(newtonX,false);
			System.out.println("soln:");
			System.out.println(vectorEncodings.newAdapter.formatSoln(opt));
			System.out.println("translated:");
			System.out.println(standardEncodings.formatSoln(newtonX));
			assertEquals(decodeNew.size(),decodeStandard.size());
			for(final Map.Entry<String[],Double> me: decodeNew.entrySet()) {
				final String[] key = me.getKey();
				final double value = me.getValue();
				final Double nv = decodeStandard.get(key);
				assertNotNull(nv);
				final double relDiff = relDiff(value,nv);
				assertTrue(relDiff<1.0e-3);
			}

			newtonDataPortion = oldScore;
		}
		// gradient polish, TODO: switch from gradient descent to conjugate gradient
		{
			final VectorOptimizer polisher = new GradientDescent();
			final Iterable<ExampleRow> asTrain = new ExampleRowIterable(standardEncodings,trainSource);
			final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(standardEncodings.dim(),standardEncodings.noutcomes());
			final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow,ExampleRow>(sigmoidLoss,asTrain),1.0e-5);
			final double newScore = (new DataFn<ExampleRow,ExampleRow>(new SigmoidLossMultinomial(standardEncodings.dim(),standardEncodings.noutcomes()),new ExampleRowIterable(standardEncodings,trainSource))).eval(newtonX,false,false).fx;
			assertTrue(relDiff(newtonDataPortion,newScore)<1.0e-3);
			final VEval opt = polisher.maximize(sl,newtonX,20);
			System.out.println("done gradient polish training\t" + new Date());
			System.out.println("soln vector: " + LinUtil.toString(opt.x));
			System.out.println("soln details:\n" + standardEncodings.formatSoln(opt.x));
			final double trainAccuracy = HelperFns.accuracy(sigmoidLoss,asTrain,opt.x);
			System.out.println("train accuracy:" + trainAccuracy);
		}
	}
	
	@Test
	public void testDBPath() throws Exception {
		// build DB connection
		final String comment = "test";
		final String dbUserName = "";
		final String dbPassword = "";
		final String driver = "org.h2.Driver";
		final File tmpFile = File.createTempFile("TestH2DB",".dir");
		tmpFile.delete();
		tmpFile.mkdirs();
		final String dbURL = "jdbc:h2:/" + (new File(tmpFile,"H2DB")).getAbsolutePath() + ";LOG=0;CACHE_SIZE=65536;LOCK_MODE=0;UNDO_LOG=0";
		final boolean readOnly = false;
		final DBHandle handle = DBUtil.buildConnection(comment,
				dbUserName,
				dbPassword,
				dbURL,
				driver,
				readOnly);
		// copy data into table
		System.out.println("test db: " + handle);
		final String tableName = "testTable";
		final Iterable<BurstMap> source = TestLRPathPlus.readBurstFromResource("com/winvector/logistic/uciCarTrain.tsv");
		LoadTable.loadTable(source, null, tableName, handle);
		// set up formula
		final String formulaStr = "rating ~ buying + maintenance + doors + persons + lug_boot + safety";
		final Formula f = new Formula(formulaStr);
		// bring data back out of table
		final Statement stmt = handle.conn.createStatement();
		final StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		{
			boolean first = true;
			for(final String term: f.allTerms()) {
				if(first) {
					first = false;
				} else {
					query.append(",");
				}
				query.append(term);
			}
		}
		query.append(" FROM ");
		query.append(tableName);
		final Iterable<BurstMap> trainSource = new DBIterable(stmt,query.toString());
		final Model model = (new LogisticTrainPlus()).train(trainSource,f);
		final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(model.config.dim(),model.config.noutcomes());
		final double trainAccuracy = HelperFns.accuracy(sigmoidLoss,new ExampleRowIterable(model.config,trainSource),model.coefs);
		assertTrue(trainAccuracy>0.965);
		handle.conn.close();
		// clean up
		for(final File ci: tmpFile.listFiles()) {
			ci.delete();
		}
		tmpFile.delete();
	}
}
