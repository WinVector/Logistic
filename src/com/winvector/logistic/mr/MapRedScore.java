package com.winvector.logistic.mr;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import com.winvector.logistic.SigmoidLossMultinomial;
import com.winvector.logistic.mr.MapRedFn.JobStateDescr;
import com.winvector.opt.def.Datum;
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;
import com.winvector.util.BurstMap;
import com.winvector.util.LineBurster;
import com.winvector.util.SerialUtils;
import com.winvector.variables.VariableEncodings;

public final class MapRedScore {
	private static final String MRFIELDNAME = "MapRedSc.MRBlock";
	public static final String IDEALHEADERFIELD = "MapRedSc.IdealHeader";
	private final SigmoidLossMultinomial underlying;
	private final WritableVariableList defs;
	private final boolean useIntercept;
	private final Configuration mrConfig;
	private final Path pathIn;

	
	public MapRedScore(final SigmoidLossMultinomial underlying, final WritableVariableList defs, final boolean useIntercept, 
			final Configuration mrConfig, final Path pathIn) {
		this.underlying = underlying;
		this.useIntercept = useIntercept;
		this.defs = defs;
		this.pathIn = pathIn;
		this.mrConfig = mrConfig;
	}
	
	
	public static final class ScoreMapper extends Mapper<LongWritable,Text,Text,Text> {
		private final NumberFormat nf = new DecimalFormat("000000000000");
		private LineBurster burster = null;
		// config
		private JobStateDescr config = null;
		// derived
		private VariableEncodings defs = null;
		private Log log = null;
		private String hostDescr = null;

		
		@Override
		public void setup(final Context context) throws IOException {
			log = LogFactory.getLog(ScoreMapper.class);
			hostDescr = WritableUtils.hostDescr();			
			log.info(".setup() " + hostDescr); 
			// read side-channel configuration
			try {
				burster = SerialUtils.readSerialiazlabeFromString(context.getConfiguration().get(MapRedScan.BURSTERSERFIELD));
			} catch (ClassNotFoundException e) {
				throw new IOException(e.toString());
			}
			config = JobStateDescr.fromString(context.getConfiguration().get(MRFIELDNAME));
			defs = new VariableEncodings(config.defs,config.useIntercept,config.weightKey);
		}

		@Override
		public void map(final LongWritable key, final Text value, final Context context) throws IOException, InterruptedException {
			final String origStr = value.toString();
			final BurstMap parsed = burster.parse(origStr);
			if(!parsed.isEmpty()) {
				final SparseSemiVec v = defs.vector(parsed);
				if(v!=null) {
					final double wt = defs.weight(parsed);
					final int catInt = -1;
					final Datum r = new SparseExampleRow(v,wt,catInt);
					final double[] pred = config.underlying.predict(config.x,r);
					final int argMax = HelperFns.argmax(pred);
					final StringBuilder b = new StringBuilder();
					b.append(defs.outcome(argMax) + "\t" + pred[argMax] + "\t");
					for(final double pi: pred) {
						b.append("" + pi + "\t");
					}
					b.append(origStr);
					context.write(new Text("Offset." + nf.format(key.get())),new Text(b.toString()));
				}
			}
		}
		
		@Override
		public void cleanup(final Context context) {
			log.info(".cleanup() " + hostDescr);
			burster = null;
			config = null;
			defs = null;
		}
	}
	
	public static final class ScoreReducer extends Reducer<Text,Text,Text,Text> {
		@Override
		public void setup(final Context context) throws IOException, InterruptedException {
			final String idealHeader = context.getConfiguration().get(MapRedScore.IDEALHEADERFIELD);
			context.write(new Text("File.Offset"),new Text(idealHeader));
		}
	}
		
	
	public boolean score(final double[] x, final Path pathOut) throws IOException, InterruptedException, ClassNotFoundException {
		final JobStateDescr conf = new JobStateDescr();
		conf.underlying = underlying;
		conf.defs = defs;
		conf.x = x;
		conf.useIntercept = useIntercept;
		conf.wantGrad = false;
		conf.wantHessian = false;
		mrConfig.set(MRFIELDNAME,conf.toString()); // prepare config for distribution
		// run the job
		final Job job = WritableUtils.newJob(mrConfig);
		job.setJarByClass(MapRedScore.class);
		job.setJobName("MapRedScoreStep");
		FileInputFormat.setMaxInputSplitSize(job,4*1024*1024L);
		FileInputFormat.setMinInputSplitSize(job,4*1024L);
		FileInputFormat.addInputPath(job, pathIn);
		FileOutputFormat.setOutputPath(job, pathOut);
		job.setMapperClass(ScoreMapper.class);
		job.setNumReduceTasks(1);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setReducerClass(ScoreReducer.class);
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		return job.waitForCompletion(false);
	}
}
