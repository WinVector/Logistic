package com.winvector.logistic.mr;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import com.winvector.logistic.SigmoidLossMultinomial;
import com.winvector.opt.def.ExampleRow;
import com.winvector.opt.def.VEval;
import com.winvector.opt.def.VectorFn;
import com.winvector.opt.impl.SparseExampleRow;
import com.winvector.opt.impl.SparseSemiVec;
import com.winvector.util.BurstMap;
import com.winvector.util.LineBurster;
import com.winvector.util.SerialUtils;
import com.winvector.variables.VariableEncodings;

/**
 * some hard-coded paths, so can not run two of these simultaneously
 * @author johnmount
 *
 */
public final class MapRedFn implements VectorFn {
	private final Log log = LogFactory.getLog(MapRedFn.class);
	private static final String MRFIELDNAME = "MapRedFn.MRBlock";
	private final SigmoidLossMultinomial underlying;
	private final WritableVariableList defs;
	private final boolean useIntercept;
	private final Configuration mrConfig;
	private final Path pathIn;
	private final String tmpPrefix;

	
	public MapRedFn(final SigmoidLossMultinomial underlying, final WritableVariableList defs, final boolean useIntercept, 
			final String tmpPrefix, final Configuration mrConfig, final Path pathIn) {
		this.underlying = underlying;
		this.useIntercept = useIntercept;
		this.defs = defs;
		this.pathIn = pathIn;
		this.mrConfig = mrConfig;
		this.tmpPrefix = tmpPrefix;
	}
	
	@Override
	public int dim() {
		return underlying.dim();
	}

	public static final class JobStateDescr implements Writable {
		public SigmoidLossMultinomial underlying = null;
		public WritableVariableList defs = null;
		public boolean useIntercept = true;
		public String weightKey = null;
		public double[] x = null;
		public boolean wantGrad = false;
		public boolean wantHessian = false;
		
		@Override
		public void readFields(final DataInput in) throws IOException {
			underlying = null;
			defs = null;
			x = null;
			wantGrad = false;
			wantHessian = false;
			try {
				underlying = (SigmoidLossMultinomial)SerialUtils.readSerialiazlabeFromString(in.readUTF());
			} catch (ClassNotFoundException e) {
				throw new IOException(e.toString());
			}
			defs = new WritableVariableList();
			defs.readFields(in);
			useIntercept = in.readBoolean();
			final boolean haveWeightKey = in.readBoolean();
			if(haveWeightKey) {
				weightKey = in.readUTF();
			} else {
				weightKey = null;
			}
			x = WritableUtils.readVec(in);
			wantGrad = in.readBoolean();
			wantHessian = in.readBoolean();
			
		}

		@Override
		public void write(final DataOutput out) throws IOException {
			out.writeUTF(SerialUtils.serializableToString(underlying));
			defs.write(out);
			out.writeBoolean(useIntercept);
			out.writeBoolean(null!=weightKey);
			if(null!=weightKey) {
				out.writeUTF(weightKey);
			}
			WritableUtils.writeVec(x,out);
			out.writeBoolean(wantGrad);
			out.writeBoolean(wantHessian);			
		}
		
