package com.winvector.logistic.demo;

import java.io.ObjectInputStream;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.winvector.Licenses;
import com.winvector.logistic.Model;
import com.winvector.logistic.SigmoidLossMultinomial;
import com.winvector.logistic.mr.MapRedAccuracy;
import com.winvector.logistic.mr.MapRedScan;
import com.winvector.logistic.mr.MapRedScore;
import com.winvector.logistic.mr.WritableUtils;
import com.winvector.logistic.mr.WritableVariableList;
import com.winvector.util.HBurster;
import com.winvector.util.LineBurster;
import com.winvector.util.SerialUtils;

/// @SuppressWarnings("deprecation") //  Tool depreciated 0.21.0 a known issue: https://issues.apache.org/jira/browse/MAPREDUCE-2084
public class MapReduceScore extends Configured implements Tool {

	@Override
	public int run(final String[] args) throws Exception {		
		if(args.length!=3) {
			final Log log = LogFactory.getLog(MapReduceScore.class);
			log.info(Licenses.licenses);
			log.fatal("use: MapReduceScore model.ser testFile resultDir");
			return -1;
		}
		final String modelFileName = args[0];
		final String testFileName = args[1];
		final String resultFileName = args[2];
		run(modelFileName,testFileName,resultFileName);
		return 0;
	}	
		
	public double run(final String modelFileName, final String testFileName, final String resultFileName) throws Exception {
		final Log log = LogFactory.getLog(MapReduceScore.class);
		final Random rand = new Random();
		final String tmpPrefix = "TMPAC_" + rand.nextLong();
		final Configuration mrConfig = getConf();
		log.info("start");
		log.info("reading model: " + modelFileName);
		final Model model;
		{
			final Path modelPath = new Path(modelFileName);
			final FSDataInputStream fdi = modelPath.getFileSystem(mrConfig).open(modelPath);
			final ObjectInputStream ois = new ObjectInputStream(fdi);
			model = (Model)ois.readObject();		
			ois.close();
		}
		log.info("model:\n" + model.config.formatSoln(model.coefs));
		final Path testFile = new Path(testFileName);
		final Path resultFile = new Path(resultFileName);
		log.info("scoring data: " + testFile);
		log.info("writing: " + resultFile);
		final SigmoidLossMultinomial underlying = new SigmoidLossMultinomial(model.config.dim(),model.config.noutcomes());
		final WritableVariableList lConfig = WritableVariableList.copy(model.config.def());
		final String headerLine = WritableUtils.readFirstLine(mrConfig,testFile);
		final Pattern sepPattern = Pattern.compile("\t");
		final LineBurster burster = new HBurster(sepPattern,headerLine,false);
		mrConfig.set(MapRedScan.BURSTERSERFIELD,SerialUtils.serializableToString(burster));
		final StringBuilder b = new StringBuilder();
		b.append("predict" + "." + model.config.def().resultColumn + "\t");
		b.append("predict" + "." + model.config.def().resultColumn + "." + "score" + "\t");
		for(int i=0;i<model.config.noutcomes();++i) {
			final String cat = model.config.outcome(i);
			b.append("predict" + "." + model.config.def().resultColumn + "." + cat + "." + "score" + "\t");
		}
		b.append(headerLine);
		mrConfig.set(MapRedScore.IDEALHEADERFIELD,b.toString());
		final MapRedScore sc = new MapRedScore(underlying,lConfig,model.config.useIntercept(),mrConfig,testFile);				
		sc.score(model.coefs,resultFile);
		final MapRedAccuracy ac = new MapRedAccuracy(underlying,lConfig,model.config.useIntercept(),tmpPrefix,mrConfig,testFile);
		final long[] testAccuracy = ac.score(model.coefs);
		final double accuracy = testAccuracy[0]/(double)testAccuracy[1];
		log.info("test accuracy: " + testAccuracy[0] + "/" + testAccuracy[1] + "\t" + accuracy);
		log.info("done");
		return accuracy;
	}
	
	// ~/Documents/workspace/hadoop-0.21.0/bin/hadoop jar ~/Documents/workspace/Logistic/WinVectorLogistic.jar logisticscore model.ser  ~/Documents/workspace/Logistic/test/com/winvector/logistic/uciCarTest.tsv scoreDir
	public static void main(final String[] args) throws Exception {
		final MapReduceScore mrl = new MapReduceScore();
		final int code = ToolRunner.run(null,mrl,args);
		if(code!=0) {
			final Log log = LogFactory.getLog(MapReduceScore.class);
			log.fatal("MapReduceScore error, return code: " + code);
			ToolRunner.printGenericCommandUsage(System.out);
		}
	}

}
