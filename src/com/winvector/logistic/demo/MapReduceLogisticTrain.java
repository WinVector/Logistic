package com.winvector.logistic.demo;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.winvector.Licenses;
import com.winvector.logistic.Formula;
import com.winvector.logistic.Model;
import com.winvector.logistic.SigmoidLossMultinomial;
import com.winvector.logistic.mr.MapRedAccuracy;
import com.winvector.logistic.mr.MapRedFn;
import com.winvector.logistic.mr.MapRedScan;
import com.winvector.logistic.mr.WritableUtils;
import com.winvector.logistic.mr.WritableVariableList;
import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;
import com.winvector.opt.impl.SumFn;
import com.winvector.util.HBurster;
import com.winvector.util.LineBurster;
import com.winvector.util.SerialUtils;
import com.winvector.variables.VariableEncodings;

/// @SuppressWarnings("deprecation") //  Tool depreciated 0.21.0 a known issue: https://issues.apache.org/jira/browse/MAPREDUCE-2084
public class MapReduceLogisticTrain extends Configured implements Tool {

	@Override
	public int run(final String[] args) throws Exception {
		if(args.length!=3) {
			final Log log = LogFactory.getLog(MapReduceLogisticTrain.class);
			log.info(Licenses.licenses);
			log.fatal("use: MapReduceLogisticTrain trainFile formula resultFile");
			return -1;
		}
		final String trainFileName = args[0]; // file with input data in name=value pairs separated by tabs
		final String formulaStr = args[1]; // name of variable we expect to predict
		final String resultFileName = args[2];
		run(trainFileName,formulaStr,null,resultFileName,5);
		return 0;
	}
		
	public double run(final String trainFileName, final String formulaStr, final String weightKey,
			final String resultFileName, final int maxNewtonRounds) throws Exception {
		final Log log = LogFactory.getLog(MapReduceLogisticTrain.class);
		log.info("start");
		final Formula formula = new Formula(formulaStr); // force an early parse error if wrong
		final Random rand = new Random();
		final String tmpPrefix = "TMPLR_" + rand.nextLong(); 
		final Configuration mrConfig = getConf();
		final Path trainFile = new Path(trainFileName);
		final Path resultFile = new Path(resultFileName);
		final String headerLine = WritableUtils.readFirstLine(mrConfig,trainFile);
		final Pattern sepPattern = Pattern.compile("\t");
		final LineBurster burster = new HBurster(sepPattern,headerLine,false);
		mrConfig.set(MapRedScan.BURSTERSERFIELD,SerialUtils.serializableToString(burster));
		final WritableVariableList lConfig = MapRedScan.initialScan(tmpPrefix,mrConfig,
				trainFile,formulaStr);
		log.info("formula:\t" + formulaStr + "\n" + lConfig.formatState());
		final VariableEncodings defs = new VariableEncodings(lConfig,true,weightKey);
		//final WritableSigmoidLossBinomial underlying = new WritableSigmoidLossBinomial(defs.dim());
		final SigmoidLossMultinomial underlying = new SigmoidLossMultinomial(defs.dim(),defs.noutcomes());
		final MapRedFn f = new MapRedFn(underlying,lConfig,defs.useIntercept(),
				tmpPrefix,mrConfig,trainFile);
		final ArrayList<VectorFn> fns = new ArrayList<VectorFn>();
		fns.add(f);
		fns.add(new NormPenalty(f.dim(),1.0e-5,defs.adaptions));
		final VectorFn sl = new SumFn(fns); 
		final VectorOptimizer nwt = new Newton();
		final VEval opt = nwt.maximize(sl,null,maxNewtonRounds);
		log.info("done training");
		log.info("soln vector:\n" + LinUtil.toString(opt.x));
		log.info("soln details:\n" + defs.formatSoln(opt.x));
		{
			final Model model = new Model();
			model.config = defs;
			model.coefs = opt.x;
			model.origFormula = formula;
			log.info("writing " + resultFile);
			final FSDataOutputStream fdo = resultFile.getFileSystem(mrConfig).create(resultFile,true);
			final ObjectOutputStream oos = new ObjectOutputStream(fdo);
			oos.writeObject(model);
			oos.close();
		}
		final MapRedAccuracy sc = new MapRedAccuracy(underlying,lConfig,defs.useIntercept(),
				tmpPrefix,mrConfig,trainFile);
		final long[] trainAccuracy = sc.score(opt.x);
		final double accuracy = trainAccuracy[0]/(double)trainAccuracy[1];
		log.info("train accuracy: " + trainAccuracy[0] + "/" + trainAccuracy[1] + "\t" + accuracy);
		return accuracy;
	}
	
	
	// ~/Documents/workspace/hadoop-0.21.0/bin/hadoop jar ~/Documents/workspace/Logistic/WinVectorLogistic.jar logistictrain  ~/Documents/workspace/Logistic/test/com/winvector/logistic/uciCarTrain.tsv "rating ~ buying + maintenance + doors + persons + lug_boot + safety" model.ser
	public static void main(final String[] args) throws Exception {
		final MapReduceLogisticTrain mrl = new MapReduceLogisticTrain();
		final int code = ToolRunner.run(null,mrl,args);
		if(code!=0) {
			final Log log = LogFactory.getLog(MapReduceLogisticTrain.class);
			log.fatal("MapReduceLogistic error, return code: " + code);
			ToolRunner.printGenericCommandUsage(System.out);
		}
	}

}
