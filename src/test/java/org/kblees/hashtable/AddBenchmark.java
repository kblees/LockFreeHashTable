/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.kblees.hashtable.BenchUtils.*;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.AuxCounters.Type;
import org.openjdk.jmh.infra.*;

/**
 * Tests add / put performance of hash table implementations.
 */
@State(Scope.Benchmark)
public class AddBenchmark extends BaseBenchmark
{
	private int batchSize;

	private boolean useSet;

	private int threads;

	private Object[] keys;

	private final Queue<DataCounter> testData = new ConcurrentLinkedQueue<>();

	/**
	 * Combines an arbitrary object with an atomic int counter.
	 */
	static class DataCounter extends AtomicInteger
	{
		private static final long serialVersionUID = 1L;

		final Object data;

		DataCounter(Object data)
		{
			this.data = data;
		}
	}

	@Setup(Level.Trial)
	public void setupBenchmark(BenchmarkParams params)
	{
		super.setupBenchmark(params);

		useSet = params.getBenchmark().endsWith("add");
		threads = params.getThreads();
		batchSize = Math.min(100, Math.max(1, capacity / 100 / threads));

		if (threads > 1 && !hashTable.isUpdateThreadSafe())
			throw new UnsupportedOperationException(hashTable + " does not support thread safe updates!");

		keys = key.create(shuffle(generateInts(capacity, randomKeys)));
	}

	@Setup(Level.Iteration)
	public void setupIteration()
	{
		// fill testData queue with empty Sets or Maps
		for (int i = 0; i < threads; i++)
			testData.add(new DataCounter(useSet ? createSet() : createMap()));
	}

	@State(Scope.Thread)
	@AuxCounters(Type.EVENTS)
	public static class ThreadState extends RoundRobinCounter
	{
		@Setup(Level.Iteration)
		public void setup(AddBenchmark benchmark, ThreadParams threads)
		{
			onReset(() -> benchmark.onReset(this));
		}

		Set<Object> set;
		Map<Object, Object> map;
	}

	@SuppressWarnings("unchecked")
	private void onReset(ThreadState state)
	{
		for (;;)
		{
			// get head of testData queue
			DataCounter dataCounter = testData.peek();
			if (useSet)
				state.set = (Set<Object>) dataCounter.data;
			else
				state.map = (Map<Object, Object>) dataCounter.data;

			// reserve a batch of keys to add
			int index = dataCounter.getAndIncrement() * batchSize;
			int max = Math.min(capacity, index + batchSize);
			if (index < max)
			{
				state.init(0, max, index);
				return;
			}

			// if there are no more keys to add, switch to next testData instance
			if (testData.remove(dataCounter))
			{
				// and add a new empty instance to the queue
				Object data = useSet ? createSet() : createMap();
				testData.add(new DataCounter(data));
			}
		}
	}

	@Benchmark
	public boolean add(ThreadState state)
	{
		int index = state.nextIndex();
		return state.set.add(keys[index]);
	}

	@Benchmark
	public Object put(ThreadState state)
	{
		int index = state.nextIndex();
		return state.map.put(keys[index], keys[index]);
	}

	public static void main(String[] args)
	{
		run(AddBenchmark.class, 8, false, false, args);
		run(AddBenchmark.class, 1, false, true, args);
	}
}
