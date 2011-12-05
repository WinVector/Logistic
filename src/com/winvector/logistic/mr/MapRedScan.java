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

import com.winvector.logistic.Formula;
import com.winvector.util.BurstMap;
import com.winvector.util.LineBurster;
import com.winvector.util.SerialUtils;

public final class MapRedScan {
	public static final String BURSTERSERFIELD = "MapRedScan.BursterScan";
	private static final String FORMULAFIELD = "MapRedScan.Formula";
	private static final String DEFFIELD = "MapRedScan.DefFieldName";
	
	public static final class DefMapper extends Mapper<LongWritable,Text,LongWritable,WritableVariableList> {
		// config
		private LineBurster burster = null;
		// derived
		private Log log = null;
		private String hostDescr = null;
		// result
		private WritableVariableList accum = null;
		
		@Override
		public void setup(final Context context) throws IOException {
			log = LogFactory.getLog(DefMapper.class);
			hostDescr = WritableUtils.hostDescr();			
			log.info(".setup() " + hostDescr); 
			// read side-channel configuration
			accum = null;
			try {
				burster = SerialUtils.readSerialiazlabeFromString(context.getConfiguration().get(MapRedScan.BURSTERSERFIELD));
				final String formulaStr = context.getConfiguration().get(FORMULAFIELD);
				final Formula f = new Formula(formulaStr);
				accum = new WritableVariableList();
				accum.readyForDefTracking(f);
			} catch (Exception e) {
				throw new IOException(e.toString());
			}
		}

		@Override
		public void map(final LongWritable key, final Text value, final Context context) {
			final BurstMap parsed = burster.parse(value.toString());
			if(!parsed.isEmpty()) {
				accum.trackVariableDefsFromRow(parsed);
			}
		}
		
		@Override
		public void cleanup(final Context context) throws IOException, InterruptedException {
			context.write(new LongWritable(0),accum);
			log.info(".cleanup() " + hostDescr);
			burster = null;
			accum = null;
		}
	}
	
	public static final class DefReducer extends Reducer<LongWritable,WritableVariableList,LongWritable,WritableVariableList> {
		@Override
		public void reduce(final LongWritable key, final Iterable<WritableVariableList> values, final Context context) throws IOException, InterruptedException {
			WritableVariableList r = null;
			for(final WritableVariableList vi: values) {
				if(r==null) {
					r = WritableVariableList.copy(vi);
				} else {
					r.mergeVariableDefs(vi);
				}
			}
			if(r!=null) {
				context.write(new LongWritable(0),r);
			}
		}
	}
	
	
	private static WritableVariableList ruDefStep(final String tmpPrefix, final Configuration mrConfig, 
			final String formulaStr, final Path pathIn) throws IOException, InterruptedException, ClassNotFoundException {
		// run the job
		final Path pathOut = new Path(tmpPrefix + "_MPRedDefOut");
		// write side-channel information
		mrConfig.set(FORMULAFIELD, formulaStr);
		final Job job = WritableUtils.newJob(mrConfig);
		job.setJarByClass(MapRedScan.class);
		job.setJobName("MapRevDefStep");
		FileInputFormat.setMaxInputSplitSize(job,4*1024*1024L);
		FileInputFormat.setMinInputSplitSize(job,4*1024L);
		FileInputFormat.addInputPath(job, pathIn);
		FileOutputFormat.setOutputPath(job, pathOut);
		job.setMapperClass(DefMapper.class);
		job.setCombinerClass(DefReducer.class);
		job.setReducerClass(DefReducer.class);
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(WritableVariableList.class);
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setNumReduceTasks(1);
		WritableVariableList r = null;
		if(job.waitForCompletion(false)) {
			// collect results
			final FSDataInputStream fdi = pathOut.getFileSystem(mrConfig).open(new Path(pathOut,"part-r-00000"));
			final BufferedReader d = new BufferedReader(new InputStreamReader(fdi));
			final String line = d.readLine();
			d.close();
			final String[] flds = line.split("\t",2);
			r = WritableVariableList.fromString(flds[1]);
		}
		// clean up
		pathOut.getFileSystem(mrConfig).delete(pathOut,true);
		return r;
	}
	
	
	
	
	public static final class LevMapper extends Mapper<LongWritable,Text,LongWritable,WritableVariableList> {
		// config
		private LineBurster burster = null;
		// derived
		private Log log = null;
		private String hostDescr = null;
		// result
		private WritableVariableList accum = null;
		
