package com.winvector.logistic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.winvector.db.DBIterable;
import com.winvector.db.DBUtil;
import com.winvector.db.DBUtil.DBHandle;
import com.winvector.opt.def.DModel;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;
import com.winvector.util.BurstMap;
import com.winvector.util.Ticker;
import com.winvector.util.TrivialReader;

public class LogisticScore {
	private static final String DATAURIKEY = "dataURI";
	private static final String DATASEP = "sep";
	private static final String DATAHDLKEY = "dataHDL";
	private static final String DATATBLKEY = "dataTBL";
	private static final String MODELKEY = "modelFile";
	private static final String RESULTFILEKEY = "resultFile";

	private static CommandLine parseCommandLine(final String[] args) throws org.apache.commons.cli.ParseException {
		final CommandLineParser clparser = new GnuParser();
		final Options cloptions = new Options();
		cloptions.addOption(MODELKEY,true,"file to read serialized model from");
		cloptions.addOption(DATAURIKEY,true,"URI to get scoring data from");
		cloptions.addOption(DATASEP,true,"(optional) data input separator");
		cloptions.addOption(DATAHDLKEY,true,"XML file to get JDBC connection to scoring data table");
		cloptions.addOption(DATATBLKEY,true,"table to use from database for scoring data");
		cloptions.addOption(RESULTFILEKEY,true,"file to write results to");
		for(final String optkey: new String[] {MODELKEY, RESULTFILEKEY} ) {
			cloptions.getOption(optkey).setRequired(true);
		}
		final HelpFormatter hf = new HelpFormatter();
		final CommandLine cl = clparser.parse(cloptions, args);
		if((cl.getOptionValue(DATAURIKEY)==null)==(cl.getOptionValue(DATAHDLKEY)==null)) {
			hf.printHelp("com.winvector.logistic.LogisticScore", cloptions);
			throw new org.apache.commons.cli.ParseException("Must set exacty one of --" + DATAURIKEY + " or --" + DATAHDLKEY);
		}
		if((cl.getOptionValue(DATAHDLKEY)!=null)&&(cl.getOptionValue(DATATBLKEY)==null)) {
			hf.printHelp("com.winvector.logistic.LogisticScore", cloptions);
			throw new org.apache.commons.cli.ParseException("If --" + DATAHDLKEY + " is set then must specify a table with --" + DATATBLKEY);
		}
		return cl;
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @throws ClassNotFoundException 
	 * @throws URISyntaxException 
	 * @throws ParseException 
	 * @throws SQLException 
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException, URISyntaxException, ParseException, SQLException {
		final Log log = LogFactory.getLog(LogisticScore.class);
		log.info("start LogisticScore\t" + new Date());
		final CommandLine cl = parseCommandLine(args);
		final File modelFile = new File(cl.getOptionValue(MODELKEY));
		final File resultFile = new File(cl.getOptionValue(RESULTFILEKEY));
		log.info("cwd: " + new File(".").getAbsolutePath());
		log.info("reading model: " + modelFile.getAbsolutePath());
		final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
		final Model model = (Model)ois.readObject();		
		ois.close();
		log.info("model:\n" + model.config.formatSoln(model.coefs));
		DBHandle handle = null;
		Statement stmt = null;
		final Iterable<BurstMap> testSource;
		if(cl.getOptionValue(DATAURIKEY)!=null) {
			final URI trainURI = new URI(cl.getOptionValue(DATAURIKEY));
			log.info(" source URI: " + trainURI.toString());
			char sep = '\t';
			if(cl.getOptionValue(DATASEP)!=null) {
				sep = cl.getOptionValue(DATASEP).charAt(0);
			}
			testSource = new TrivialReader(trainURI,sep,null,false,null, false);
		} else {
			final URI dbProps = new URI(cl.getOptionValue(DATAHDLKEY));
			final String dbTable = cl.getOptionValue(DATATBLKEY);
			handle = DBUtil.buildConnection(dbProps, true);
			stmt = handle.conn.createStatement();
			log.info(" source db: " + handle);
			testSource = DBIterable.buildSource(handle, stmt, dbTable, model.origFormula.allTerms());
			log.info(" query: " + testSource);
		}
		log.info("scoring data: " + testSource);
		score(model,testSource,resultFile);
		if(null!=stmt) {
			stmt.close();
			stmt = null;
		}
		if(null!=handle) {
			handle.conn.close();
			handle = null;
		}
		log.info("done LogisticScore\t" + new Date());
	}
	
	// can load into DB and get marginals with SQL like: select MODEL_CHOSEN_OUTCOME,RATING,COUNT(1) from scored1 group by MODEL_CHOSEN_OUTCOME,RATING
	public static double score(final Model model, final Iterable<BurstMap> testSource, final File resultFile) throws FileNotFoundException, IOException, ClassNotFoundException {
		final Log log = LogFactory.getLog(LogisticScore.class);
		final DModel<ExampleRow> sigmoidLoss = new SigmoidLossMultinomial(model.config.dim(),model.config.noutcomes());
		final PrintStream p;
		if(null!=resultFile) {
			p = new PrintStream(new FileOutputStream(resultFile));
		} else {
			p = null;
		}
		ArrayList<String> headerFlds = null;
		long nToCompare = 0;
		long nRight = 0;
		long[][] confusionMatrix = new long[model.config.noutcomes()][model.config.noutcomes()];
		final Ticker ticker = new Ticker("LogisticScore");
		for(final BurstMap row: testSource) {
			ticker.tick();
			if(null==headerFlds) {
				headerFlds = new ArrayList<String>(row.keySet());
				if(null!=p) {
					for(int i=0;i<model.config.noutcomes();++i) {
						final String cat = model.config.outcome(i);
						if(i>0) {
							p.print("\t");
						}
						p.print("model.predict" + "." + model.config.def().resultColumn + "." + cat);
					}
					p.print("\t" + "model.chosen.Outcome");
					p.print("\t" + "model.predict.Outcome");
					for(final String fldi: headerFlds) {
						p.print("\t" + fldi);
					}
					p.println();
				}
			}
			int catInt = -1;
			final String resStr = row.getAsString(model.config.def().resultColumn);
			if(resStr!=null) {
				final Integer category = model.config.category(resStr);
				if(category!=null) {
					catInt = category;
				}
			}
			final SparseSemiVec vector = model.config.vector(row);
			if(null!=vector) {
				final ExampleRow ei = new SparseExampleRow(vector,model.config.weight(row),catInt);
				final double[] pred = sigmoidLoss.predict(model.coefs,ei);
				final int argMax = HelperFns.argmax(pred);
				if(null!=p) {
					for(int i=0;i<model.config.noutcomes();++i) { // non-empty list
						if(i!=0) {
							p.print("\t");
						}
						p.print(pred[i]);
					}
					p.print("\t" + model.config.outcome(argMax));
					p.print("\t" + pred[argMax]);
					for(final String hiK: headerFlds) {
						String value = row.getAsString(hiK);
						if(value==null) {
							value = "";
						}
						p.print("\t" + value);
					}
					p.println();
				}
				if(catInt>=0) {
					++nToCompare;
					final boolean good = (pred!=null)&&(catInt==argMax);
					if(good) {
						++nRight;
					}
					confusionMatrix[argMax][catInt] += 1;
				}
			}
		}
		if(null!=p) {
			p.close();
		}
		final double testAccuracy = nRight/(double)nToCompare;
		log.info("scored accuracy: " + testAccuracy);
		if(null!=resultFile) {
			log.info("wrote: " + resultFile.getAbsolutePath());
		}
		final StringBuilder cmrep = new StringBuilder();
		cmrep.append("Consfusion matrix:\n");
		cmrep.append("prediction");
		for(int correctI = 0;correctI< model.config.noutcomes();++correctI) {
			cmrep.append("\t" + "actual");
		}
		cmrep.append("\n");
		cmrep.append("index:outcome");
		for(int correctI = 0;correctI< model.config.noutcomes();++correctI) {
			cmrep.append("\t" + correctI + ":" + model.config.outcome(correctI));
		}
		cmrep.append("\n");
		for(int predI = 0;predI< model.config.noutcomes();++predI) {
			cmrep.append("" + predI + ":" + model.config.outcome(predI));
			for(int correctI = 0;correctI< model.config.noutcomes();++correctI) {
				cmrep.append("\t" + confusionMatrix[predI][correctI]);
			}
			cmrep.append("\n");
		}
		log.info(cmrep.toString());
		return testAccuracy;
	}

}
