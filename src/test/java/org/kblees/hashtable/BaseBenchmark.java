/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;

/**
 * Common parameters for hash table benchmarks.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 10)
@Measurement(iterations = 3, time = 10)
public class BaseBenchmark
{
	@Param({ "LockFree", //
			"Concurrent", //
			"ConcurrentV7", //
			"NonBlocking", //
			"Koloboke", //
			"FastUtil", //
			"Smoothie", //
			"SwissTable" //
	})
	public HashTableType hashTable;

	@Param({ "Int" })
	public KeyType key;

	@Param({ "true", "false" })
	public boolean randomKeys;

	@Param({ "true", "false" })
	public boolean useCapacity;

	@Param({ "1024", "1149", "1290", "1448", "1625", "1825", //
			"2048", "2299", "2580", "2896", "3251", "3649", //
			"4096", "4598", "5161", "5793", "6502", "7298", //
			"8192", "9195", "10321", "11585", "13004", "14596", "16384", //
			"1024k", "1149k", "1290k", "1448k", "1625k", "1825k", //
			"2048k", "2299k", "2580k", "2896k", "3251k", "3649k", //
			"4096k", "4598k", "5161k", "5793k", "6502k", "7298k", //
			"8192k", "9195k", "10321k", "11585k", "13004k", "14596k", "16384k" })
	public String size;

	protected int capacity;

	@Setup(Level.Trial)
	public void setupBenchmark(BenchmarkParams params)
	{
		capacity = (int) BenchUtils.parseScaledLong(size);
	}

	protected Set<Object> createSet()
	{
		return hashTable.createSet(useCapacity ? capacity : 0);
	}

	protected Map<Object, Object> createMap()
	{
		return hashTable.createMap(useCapacity ? capacity : 0);
	}
}