		@Override
		public String toString() {
			try {
				return WritableUtils.writableToString(this);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
		
		public static JobStateDescr fromString(final String s) throws IOException {
			final JobStateDescr r = new JobStateDescr();
			WritableUtils.readWritableFieldsFromString(s, r);
			return r;
		}
	}
	
	public static final class SumMapper extends Mapper<LongWritable,Text,Text,DoubleWritable> {
		private LineBurster burster = null;
		// config
		private JobStateDescr config = null;
		// derived
		private VariableEncodings defs = null;
		private Log log = null;
		private String hostDescr = null;
		// scratch
		private double[] pscratch = null;
		// result
		private long nProcessed = 0;
		private VEval accum = null;
		
		@Override
		public void setup(final Context context) throws IOException {
			// read side-channel configuration
			log = LogFactory.getLog(SumMapper.class);
			hostDescr = WritableUtils.hostDescr();			
			log.info(".setup() " + hostDescr); 
			try {
				burster = SerialUtils.readSerialiazlabeFromString(context.getConfiguration().get(MapRedScan.BURSTERSERFIELD));
			} catch (ClassNotFoundException e) {
				throw new IOException(e.toString());
			}
			config = JobStateDescr.fromString(context.getConfiguration().get(MRFIELDNAME));
			defs = new VariableEncodings(config.defs,config.useIntercept,config.weightKey);
			accum = new VEval(config.x,config.wantGrad,config.wantHessian);
			pscratch = new double[defs.noutcomes()];
			nProcessed = 0;
		}

		@Override
		public void map(final LongWritable key, final Text value, final Context context) {
			final String origStr = value.toString();
			final BurstMap parsed = burster.parse(origStr);
			if(!parsed.isEmpty()) {
				final String resStr = parsed.getAsString(config.defs.resultColumn);
				if((resStr!=null)&&(resStr.length()>0)) {
					final Integer category = defs.category(resStr.trim());
					if((category!=null)&&(category>=0)) {
						final SparseSemiVec v = defs.vector(parsed);
						final double wt = defs.weight(parsed);
						if((wt>0.0)&&(v!=null)) {
							final ExampleRow r = new SparseExampleRow(v,wt,category);
							config.underlying.addTerm(config.x, config.wantGrad, config.wantHessian, r, accum, pscratch);
							++nProcessed;
						}
					}
				}
			}
		}
		
		/**
		 * only write summing terms (fx,gx,hx) into Text,WritableDouble format
		 * @param v
		 * @param context
		 * @throws InterruptedException 
		 * @throws IOException 
		 */
		private static void writeVEvalToContext(final VEval v, final Context context) throws IOException, InterruptedException {
			if(v.fx!=0.0) {
				context.write(new Text("fx"),new DoubleWritable(v.fx));
			}
			if(v.gx!=null) {
				final int dim = v.gx.length;
				for(int i=0;i<dim;++i) {
					final double gxi = v.gx[i];
					if(gxi!=0.0) {
						context.write(new Text("gx_" + i),new DoubleWritable(gxi));
					}
				}
			}
			if(v.hx!=null) {
				final int dim = v.gx.length;
				for(int i=0;i<dim;++i) {
					for(int j=0;j<dim;++j) {
						final double hxij = v.hx[i][j];
						if(hxij!=0.0) {
							context.write(new Text("hx_" + i + "_" + j),new DoubleWritable(hxij));
						}
					}
				}
				
			}
		}
		
		public static void addVEvalFromBufferedReader(final VEval v, final BufferedReader r) throws IOException {
			String line = null;
			while((line=r.readLine())!=null) {
				final String[] flds = line.split("\t");
				if(flds.length==2) {
					final String key = flds[0];
					final double value = Double.parseDouble(flds[1]);
					switch(key.charAt(0)) {
					case 'f': {
						v.fx += value;
						break;
					}
					case 'g': {
						final String[] keyflds = key.split("_");
						final int i = Integer.parseInt(keyflds[1]);
						v.gx[i] += value;
						break;
					}
					case 'h': {
						final String[] keyflds = key.split("_");
						final int i = Integer.parseInt(keyflds[1]);
						final int j = Integer.parseInt(keyflds[2]);
						v.hx[i][j] += value;
						break;
					}
					default:
						break;
					}
				}
			}
		}
		
		@Override
		public void cleanup(final Context context) throws IOException, InterruptedException {
			writeVEvalToContext(accum,context);
			log.info("processed " + nProcessed + " on " + hostDescr); 			
			log.info(".cleanup() " + hostDescr); 
			burster = null;
			config = null;
			defs = null;
			accum = null;
		}
	}
	
	public static final class SumReducer extends Reducer<Text,DoubleWritable,Text,DoubleWritable> { // TODO: check if there is a standard summer we could use here
		@Override
		public void reduce(final Text key, final Iterable<DoubleWritable> values, final Context context) throws IOException, InterruptedException {
			double total = 0.0;
			for(final DoubleWritable vi: values) {
				total += vi.get();
			}
			if(total!=0.0) {
				context.write(new Text(key.toString()),new DoubleWritable(total));
			}
		}
	}
	
	@Override
	public VEval eval(final double[] x, final boolean wantGrad, final boolean wantHessian) {
		try {
			final String pathOutName = tmpPrefix + "_MPRedFnOut";
			final Path pathOut = new Path(pathOutName);
			final JobStateDescr conf = new JobStateDescr();
			conf.underlying = underlying;
			conf.defs = defs;
			conf.x = x;
			conf.useIntercept = useIntercept;
			conf.wantGrad = wantGrad;
			conf.wantHessian = wantHessian;
			mrConfig.set(MRFIELDNAME,conf.toString()); // prepare config for distribution
			// run the job
			final Job job = WritableUtils.newJob(mrConfig);
			job.setJarByClass(MapRedFn.class);
			job.setJobName("MapRedFnStep");
			FileInputFormat.setMaxInputSplitSize(job,4*1024*1024L);
			FileInputFormat.setMinInputSplitSize(job,4*1024L);
			FileInputFormat.addInputPath(job, pathIn);
			FileOutputFormat.setOutputPath(job, pathOut);
			job.setMapperClass(SumMapper.class);
			job.setCombinerClass(SumReducer.class);
			job.setReducerClass(SumReducer.class);
			job.setOutputKeyClass(Text.class);
			job.setOutputValueClass(DoubleWritable.class);
			job.setInputFormatClass(TextInputFormat.class);
			job.setOutputFormatClass(TextOutputFormat.class);
			job.setNumReduceTasks(1);
			final VEval r = new VEval(x,wantGrad,wantHessian);
			if(job.waitForCompletion(false)) {
				// collect results
				final FSDataInputStream fdi = pathOut.getFileSystem(mrConfig).open(new Path(pathOut,"part-r-00000"));
				final BufferedReader d = new BufferedReader(new InputStreamReader(fdi));
				SumMapper.addVEvalFromBufferedReader(r,d);
		        d.close();
			}
			// clean up
			pathOut.getFileSystem(mrConfig).delete(pathOut,true);
			return r;
		} catch (Exception ex) {
			log.error("caught: " + ex);
			throw new RuntimeException(ex);
		}
	}
	
}
