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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;

import com.winvector.db.DBIterable;
import com.winvector.db.DBUtil;
import com.winvector.db.DBUtil.DBHandle;
import com.winvector.db.LoadTable;
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
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;
import com.winvector.opt.impl.SparseExampleRow;
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
		final Iterator<BurstMap> it = new TrivialIterator(new LineNumberReader(new InputStreamReader(is)),"\t",true,"res:"+resourceName);
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
			final LinearContribution<T> sigmoidLoss, final double[] x) {
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
		final Iterable<BurstMap> trainSource = TestLRPath.readBurstFromResource("com/winvector/logistic/uciCarTrain.tsv");
		final String formulaStr = "rating ~ buying + maintenance + doors + persons + lug_boot + safety";
		final Formula f = new Formula(formulaStr);
		final boolean useIntercept = true;
		final PrimaVariableInfo def = LogisticTrain.buildVariableDefs(f,trainSource);
		final VariableEncodings adapter = new VariableEncodings(def,useIntercept,null);
		final Iterable<ExampleRow> asTrain = new ExampleRowIterable(adapter,trainSource);
		final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(adapter.dim(),adapter.noutcomes());
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow,ExampleRow>(sigmoidLoss,asTrain),0.1);
		final VectorOptimizer nwt = new Newton();
		final VEval opt = nwt.maximize(sl,null,10);
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
		TestLRPath.copyResourceToFile("com/winvector/logistic/uciCarTrain.tsv",trainFile);
		final TrivialReader trainSource = new TrivialReader(trainFile.toURI(),'\t',null,null, false);
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
		final Iterable<BurstMap> source = TestLRPath.readBurstFromResource("com/winvector/logistic/uciCarTrain.tsv");
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
		final Model model = (new LogisticTrain()).train(trainSource,f);
		final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(model.config.dim(),model.config.noutcomes());
		final double trainAccuracy = HelperFns.accuracy(sigmoidLoss,new ExampleRowIterable(model.config,trainSource),model.coefs);
		assertTrue(trainAccuracy>0.968);
		handle.conn.close();
		// clean up
		for(final File ci: tmpFile.listFiles()) {
			ci.delete();
		}
		tmpFile.delete();
	}
}
