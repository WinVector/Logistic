package com.winvector.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public final class Ticker {
	private final Log log = LogFactory.getLog(Ticker.class);
	private final String logString;
	private final long checkIntervalMillis = 200L;
	private final long reportIntervalMillis = 60000L;
	private long startTimeMillis;
	private long nTicks;
	private long nextCheckTick;
	private long nextReportTimeMillis;
	
	public Ticker(String logString) {
		this.logString = logString;
		start();
	}
	
	public void start() {
		startTimeMillis = System.currentTimeMillis();
		nTicks = 0;
		nextCheckTick = 0;
		nextReportTimeMillis = startTimeMillis - (startTimeMillis%reportIntervalMillis) + reportIntervalMillis;
		if(nextReportTimeMillis-startTimeMillis<reportIntervalMillis/2) {
			nextReportTimeMillis += reportIntervalMillis;
		}
	}

	public void tick() {
		++nTicks;
		if(nTicks>=nextCheckTick) {
			final long nowMillis = System.currentTimeMillis();
			final double ticksPerMilli = nTicks/(double)Math.max(1L,nowMillis-startTimeMillis);
			final long ticksUntilCheck = Math.max(1L,(long)Math.ceil(checkIntervalMillis*ticksPerMilli));
			nextCheckTick = nTicks + ticksUntilCheck;
			if(nowMillis>=nextReportTimeMillis) {
				// report
				log.info(logString + " tick: " + nTicks + ", " + ticksPerMilli + " ticks/milli");
				// set next report time
				nextReportTimeMillis += reportIntervalMillis*(1+(nowMillis-nextReportTimeMillis)/reportIntervalMillis);
			}
		}
	}
}
