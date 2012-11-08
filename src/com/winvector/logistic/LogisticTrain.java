package com.winvector.logistic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.db.DBIterable;
import com.winvector.db.DBUtil;
import com.winvector.db.DBUtil.DBHandle;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.LinUtil;
import com.winvector.opt.def.LinearContribution;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.def.VectorOptimizer;
import com.winvector.opt.impl.DataFn;
import com.winvector.opt.impl.ExampleRowIterable;
import com.winvector.opt.impl.Newton;
import com.winvector.opt.impl.NormPenalty;
import com.winvector.util.BurstMap;
import com.winvector.util.Ticker;
import com.winvector.util.TrivialReader;
import com.winvector.variables.PrimaVariableInfo;
import com.winvector.variables.VariableEncodings;

public class LogisticTrain {
	
	public static PrimaVariableInfo buildVariableDefs(final Formula f, final Iterable<BurstMap> source) {
		// pass to get numeric columns and categorical columns
		final Log log = LogFactory.getLog(LogisticTrain.class);
		final PrimaVariableInfo def = new PrimaVariableInfo();
		def.readyForDefTracking(f);
		// not going to parallelize this as it it is cheaper than scans that evaluate model probabilities.
		log.info("start variable def scan 1/2");
		final Ticker ticker = new Ticker("build variable defs");
		for (BurstMap row : source) {
			ticker.tick();
			def.trackVariableDefsFromRow(row);
		}
		log.info("start variable def scan 2/2");
		ticker.start();
		// pass to get levels of categorical variables
		for(BurstMap row: source) {
			ticker.tick();
			def.trackVariableLevelsFromRow(row);
		}
		log.info("done variable def scans");
		def.trimStuckLevels();
		return def;
	}
	
	public static VariableEncodings buildAdpater(final Formula f, final String weightKey,
			final Iterable<BurstMap> source) {
		final PrimaVariableInfo def = buildVariableDefs(f,source);
		return new VariableEncodings(def,f.useIntercept,weightKey);
	}

	private static final String TRAINURIKEY = "trainURI";
	private static final String TRAINSEP = "sep";
	private static final String TRAINHDLKEY = "trainHDL";
	private static final String TRAINTBLKEY = "trainTBL";
	private static final String MEMKEY = "inmemory";
	private static final String FORMULAKEY = "formula";
	private static final String WEIGHTKEY = "weights";
	private static final String RESULTSERKEY = "resultSer";
	private static final String RESULTTSVKEY = "resultTSV";
	private static final String TRAINCLASSKEY = "trainClass";

	private static CommandLine parseCommandLine(final String[] args) throws org.apache.commons.cli.ParseException {
		final CommandLineParser clparser = new GnuParser();
		final Options cloptions = new Options();
		cloptions.addOption(TRAINURIKEY,true,"URI to get training TSV data from");
		cloptions.addOption(TRAINSEP,true,"(optional) training data input separator");
		cloptions.addOption(TRAINHDLKEY,true,"XML file to get JDBC connection to training data table");
		cloptions.addOption(TRAINTBLKEY,true,"table to use from database for training data");
		cloptions.addOption(MEMKEY, false, "(optional) if set data is held in memory during training");
		cloptions.addOption(FORMULAKEY,true,"formula to fit");
		cloptions.addOption(WEIGHTKEY,true,"(optional) symbol to user for weights");
		cloptions.addOption(RESULTSERKEY,true,"(optional) file to write seriazlized results to");
		cloptions.addOption(RESULTTSVKEY,true,"(optional) file to write TSV results to");
		cloptions.addOption(TRAINCLASSKEY,true,"(optional) alternate class to use for training");
		cloptions.getOption(FORMULAKEY).setRequired(true);
		final CommandLine cl = clparser.parse(cloptions, args);
		final HelpFormatter hf = new HelpFormatter();
		if((cl.getOptionValue(TRAINURIKEY)==null)==(cl.getOptionValue(TRAINHDLKEY)==null)) {
			hf.printHelp("com.winvector.logistic.LogisticTrain", cloptions);
			throw new org.apache.commons.cli.ParseException("Must set exacty one of --" + TRAINURIKEY + " or --" + TRAINHDLKEY);
		}
		if((cl.getOptionValue(TRAINHDLKEY)!=null)&&(cl.getOptionValue(TRAINTBLKEY)==null)) {
			hf.printHelp("com.winvector.logistic.LogisticTrain", cloptions);
			throw new org.apache.commons.cli.ParseException("If --" + TRAINHDLKEY + " is set then must specify a table with --" + TRAINTBLKEY);
		}
		return cl;
	}
	
