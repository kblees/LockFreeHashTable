/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import static org.kblees.hashtable.BenchUtils.*;

import java.util.*;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;
import org.openjdk.jmh.runner.RunnerException;

/**
 * Tests contains / get performance of hash table implementations.
 */
@State(Scope.Benchmark)
public class GetBenchmark extends BaseBenchmark
{
	@Param({ "0", "50", "100" })
	public int missPercent;

	private Object[] lookup;

	private Object[] insert;

	private boolean useSet;

	private Set<Object> set;

	private Map<Object, Object> map;

	@Setup(Level.Trial)
	public void setupBenchmark(BenchmarkParams params)
	{
		super.setupBenchmark(params);

		useSet = params.getBenchmark().endsWith("contains");

		if (params.getThreads() > 1 && !hashTable.isLookupThreadSafe())
			throw new UnsupportedOperationException(hashTable + " does not support thread safe lookups!");

		int miss = (int) ((long) capacity * (long) missPercent / 100L);

		int[] keys = generateInts(capacity + miss, randomKeys);
		insert = key.create(shuffleCopy(keys, 0, capacity));
		lookup = key.create(shuffleCopy(keys, miss, capacity + miss));
	}

	@Setup(Level.Iteration)
	public void setupIteration(BenchmarkParams params)
	{
		// add keys to set
		if (useSet)
			set = createSet();
		else
			map = createMap();

		// abort if add/put takes more than 10us for an extended period of time
		SpeedGuard guard = new SpeedGuard(10000);
		for (int i = 0; i < capacity; i++, guard.tick())
		{
			if (useSet)
				set.add(insert[i]);
			else
				map.put(insert[i], lookup[i]);
		}
	}

	@State(Scope.Thread)
	public static class ThreadState extends RoundRobinCounter
	{
		@Setup(Level.Iteration)
		public void setup(GetBenchmark benchmark, ThreadParams threads)
		{
			init(0, benchmark.lookup.length, threads.getThreadCount(), threads.getThreadIndex());
		}
	}

	@Benchmark
	public boolean contains(ThreadState state)
	{
		return set.contains(lookup[state.nextIndex()]);
	}

	@Benchmark
	public Object get(ThreadState state)
	{
		return map.get(lookup[state.nextIndex()]);
	}

	public static void main(String[] args) throws RunnerException
	{
		run(GetBenchmark.class, 8, false, false, args);
	}
}
