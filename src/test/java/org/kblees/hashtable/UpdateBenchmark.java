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
import org.openjdk.jmh.annotations.AuxCounters.Type;
import org.openjdk.jmh.infra.*;
import org.openjdk.jmh.runner.RunnerException;

/**
 * Tests update performance of hash table implementations.
 */
@State(Scope.Benchmark)
public class UpdateBenchmark extends BaseBenchmark
{
	private static final int BATCH_SIZE = 1024;

	private boolean useSet;

	private Set<Object> set;

	private Map<Object, Object> map;

	@Setup(Level.Trial)
	public void setupBenchmark(BenchmarkParams params)
	{
		super.setupBenchmark(params);

		useSet = params.getBenchmark().endsWith("updateSet");
		int threads = params.getThreads();
		if (threads > 1 && !hashTable.isUpdateThreadSafe())
			throw new UnsupportedOperationException(hashTable + " does not support thread safe updates!");
	}

	@Setup(Level.Iteration)
	public void setupIteration()
	{
		if (useSet)
			set = createSet();
		else
			map = createMap();
	}

	class KeyIterator implements Iterator<Object>
	{
		private int nextKey;

		KeyIterator(int nextKey)
		{
			this.nextKey = nextKey;
		}

		@Override
		public boolean hasNext()
		{
			return true;
		}

		protected int nextInt()
		{
			int v = nextKey++;
			return randomKeys ? qrng(v) : v;
		}

		@Override
		public Object next()
		{
			return key.create(nextInt());
		}
	}

	@State(Scope.Thread)
	@AuxCounters(Type.EVENTS)
	public static class ThreadState
	{
		private Iterator<Object> insert;
		private Iterator<Object> remove;
		private boolean inserting;
		private Runnable onTearDown;

		public long insertCount;
		public long insertNanos;
		public long removeCount;
		public long removeNanos;
		public int entries;
		public long memory;

		@Setup(Level.Iteration)
		public void setup(UpdateBenchmark benchmark, ThreadParams threadParams)
		{
			int startKey = Integer.MAX_VALUE / threadParams.getThreadCount() * threadParams.getThreadIndex();
			insert = benchmark.new KeyIterator(startKey);
			remove = benchmark.new KeyIterator(startKey);

			int perThreadCapacity = Math.max(BATCH_SIZE, benchmark.capacity / threadParams.getThreadCount());
			for (int i = 0; i < perThreadCapacity; i++)
			{
				Object key = insert.next();
				if (benchmark.useSet)
					benchmark.set.add(key);
				else
					benchmark.map.put(key, key);
			}

			if (threadParams.getThreadIndex() == 0)
			{
				onTearDown = () -> {
					entries = benchmark.useSet ? benchmark.set.size() : benchmark.map.size();
					gc();
					memory = getUsedMemory();
					benchmark.set = null;
					benchmark.map = null;
					gc();
					memory -= getUsedMemory();
				};
			}
		}

		@TearDown(Level.Iteration)
		public void tearDown()
		{
			if (onTearDown != null)
				onTearDown.run();
		}

		int measureSet(Set<Object> set, Control ctrl)
		{
			long start = System.nanoTime();
			int result = 0;
			if (inserting)
			{
				Iterator<Object> it = insert;
				for (int i = 0; i < BATCH_SIZE; i++)
					if (set.add(it.next()))
						result++;
				updateInsertCounters(ctrl, start);
			}
			else
			{
				Iterator<Object> it = remove;
				for (int i = 0; i < BATCH_SIZE; i++)
					if (set.remove(it.next()))
						result++;
				updateRemoveCounters(ctrl, start);
			}
			inserting = !inserting;
			return result;
		}

		int measureMap(Map<Object, Object> map, Control ctrl)
		{
			long start = System.nanoTime();
			int result = 0;
			if (inserting)
			{
				Iterator<Object> it = insert;
				for (int i = 0; i < BATCH_SIZE; i++)
				{
					Object key = it.next();
					if (map.put(key, key) == null)
						result++;
				}
				updateInsertCounters(ctrl, start);
			}
			else
			{
				Iterator<Object> it = remove;
				for (int i = 0; i < BATCH_SIZE; i++)
					if (map.remove(it.next()) != null)
						result++;
				updateRemoveCounters(ctrl, start);
			}
			inserting = !inserting;
			return result;
		}

		private void updateInsertCounters(Control ctrl, long start)
		{
			if (ctrl.startMeasurement && !ctrl.stopMeasurement)
			{
				insertCount += BATCH_SIZE;
				insertNanos += System.nanoTime() - start;
			}
		}

		private void updateRemoveCounters(Control ctrl, long start)
		{
			if (ctrl.startMeasurement && !ctrl.stopMeasurement)
			{
				removeCount += BATCH_SIZE;
				removeNanos += System.nanoTime() - start;
			}
		}
	}

	@Benchmark
	@OperationsPerInvocation(BATCH_SIZE)
	public int updateSet(ThreadState state, Control ctrl)
	{
		return state.measureSet(set, ctrl);
	}

	@Benchmark
	@OperationsPerInvocation(BATCH_SIZE)
	public int updateMap(ThreadState state, Control ctrl)
	{
		return state.measureMap(map, ctrl);
	}

	public static void main(String[] args) throws RunnerException
	{
		run(UpdateBenchmark.class, 8, false, false, args);
	}
}