		@Override
		public void setup(final Context context) throws IOException {
			log = LogFactory.getLog(LevMapper.class);
			hostDescr = WritableUtils.hostDescr();			
			log.info(".setup() " + hostDescr); 
			// read side-channel configuration
			try {
				burster = SerialUtils.readSerialiazlabeFromString(context.getConfiguration().get(MapRedScan.BURSTERSERFIELD));
			} catch (ClassNotFoundException e) {
				throw new IOException(e.toString());
			}
			accum = WritableVariableList.fromString(context.getConfiguration().get(DEFFIELD));
		}

		@Override
		public void map(final LongWritable key, final Text value, final Context context) {
			final BurstMap parsed = burster.parse(value.toString());
			if(!parsed.isEmpty()) {
				accum.trackVariableLevelsFromRow(parsed);
			}
		}
		
		@Override
		public void cleanup(final Context context) throws IOException, InterruptedException {
			context.write(new LongWritable(0),accum);
			log.info(".cleanup() " + hostDescr);
			burster = null;
			accum = null;
		}
	}
	
	public static final class LevReducer extends Reducer<LongWritable,WritableVariableList,LongWritable,WritableVariableList> {
		@Override
		public void reduce(final LongWritable key, final Iterable<WritableVariableList> values, final Context context) throws IOException, InterruptedException {
			WritableVariableList r = null;
			for(final WritableVariableList vi: values) {
				if(r==null) {
					r = WritableVariableList.copy(vi);
				} else {
					r.mergeVariableLevels(vi);
				}
			}
			if(r!=null) {
				context.write(new LongWritable(0),r);
			}
		}
	}
	
	
	private static WritableVariableList ruLevelStep(final String tmpPrefix, final Configuration mrConfig, 
			final WritableVariableList defs, final Path pathIn) throws IOException, InterruptedException, ClassNotFoundException {
		// write side-channel information
		mrConfig.set(DEFFIELD,defs.toString());
		// run the job
		final Path pathOut = new Path(tmpPrefix + "_MPRedLevelOut");
		final Job job = WritableUtils.newJob(mrConfig);
		job.setJarByClass(MapRedScan.class);
		job.setJobName("MapRevLevelStep");
		FileInputFormat.setMaxInputSplitSize(job,4*1024*1024L);
		FileInputFormat.setMinInputSplitSize(job,4*1024L);
		FileInputFormat.addInputPath(job, pathIn);
		FileOutputFormat.setOutputPath(job, pathOut);
		job.setMapperClass(LevMapper.class);
		job.setCombinerClass(LevReducer.class);
		job.setReducerClass(LevReducer.class);
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(WritableVariableList.class);
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setNumReduceTasks(1);
		WritableVariableList r = null;
		if(job.waitForCompletion(false)) {
			// collect results
			final FSDataInputStream fdi = pathOut.getFileSystem(mrConfig).open(new Path(pathOut,"part-r-00000"));
			final BufferedReader d = new BufferedReader(new InputStreamReader(fdi));
			final String line = d.readLine();
			d.close();
			final String[] flds = line.split("\t",2);
			r = WritableVariableList.fromString(flds[1]);
		}
		// clean up
		pathOut.getFileSystem(mrConfig).delete(pathOut,true);
		return r;
	}


	public static WritableVariableList initialScan(final String tmpPrefix, final Configuration mrConfig, 
			final Path trainFile, final String formulaStr) throws IOException, InterruptedException, ClassNotFoundException {
		final WritableVariableList defs = ruDefStep(tmpPrefix,mrConfig,formulaStr,trainFile);
		WritableVariableList lConfig = ruLevelStep(tmpPrefix,mrConfig,defs,trainFile);
		lConfig.trimStuckLevels();
		return lConfig;
	}
	
}
