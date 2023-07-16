/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import static org.kblees.hashtable.BenchUtils.*;

import org.kblees.hashtable.RoundRobinCounter;
import org.openjdk.jmh.annotations.*;

public class IntSetAddBenchmark extends IntSetBenchmark
{
	@Param({ "true", "false" })
	boolean useCapacity;

	MinIntSet create()
	{
		return type.create(useCapacity ? insert.length : 0);
	}

	@Setup(Level.Trial)
	public void setupBenchmark()
	{
		int sz = (int) parseScaledLong(size);
		insert = shuffle(generateInts(sz, randomKeys));
	}

	@State(Scope.Thread)
	public static class ThreadState extends RoundRobinCounter
	{
		MinIntSet set;

		@Setup(Level.Iteration)
		public void setup(IntSetAddBenchmark benchmark)
		{
			set = benchmark.create();
			init(benchmark.insert.length);
			onReset(() -> set = benchmark.create());
		}
	}

	@Benchmark
	public boolean add(IntSetAddBenchmark.ThreadState state)
	{
		return state.set.add(insert[state.nextIndex()]);
	}

	public static void main(String[] args)
	{
		run(IntSetAddBenchmark.class, 1, false, true, args);
	}
}