	private static File valueAsFile(final CommandLine cl, final String key) {
		final String v = cl.getOptionValue(key);
		if(v!=null) {
			return new File(v);
		} else {
			return null;
		}
	}
	
	/**
	 * > logisticModel <- glm(rating!='unacc' ~ buying + maintinance + doors + persons + lug_boot +safety,family=binomial(link = "logit"),data=CarData)
	 * > table <- table(CarData$rating!='unacc',predict(logisticModel,type='response')>=0.5)
	 * > (table[1,1]+table[2,2])/(table[1,1]+table[1,2] + table[2,1] +table[2,2]) 
	 * [1] 0.9560185
	 *  
	 *   We get identical accuracy with epsilon 0, and 0.9554 with epsilon=0.1 (and much smaller coefs)
	 *   
	 *   
	 *   
	 *   Car Data example:
	 *   
	 *    > CarData <- read.table('~/Documents/work/Greenplum/ML1/UCIexamples/Car/car.data.txt',sep=',',header=T)
 	 * > CarData$rating <- CarData$rating!='unacc'
 	 * > inTrain <- runif(dim(CarData)[1])>=0.5
 	 * > CarDataTrain <- CarData[inTrain,]
 	 * > CarDataTest <- CarData[!inTrain,]
 	 * > logisticModel <- glm(rating ~ buying + maintinance + doors + persons + lug_boot +safety,family=binomial(link = "logit"),data=CarDataTrain)
 	 * > tableTrain <- table(CarDataTrain$rating,predict(logisticModel,type='response')>=0.5)
 	 * > (tableTrain[1,1]+tableTrain[2,2])/(tableTrain[1,1]+tableTrain[1,2] + tableTrain[2,1] +tableTrain[2,2])
 	 * [1] 0.968218
 	 * > tableTest <- table(CarDataTest$rating,predict(logisticModel,type='response',newdata=CarDataTest)>=0.5)
 	 * > (tableTest[1,1]+tableTest[2,2])/(tableTest[1,1]+tableTest[1,2] + tableTest[2,1] +tableTest[2,2])
 	 * [1] 0.9456907
 	 * > write.table(CarDataTrain,'~/Documents/workspace/Logistic/test/com/winvector/logistic/uciCarTrain.tsv',sep='\t',quote=F,col.names=T,row.names=F)
 	 * > write.table(CarDataTest,'~/Documents/workspace/Logistic/test/com/winvector/logistic/uciCarTest.tsv',sep='\t',quote=F,col.names=T,row.names=F)
 	 * 
 	 * > summary(logisticModel)
 	 * 
 	 * Call:
 	 * glm(formula = rating ~ buying + maintinance + doors + persons + 
 	 *     lug_boot + safety, family = binomial(link = "logit"), data = CarDataTrain)
 	 * 
 	 * Deviance Residuals: 
 	 *        Min          1Q      Median          3Q         Max  
 	 * -3.529e+00  -9.506e-06  -2.107e-08   1.118e-02   2.052e+00  
 	 * 
 	 * Coefficients:
 	 *                   Estimate Std. Error z value Pr(>|z|)    
 	 * (Intercept)       -27.9846  1732.8261  -0.016 0.987115    
 	 * buyinglow           6.0503     1.0137   5.968 2.40e-09 ***
 	 * buyingmed           4.9091     0.8149   6.024 1.70e-09 ***
 	 * buyingvhigh        -1.8225     0.5636  -3.234 0.001221 ** 
 	 * maintinancelow      2.9505     0.7094   4.159 3.20e-05 ***
 	 * maintinancemed      2.6507     0.6923   3.829 0.000129 ***
 	 * maintinancevhigh   -4.0775     0.7287  -5.595 2.20e-08 ***
 	 * doors3              1.8079     0.6375   2.836 0.004567 ** 
 	 * doors4              2.0369     0.6738   3.023 0.002503 ** 
 	 * doors5more          2.2269     0.6277   3.548 0.000388 ***
 	 * persons4           31.1007  1732.8262   0.018 0.985680    
 	 * personsmore        30.2984  1732.8262   0.017 0.986050    
 	 * lug_bootmed        -2.0957     0.5967  -3.512 0.000445 ***
 	 * lug_bootsmall      -5.0905     0.7862  -6.475 9.48e-11 ***
 	 * safetylow         -31.6949  1780.9178  -0.018 0.985801    
 	 * safetymed          -3.1415     0.5730  -5.482 4.20e-08 ***
 	 * ---
 	 * Signif. codes:  0 Ô***Õ 0.001 Ô**Õ 0.01 Ô*Õ 0.05 Ô.Õ 0.1 Ô Õ 1 
 	 * 
 	 * (Dispersion parameter for binomial family taken to be 1)
 	 * 
 	 *     Null deviance: 1063.68  on 880  degrees of freedom
 	 * Residual deviance:  148.55  on 865  degrees of freedom
 	 * AIC: 180.55
 	 * 
 	 * Number of Fisher Scoring iterations: 21
 	 * 
 	 * 
 	 * 
 	 * and our model (eplsion=0.1)]
 	 * start	Mon Nov 22 17:52:39 PST 2010
 	 * done training	Mon Nov 22 17:52:40 PST 2010
 	 * 
 	 * variable	kind	level	value
 	 * 	Intercept		-1.8591242994598283
 	 * buying	Categoricial	high	-1.8873447940775325
 	 * buying	Categoricial	low	1.8058297772088103
 	 * buying	Categoricial	med	1.2854138009554246
 	 * buying	Categoricial	vhigh	-3.063023083546262
 	 * doors	Categoricial	2	-1.3553226266984348
 	 * doors	Categoricial	3	-0.3633894398537312
 	 * doors	Categoricial	4	-0.1371757583347494
 	 * doors	Categoricial	5more	-0.0032364745727181538
 	 * lug_boot	Categoricial	big	0.8909475789578385
 	 * lug_boot	Categoricial	med	-0.39543218643006367
 	 * lug_boot	Categoricial	small	-2.354639691987332
 	 * maintinance	Categoricial	high	-0.6922730615814465
 	 * maintinance	Categoricial	low	1.1651037650716491
 	 * maintinance	Categoricial	med	1.0369906933425383
 	 * maintinance	Categoricial	vhigh	-3.3689456962923217
 	 * persons	Categoricial	2	-7.634135402486503
 	 * persons	Categoricial	4	3.1685491612643233
 	 * persons	Categoricial	more	2.6064619417626824
 	 * safety	Categoricial	high	3.8128922051914707
 	 * safety	Categoricial	low	-7.519660582472144
 	 * safety	Categoricial	med	1.847644077821258
 	 * 
 	 * train accuracy: 0.9693530079455165
 	 * test accuracy: 0.9456906729634003
 	 * Math.log(maxdouble): 709.782712893384	6.564958885017789
	 * 
	 * @param args
	 * @throws IOException
	 * @throws ParseException 
	 * @throws URISyntaxException 
	 * @throws org.apache.commons.cli.ParseException 
	 * @throws SQLException 
	 * @throws ClassNotFoundException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public static void main(final String[] args) throws IOException, ParseException, URISyntaxException, org.apache.commons.cli.ParseException, SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		final Log log = LogFactory.getLog(LogisticTrain.class);
		log.info("start LogisticTrain\t" + new Date());
		final CommandLine cl = parseCommandLine(args);
		final String formulaStr = cl.getOptionValue(FORMULAKEY);       // example: rating ~ buying + maintinance + doors + persons + lug_boot + safety
		final String weightKey = cl.getOptionValue(WEIGHTKEY);
		final Formula f = new Formula(formulaStr);
		final boolean memorize = cl.hasOption(MEMKEY);
		DBHandle handle = null;
		Statement stmt = null;
		final Iterable<BurstMap> trainSource;
		final File resultFileSer = valueAsFile(cl,RESULTSERKEY);
		final File resultFileTSV = valueAsFile(cl,RESULTTSVKEY);
		{
			final Iterable<BurstMap> origSource;
			if(cl.getOptionValue(TRAINURIKEY)!=null) {
				char sep = '\t';
				if(cl.getOptionValue(TRAINSEP)!=null) {
					sep = cl.getOptionValue(TRAINSEP).charAt(0);
				}
				final URI trainURI = new URI(cl.getOptionValue(TRAINURIKEY));
				log.info(" source URI: " + trainURI.toString());
				origSource = new TrivialReader(trainURI,sep,null,false,null,memorize);
			} else {
				final URI dbProps = new URI(cl.getOptionValue(TRAINHDLKEY));
				final String dbTable = cl.getOptionValue(TRAINTBLKEY);
				handle = DBUtil.buildConnection(dbProps, true);
				stmt = handle.conn.createStatement();
				log.info(" source db: " + handle);
				origSource = DBIterable.buildSource(handle, stmt, dbTable, f.allTerms()); // TODO: deep intern db sources also
				log.info(" query: " + origSource);
			}
			if(memorize) {
				final Ticker ticker = new Ticker("LogisticTrain-memorize data");
				final ArrayList<BurstMap> list = new ArrayList<BurstMap>();
				for(final BurstMap row: origSource) {
					ticker.tick();
					list.add(row);
				}
				trainSource = list;
				if(null!=stmt) {
					stmt.close();
					stmt = null;
				}
				if(null!=handle) {
					handle.conn.close();
					handle = null;
				}
			} else {
				trainSource = origSource;
			}
		}
		log.info("formula: " + f);
		final LogisticTrain trainer;
		if(cl.getOptionValue(TRAINCLASSKEY)!=null) {
			trainer = (LogisticTrain)Class.forName(cl.getOptionValue(TRAINCLASSKEY)).newInstance();
		} else {
			trainer = new LogisticTrain();
		}
		log.info("trainer: " + trainer.getClass());
		trainer.run(trainSource,f,weightKey,resultFileSer,resultFileTSV);
		if(null!=stmt) {
			stmt.close();
			stmt = null;
		}
		if(null!=handle) {
			handle.conn.close();
			handle = null;
		}
		log.info("done LogisticTrain\t" + new Date());
	}

	public Model train(final Iterable<BurstMap> trainSource, final Formula f, final String weightKey) {
		final Log log = LogFactory.getLog(LogisticTrain.class);
		final VariableEncodings adapter = buildAdpater(f,weightKey,trainSource);
		final Iterable<ExampleRow> asTrain = new ExampleRowIterable(adapter,trainSource);
		final LinearContribution<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(adapter.dim(),adapter.noutcomes());
		final VectorFn sl = NormPenalty.addPenalty(new DataFn<ExampleRow>(sigmoidLoss,asTrain),1.0e-5,adapter.adaptions);
		final VectorOptimizer nwt = new Newton();
		final VEval opt = nwt.maximize(sl,null,10);
		log.info("done training");
		log.info("soln vector: " + LinUtil.toString(opt.x));
		log.info("soln details:\n" + adapter.formatSoln(opt.x));
		final Model model = new Model();
		model.config = adapter;
		model.coefs = opt.x;
		model.origFormula = f;
		return model;
	}
	
	
	public final void run(final Iterable<BurstMap> trainSource, final Formula f, final String weightKey,
			final File resultFileSer, final File resultFileTSV) throws IOException, ParseException, ClassNotFoundException {
		final Log log = LogFactory.getLog(this.getClass());
		final Model model = train(trainSource,f,weightKey);
		LogisticScore.score(model, trainSource, null);
		if(resultFileSer!=null) {
			log.info("writing " + resultFileSer.getAbsolutePath());
			final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(resultFileSer));
			oos.writeObject(model);
			oos.close();
		}
		if(resultFileTSV!=null) {
			log.info("writing " + resultFileTSV.getAbsolutePath());
			final PrintStream p = new PrintStream(new FileOutputStream(resultFileTSV));
			p.println(model.config.formatSoln(model.coefs));
			p.close();
		}
	}
	
}
