/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.util.Arrays;

/**
 * Ensures that a component under test meets minimum performance requirements, specified as a maximum expected execution
 * time per operation. Allows for typical delays caused by e.g. class loading, lazy initialization, garbage collection
 * and resizing / rehashing.
 */
public class SpeedGuard
{
	/**
	 * Default grace period = 5 s.
	 */
	private static final long DEFAULT_GRACE_PERIOD = 5000000000L;

	/**
	 * The batch size to start with.
	 */
	private static final int DEFAULT_BATCH_SIZE = 1;

	/**
	 * Target time for batches = 10 ms.
	 */
	private static final long BATCH_PERIOD = 10000000L;
	private static final int BATCH_AVERAGES = 20;

	/**
	 * @return estimated execution time of {@link System#nanoTime()} in nanoseconds
	 */
	public static long getSystemNanoTimePerformance()
	{
		long start = 0, end = 0, now = 0, count = 0;

		// measure twice to account for warmup
		for (int i = 0; i < 2; i++)
		{
			// run for 5 ms
			start = System.nanoTime();
			end = start + 5000000;
			// count how often System.nanoSeconds() can be called in that interval
			for (count = 0; now < end; count++)
				now = System.nanoTime();
		}
		return (now - start) / count;
	}

	private final long maxTimePerOperation;
	private final long gracePeriod;
	private final long[] batchAverages = new long[BATCH_AVERAGES];

	private long batchSize = DEFAULT_BATCH_SIZE;
	private long start = System.nanoTime();
	private long count;

	private int timeouts;
	private long timeoutStart;
	private long timeoutCount;

	/**
	 * Creates a new instance.
	 *
	 * @param maxTimePerOperation the maximum time per operation (i.e. between {@link #tick()} calls) in nanoseconds
	 */
	public SpeedGuard(long maxTimePerOperation)
	{
		this(maxTimePerOperation, DEFAULT_GRACE_PERIOD);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param maxTimePerOperation the maximum time per operation (i.e. between {@link #tick()} calls) in nanoseconds
	 * @param gracePeriod the grace period in which the maximum time per operation may be exceeded in nanoseconds
	 */
	public SpeedGuard(long maxTimePerOperation, long gracePeriod)
	{
		this.maxTimePerOperation = maxTimePerOperation;
		this.gracePeriod = gracePeriod;
	}

	/**
	 * Called for each operation. 
	 */
	public void tick()
	{
		// only measure time after batchSize calls to minimize performance impact 
		if (++count < batchSize)
			return;

		long now = System.nanoTime();
		long time = Math.max(1, now - start);
		// check if average execution time of the last batch exceeded the allowed maximum
		if (time > count * maxTimePerOperation)
		{
			if (timeouts == 0)
			{
				// first consecutive timeout, reset control variables
				timeoutStart = start;
				timeoutCount = 0;
				Arrays.fill(batchAverages, 0);
			}
			// in consecutive timeouts, keep track of total ticks...
			timeoutCount += count;
			// ...and average execution time of most recent batches
			batchAverages[(timeouts++ % batchAverages.length)] = time / count;
			
			// fail if consecutive timeouts exceed the grace period
			long timeoutTime = now - timeoutStart;
			if (timeoutTime > gracePeriod)
			{
				StringBuilder sb = new StringBuilder();
				sb.append("Timeout detected! The last ").append(timeoutCount).append(" operations took ").append(timeoutTime)
						.append(" ns (~").append(timeoutTime / timeoutCount).append(" ns per operation, [");
				for (long t : batchAverages)
					if (t != 0)
						sb.append(t).append(",");
				sb.setLength(sb.length() - 1);
				sb.append("] ns per operation in most recent batches)!");
				throw new RuntimeException(sb.toString());
			}
		}
		else
		{
			// no timeout, reset timeout data
			timeouts = 0;
		}
		
		// adjust batch size to ~ 10ms
		batchSize = BATCH_PERIOD * count / time;
		count = 0;
		start = now;
	}
}
