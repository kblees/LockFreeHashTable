/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import static org.kblees.hashtable.BenchUtils.*;

import org.kblees.hashtable.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;
import org.openjdk.jmh.runner.RunnerException;

public class IntSetContainsBenchmark extends IntSetBenchmark
{
	@Param({ "0", "50", "100" })
	int missPercent;

	int[] lookup;

	MinIntSet set;

	@Setup(Level.Trial)
	public void setupBenchmark()
	{
		int sz = (int) parseScaledLong(size);
		int miss = (int) ((long) sz * (long) missPercent / 100L);

		int[] keys = generateInts(sz + miss, randomKeys);
		insert = shuffleCopy(keys, 0, sz);
		lookup = shuffleCopy(keys, miss, sz + miss);
	}

	@State(Scope.Thread)
	public static class ThreadState extends RoundRobinCounter
	{
		@Setup(Level.Iteration)
		public void setup(IntSetContainsBenchmark benchmark, ThreadParams threads)
		{
			init(0, benchmark.lookup.length, threads.getThreadCount(), threads.getThreadIndex());
		}
	}

	@Setup(Level.Iteration)
	public void setupIteration(BenchmarkParams params)
	{
		// add keys to set
		set = type.create(insert.length);
		SpeedGuard guard = new SpeedGuard(10000);
		for (int key : insert)
		{
			set.add(key);
			guard.tick();
		}
	}

	@Benchmark
	public boolean contains(ThreadState state)
	{
		return set.contains(lookup[state.nextIndex()]);
	}

	public static void main(String[] args) throws RunnerException
	{
		run(IntSetContainsBenchmark.class, 8, false, false);
	}
}
