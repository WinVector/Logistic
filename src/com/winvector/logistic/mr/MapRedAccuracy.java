package com.winvector.logistic.mr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
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
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.impl.HelperFns;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;
import com.winvector.util.BurstMap;
import com.winvector.util.LineBurster;
import com.winvector.util.SerialUtils;
import com.winvector.variables.VariableEncodings;

public final class MapRedAccuracy {
	private static final String MRFIELDNAME = "MapRedAc.MRBlock";
	public static final long NGOODFIELD = 0;
	public static final long NSEENFIELD = 1;
	private final SigmoidLossMultinomial underlying;
	private final WritableVariableList defs;
	private final boolean useIntercept;
	private final Configuration mrConfig;
	private final Path pathIn;
	private final String tmpPrefix;

	
	public MapRedAccuracy(final SigmoidLossMultinomial underlying, final WritableVariableList defs, final boolean useIntercept, 
			final String tmpPrefix, final Configuration mrConfig, final Path pathIn) {
		this.underlying = underlying;
		this.useIntercept = useIntercept;
		this.defs = defs;
		this.pathIn = pathIn;
		this.mrConfig = mrConfig;
		this.tmpPrefix = tmpPrefix;
	}
	
	
	public static final class AccuracyMapper extends Mapper<LongWritable,Text,LongWritable,LongWritable> {
		private LineBurster burster = null;
		// config
		private JobStateDescr config = null;
		// derived
		private VariableEncodings defs = null;
		private Log log = null;
		private String hostDescr = null;
		// result
		private long nGood = 0;
		private long nSeen = 0;
		
		@Override
		public void setup(final Context context) throws IOException {
			log = LogFactory.getLog(AccuracyMapper.class);
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
			nGood = 0;
			nSeen = 0;
		}

		@Override
		public void map(final LongWritable key, final Text value, final Context context) {
			final String origStr = value.toString();
			final BurstMap parsed = burster.parse(origStr);
			if(!parsed.isEmpty()) {
				final SparseSemiVec v = defs.vector(parsed);
				final double wt = defs.weight(parsed);
				if((wt>0.0)&&(v!=null)) {
					int catInt = -1;
					final String resStr = parsed.getAsString(config.defs.resultColumn);
					if((resStr!=null)&&(resStr.length()>0)) {
						final Integer category = defs.category(resStr);
						if(category!=null) {
							catInt = category;
						}
					}
					final ExampleRow r = new SparseExampleRow(v,wt,catInt);
					final double[] pred = config.underlying.predict(config.x,r);
					if(catInt>=0) {
						final boolean good = (pred!=null)&&(pred.length==defs.noutcomes())&&HelperFns.isGoodPrediction(pred,r);
						if(good) {
							++nGood;
						}
						++nSeen;
					}
				}
			}
		}
		
		@Override
		public void cleanup(final Context context) throws IOException, InterruptedException {
			context.write(new LongWritable(NGOODFIELD),new LongWritable(nGood));
			context.write(new LongWritable(NSEENFIELD),new LongWritable(nSeen));
			log.info(".cleanup() " + hostDescr);
			burster = null;
			config = null;
			defs = null;
			nGood = 0;
			nSeen = 0;
		}
	}
	
	public static final class SumReducer extends Reducer<LongWritable,LongWritable,LongWritable,LongWritable> { // TODO: check if there is a standard summer we could use here
		@Override
		public void reduce(final LongWritable key, final Iterable<LongWritable> values, final Context context) throws IOException, InterruptedException {
			long total = 0;
			for(final LongWritable vi: values) {
				total += vi.get();
			}
			if(total!=0) {
				context.write(new LongWritable(key.get()),new LongWritable(total));
			}
		}
	}
	
	public long[] score(final double[] x) throws IOException, InterruptedException, ClassNotFoundException {
		final String pathOutName = tmpPrefix + "_MPRedAcOut";
		final Path pathOut = new Path(pathOutName);
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
		job.setJarByClass(MapRedAccuracy.class);
		job.setJobName("MapRedScoreStep");
		FileInputFormat.setMaxInputSplitSize(job,4*1024*1024L);
		FileInputFormat.setMinInputSplitSize(job,4*1024L);
		FileInputFormat.addInputPath(job,pathIn);
		FileOutputFormat.setOutputPath(job,pathOut);
		job.setMapperClass(AccuracyMapper.class);
		job.setCombinerClass(SumReducer.class);
		job.setReducerClass(SumReducer.class);
		job.setNumReduceTasks(1);
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(LongWritable.class);
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		final long[] r = new long[2];
		if(job.waitForCompletion(false)) {
			// collect results
			final FSDataInputStream fdi = pathOut.getFileSystem(mrConfig).open(new Path(pathOut,"part-r-00000"));
			final BufferedReader d = new BufferedReader(new InputStreamReader(fdi));
			String line = null;
			while((line=d.readLine())!=null) {
				final String[] flds = line.split("\t");
				if(flds.length==2) {
					r[Integer.parseInt(flds[0])] = Long.parseLong(flds[1]);
				}
			}
			d.close();
		}
		// clean up
		pathOut.getFileSystem(mrConfig).delete(pathOut,true);
		return r;
	}
}
