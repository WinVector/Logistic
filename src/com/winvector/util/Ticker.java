package com.winvector.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public final class Ticker {
	private final Log log = LogFactory.getLog(Ticker.class);
	private final long checkIntervalMillis = 1000L;
	private final long reportIntervalMillis = 10000L;
	private long startTimeMillis;
	private long nTicks;
	private long nextCheckTick;
	private long nextReportTimeMillis;
	
	public Ticker() {
		start();
	}
	
	public void start() {
		startTimeMillis = System.currentTimeMillis();
		nTicks = 0;
		nextCheckTick = 0;
		nextReportTimeMillis = startTimeMillis - (startTimeMillis%reportIntervalMillis) + reportIntervalMillis;
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
				log.info("tick: " + nTicks + ", " + ticksPerMilli + " ticks/milli");
				// set next report time
				nextReportTimeMillis += reportIntervalMillis*(1+(nowMillis-nextReportTimeMillis)/reportIntervalMillis);
			}
		}
	}
}
